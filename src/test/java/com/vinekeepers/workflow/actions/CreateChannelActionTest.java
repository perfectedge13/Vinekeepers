package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateChannelActionTest {

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
        CreateChannelAction action = new CreateChannelAction(gateway);
        Object result = action.run(null, Map.of(), Map.of("guildId", "guild-1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runReturnsChannelCreateFailedWhenGatewayIsNull() {
        CreateChannelAction action = new CreateChannelAction((DiscordGateway) null);
        Object result = action.run(null, Map.of(), Map.of("guildId", "guild-1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runReturnsChannelCreateFailedWhenGuildIdMissing() {
        DiscordGateway gateway = connectedGateway();
        CreateChannelAction action = new CreateChannelAction(gateway);
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runUsesGuildIdFromBindThenStateThenEventSourceId() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "new-chan-1";
        CreateChannelAction action = new CreateChannelAction(gateway);

        Object result = action.run(null, Map.of(), Map.of());
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);

        result = action.run(new Event("discord:my-guild", "message", Map.of()), Map.of(), Map.of());
        assertEquals("new-chan-1", result);
        assertEquals("my-guild", gateway.lastGuildId);
        // Channel name from state when bind/state have no channelName: built from state (repo-change-<suffix> for empty state)
        assertTrue(gateway.lastChannelName != null && gateway.lastChannelName.startsWith("repo-change-"),
                "expected channel name built from state, got: " + gateway.lastChannelName);
    }

    @Test
    void runUsesChannelNameFromBindOrStateOrBuildsFromState() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "chan-1";
        CreateChannelAction action = new CreateChannelAction(gateway);

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
        CreateChannelAction action = new CreateChannelAction(gateway);
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
        CreateChannelAction action = new CreateChannelAction(gateway);
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
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
        CreateChannelAction action = new CreateChannelAction(gateway);
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
        CreateChannelAction action = new CreateChannelAction(gateway);
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
        CreateChannelAction action = new CreateChannelAction(gateway);
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1"));
        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
    }

    @Test
    void runBuildsChannelNameWithRepoNameOnlyNotOwnerRepo() {
        FakeCreateChannelGateway gateway = new FakeCreateChannelGateway();
        gateway.connected = true;
        gateway.createdChannelId = "chan-1";
        CreateChannelAction action = new CreateChannelAction(gateway);
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
        CreateChannelAction action = new CreateChannelAction(gateway);
        action.run(new Event("discord:g1", "m", Map.of()), Map.of(), Map.of("guildId", "g1", "channelName", "Luna Room #1"));
        assertEquals("luna-room-1", gateway.lastChannelName);
    }

    @Test
    void normalizeChannelNameReturnsFallbackForNullOrBlank() {
        assertEquals("lifecycle-room", CreateChannelAction.normalizeChannelName(null));
        assertEquals("lifecycle-room", CreateChannelAction.normalizeChannelName(""));
        assertEquals("lifecycle-room", CreateChannelAction.normalizeChannelName("   "));
    }

    @Test
    void normalizeChannelNameNormalizesSpecialCharsAndTruncatesToMaxLength() {
        assertEquals("luna-room-1", CreateChannelAction.normalizeChannelName("Luna Room #1"));
        assertEquals("lifecycle-room", CreateChannelAction.normalizeChannelName("!@#$%"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 150; i++) longName.append("a");
        String result = CreateChannelAction.normalizeChannelName(longName.toString());
        assertEquals(100, result.length());
        assertTrue(result.matches("[a-z0-9_-]+"));
    }

    /** CreateChannelAction(router) with lifecycleOwnerBotId adds permission overwrite after create (mock gateway). */
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

        CreateChannelAction action = new CreateChannelAction(router);
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

    /** When addPermissionOverride returns false, create_channel fails with CHANNEL_CREATE_FAILED. */
    @Test
    void runWithRouterAndLifecycleOwnerBotId_whenAddPermissionOverrideReturnsFalse_returnsChannelCreateFailed() {
        FakeGatewayWithPermissionOverride gateway = new FakeGatewayWithPermissionOverride("owner-id");
        gateway.connected = true;
        gateway.createdChannelId = "new-ch";
        gateway.permissionOverrideReturn = false;
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        router.setDefaultGateway(gateway);
        router.registerSender("luna", (ch, msg, content) -> {}, gateway);

        CreateChannelAction action = new CreateChannelAction(router);
        Map<String, Object> bind = Map.of(
                "guildId", "g1",
                "channelName", "room",
                "lifecycleOwnerBotId", "luna");
        Object result = action.run(new Event("discord:g1", "m", Map.of()), Map.of(), bind);

        assertEquals(CreateChannelAction.CHANNEL_CREATE_FAILED, result);
        assertNotNull(gateway.lastPermissionOverride);
    }

    /** When lifecycleOwnerBotId is set but that bot has no Discord user id, create_channel fails. */
    @Test
    void runWithRouterAndLifecycleOwnerBotId_whenOwnerBotHasNoUserId_returnsChannelCreateFailed() {
        FakeGatewayWithPermissionOverride gateway = new FakeGatewayWithPermissionOverride("luna-user-id");
        gateway.connected = true;
        gateway.createdChannelId = "new-ch";
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        router.setDefaultGateway(gateway);
        router.registerSender("luna", (ch, msg, content) -> {}, gateway);
        // Do not register any gateway for "arrietty" so getSelfUserIdForBot("arrietty") returns null

        CreateChannelAction action = new CreateChannelAction(router);
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
