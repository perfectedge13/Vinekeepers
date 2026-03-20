package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.CreateRoomRequest;
import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.connectors.DiscordSpaceOperations;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.SpaceOperations;
import com.vinekeepers.connectors.SpaceOperationsRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateChannelActionTest {

    private static SpaceOperationsRegistry registryWithDiscord(OutboundDeliveryRouter router, LifecycleContextStore store) {
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        registry.register("discord", new DiscordSpaceOperations(router, store));
        return registry;
    }

    @Test
    void runReturnsChannelCreateFailedWhenGatewayNotConnected() {
        DiscordGateway gateway = new DiscordGateway() {
            @Override
            public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {}
            @Override
            public void shutdown() {}
            @Override
            public void send(String channelId, String messageId, String content) {}
            @Override
            public boolean isConnected() { return false; }
        };
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:guild-1", "message", Map.of()), Map.of(), Map.of("guildId", "guild-1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runReturnsChannelCreateFailedWhenGatewayIsNull() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:x", "m", Map.of()), Map.of(), Map.of("guildId", "guild-1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runReturnsChannelCreateFailedWhenGuildIdMissing() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(connectedGateway());
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of());
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runUsesGuildIdFromBindThenStateThenEventSourceId() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "new-chan-1";
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));

        Object result = action.run(null, Map.of(), Map.of());
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);

        result = action.run(new Event("discord:my-guild", "message", Map.of()), Map.of(), Map.of());
        assertEquals("new-chan-1", result);
        assertEquals("my-guild", gateway.lastGuildId);
        assertTrue(gateway.lastChannelName != null && gateway.lastChannelName.startsWith("repo-change-"),
                "expected channel name built from state, got: " + gateway.lastChannelName);
    }

    @Test
    void runUsesChannelNameFromBindOrStateOrBuildsFromState() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "chan-1";
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));

        action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1", "channelName", "luna-room"));
        assertEquals("luna-room", gateway.lastChannelName);

        gateway.lastChannelName = null;
        action.run(new Event("discord:g1", "m", Map.of()), Map.of("channelName", "from-state"), Map.of("guildId", "g1"));
        assertEquals("from-state", gateway.lastChannelName);

        gateway.lastChannelName = null;
        action.run(new Event("discord:g1", "m", Map.of()),
                Map.of("project", "https://github.com/owner/repo", "codeChange", "phase-1-tests"),
                Map.of("guildId", "g1"));
        assertTrue(gateway.lastChannelName != null && gateway.lastChannelName.startsWith("repo-phase-1-tests-"),
                "expected channel name from state (repo name only, no owner), got: " + gateway.lastChannelName);
    }

    @Test
    void runBuildsChannelNameFullyLowercaseFromMixedCaseProject() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "chan-1";
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        action.run(new Event("discord:g1", "m", Map.of()),
                Map.of("project", "https://github.com/Owner/Repo", "codeChange", "Phase One"),
                Map.of("guildId", "g1"));
        assertTrue(gateway.lastChannelName != null, "expected channel name");
        assertEquals(gateway.lastChannelName, gateway.lastChannelName.toLowerCase(java.util.Locale.ROOT),
                "channel name must be fully lowercase");
        assertTrue(gateway.lastChannelName.startsWith("repo-"), "expected repo segment (repo name only, no owner)");
    }

    @Test
    void runReturnsChannelCreateFailedWhenCreateTextChannelReturnsNull() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = null;
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runReusesExistingDiscordChannelIdInState_withoutCallingCreate() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "would-be-new-id";
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        String existing = "1098765432109876543";
        Object result = action.run(new Event("discord:g1", "m", Map.of()),
                Map.of("channelId", existing),
                Map.of("guildId", "g1"));
        assertEquals(existing, result);
        assertNull(gateway.lastGuildId, "createTextChannel must not run when state already has a reusable channel id");
    }

    @Test
    void runReturnsChannelCreateFailedWhenCreateTextChannelThrows() {
        DiscordGateway gateway = new DiscordGateway() {
            @Override
            public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {}
            @Override
            public void shutdown() {}
            @Override
            public void send(String channelId, String messageId, String content) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public String createTextChannel(String guildId, String channelName) {
                throw new RuntimeException("Discord API error");
            }
        };
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runReturnsChannelCreateFailedWhenCreateTextChannelThrowsTimeoutException() {
        DiscordGateway gateway = new DiscordGateway() {
            @Override
            public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {}
            @Override
            public void shutdown() {}
            @Override
            public void send(String channelId, String messageId, String content) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public String createTextChannel(String guildId, String channelName) {
                throw new RuntimeException(new TimeoutException("submit().get() timeout"));
            }
        };
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runReturnsChannelCreateFailedWhenCreateTextChannelThrowsExecutionException() {
        DiscordGateway gateway = new DiscordGateway() {
            @Override
            public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {}
            @Override
            public void shutdown() {}
            @Override
            public void send(String channelId, String messageId, String content) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public String createTextChannel(String guildId, String channelName) {
                throw new RuntimeException(new ExecutionException(new IllegalStateException("JDA rest action failed")));
            }
        };
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runBuildsChannelNameWithRepoNameOnlyNotOwnerRepo() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "chan-1";
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        action.run(new Event("discord:g1", "m", Map.of()),
                Map.of("project", "owner/repo", "codeChange", "add-feature"),
                Map.of("guildId", "g1"));
        assertTrue(gateway.lastChannelName != null && gateway.lastChannelName.startsWith("repo-add-feature-"),
                "channel name must use repo name only (no owner), got: " + gateway.lastChannelName);
        assertFalse(gateway.lastChannelName.startsWith("owner-"), "channel name must not start with owner");
    }

    @Test
    void runNormalizesChannelNameFromBindToLowercaseDiscordSafe() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "chan-1";
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1", "channelName", "Luna Room #1"));
        assertEquals("luna-room-1", gateway.lastChannelName);
    }

    @Test
    void runReturnsChannelCreateFailedWhenUnknownSourcePrefix() {
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        CreateChannelAction action = new CreateChannelAction(registry);
        Object result = action.run(new Event("unknown:xyz", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void whenEventIsNull_returnsChannelCreateFailed() {
        final boolean[] createRoomCalled = { false };
        SpaceOperations stub = new SpaceOperations() {
            @Override
            public com.vinekeepers.connectors.CreateRoomResult createRoom(CreateRoomRequest request) {
                createRoomCalled[0] = true;
                return new com.vinekeepers.connectors.CreateRoomResult.Success("stub-channel");
            }
            @Override
            public com.vinekeepers.connectors.CreateThreadResult createThread(com.vinekeepers.connectors.CreateThreadRequest request) {
                return new com.vinekeepers.connectors.CreateThreadResult.Failure(com.vinekeepers.connectors.CreateThreadFailureReason.CHANNEL_ID_MISSING);
            }
        };
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        registry.register("discord", stub);
        CreateChannelAction action = new CreateChannelAction(registry);
        Object result = action.run(null, Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
        assertFalse(createRoomCalled[0], "capability should not be invoked when event is null");
    }

    @Test
    void whenSourcePrefixUnknown_returnsChannelCreateFailed() {
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        CreateChannelAction action = new CreateChannelAction(registry);
        Object result = action.run(new Event("other:x", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    /** CreateChannelAction with registry: lifecycleOwnerBotId adds permission overwrite after create (via DiscordSpaceOperations). */
    @Test
    void runWithRouterAndLifecycleOwnerBotId_addsPermissionOverwriteAfterCreate() {
        long lifecycleOwnerAllow = (1L << 10) | (1L << 11); // VIEW_CHANNEL | SEND_MESSAGES
        FakeGatewayWithPermissionOverride gateway = new FakeGatewayWithPermissionOverride("owner-discord-user-id");
        gateway.connected = true;
        gateway.createdChannelId = "new-channel-id";
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(gateway);
        router.registerSender("luna", (ch, msg, content) -> {}, gateway);

        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, store));
        Map<String, Object> bind = Map.of(
                "guildId", "guild-1",
                "channelName", "lifecycle-room",
                "lifecycleOwnerBotId", "luna");
        Object result = action.run(new Event("discord:guild-1", "m", Map.of()), Map.of(), bind);

        assertEquals("new-channel-id", result);
        assertNotNull(gateway.lastPermissionOverride, "addPermissionOverride should have been called");
        assertEquals("new-channel-id", gateway.lastPermissionOverride.channelId);
        assertEquals("guild-1", gateway.lastPermissionOverride.guildId);
        assertEquals("owner-discord-user-id", gateway.lastPermissionOverride.targetUserId);
        assertEquals(lifecycleOwnerAllow, gateway.lastPermissionOverride.allow);
        assertEquals(0L, gateway.lastPermissionOverride.deny);
    }

    @Test
    void runWithRouterAndLifecycleOwnerBotId_whenAddPermissionOverrideReturnsFalse_returnsChannelCreateFailed() {
        FakeGatewayWithPermissionOverride gateway = new FakeGatewayWithPermissionOverride("owner-id");
        gateway.connected = true;
        gateway.createdChannelId = "new-ch";
        gateway.permissionOverrideReturn = false;
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        router.setDefaultGateway(gateway);
        router.registerSender("luna", (ch, msg, content) -> {}, gateway);

        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, new LifecycleContextStore()));
        Map<String, Object> bind = Map.of(
                "guildId", "g1",
                "channelName", "room",
                "lifecycleOwnerBotId", "luna");
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), bind);

        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
        assertNotNull(gateway.lastPermissionOverride);
    }

    @Test
    void runWithRouterAndLifecycleOwnerBotId_whenOwnerBotHasNoUserId_returnsChannelCreateFailed() {
        FakeGatewayWithPermissionOverride gateway = new FakeGatewayWithPermissionOverride("luna-user-id");
        gateway.connected = true;
        gateway.createdChannelId = "new-ch";
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        router.setDefaultGateway(gateway);
        router.registerSender("luna", (ch, msg, content) -> {}, gateway);

        CreateChannelAction action = new CreateChannelAction(registryWithDiscord(router, new LifecycleContextStore()));
        Map<String, Object> bind = Map.of(
                "guildId", "g1",
                "channelName", "room",
                "lifecycleOwnerBotId", "arrietty");
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), bind);

        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
        assertNull(gateway.lastPermissionOverride);
    }

    private static DiscordGateway connectedGateway() {
        return new DiscordGateway() {
            @Override
            public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {}
            @Override
            public void shutdown() {}
            @Override
            public void send(String channelId, String messageId, String content) {}
            @Override
            public boolean isConnected() { return true; }
        };
    }

    private static class FakeCreateChannelGateway implements DiscordGateway {
        boolean connected;
        String createdChannelId;
        String lastGuildId;
        String lastChannelName;

        @Override
        public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {}
        @Override
        public void shutdown() {}
        @Override
        public void send(String channelId, String messageId, String content) {}
        @Override
        public boolean isConnected() { return connected; }
        @Override
        public String createTextChannel(String guildId, String channelName) {
            this.lastGuildId = guildId;
            this.lastChannelName = channelName;
            return createdChannelId;
        }
    }

    private static final class FakeGatewayWithPermissionOverride extends FakeCreateChannelGateway {
        final String selfUserId;
        PermissionOverrideCall lastPermissionOverride;

        FakeGatewayWithPermissionOverride(String selfUserId) {
            this.selfUserId = selfUserId;
        }

        @Override
        public String getSelfUserId() {
            return selfUserId;
        }

        boolean permissionOverrideReturn = true;

        @Override
        public boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny) {
            this.lastPermissionOverride = new PermissionOverrideCall(channelId, guildId, targetUserId, allow, deny);
            return permissionOverrideReturn;
        }

        static final class PermissionOverrideCall {
            final String channelId, guildId, targetUserId;
            final long allow, deny;

            PermissionOverrideCall(String channelId, String guildId, String targetUserId, long allow, long deny) {
                this.channelId = channelId;
                this.guildId = guildId;
                this.targetUserId = targetUserId;
                this.allow = allow;
                this.deny = deny;
            }
        }
    }
}
