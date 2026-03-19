package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordSpaceOperationsTest {

    @Test
    void createRoom_returnsChannelCreateFailedWhenGatewayNotConnected() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(stubGateway(false, null, null));
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);
        CreateRoomResult result = ops.createRoom(new CreateRoomRequest(null, "guild-1", null, null, List.of(), null, null));
        assertFalse(result.isSuccess());
        assertEquals(CreateRoomFailureReason.GATEWAY_UNAVAILABLE, result.getFailureReason());
    }

    @Test
    void createRoom_returnsChannelCreateFailedWhenGuildIdMissing() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(stubGateway(true, "chan-1", null));
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);
        CreateRoomResult result = ops.createRoom(new CreateRoomRequest(null, null, null, null, List.of(), null, null));
        assertFalse(result.isSuccess());
        assertEquals(CreateRoomFailureReason.GUILD_ID_MISSING, result.getFailureReason());
    }

    @Test
    void createRoom_returnsChannelCreateFailedWhenCreateTextChannelReturnsNull() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(stubGateway(true, null, null));
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);
        CreateRoomResult result = ops.createRoom(new CreateRoomRequest("discord:g1", "g1", null, null, List.of(), null, null));
        assertFalse(result.isSuccess());
        assertEquals(CreateRoomFailureReason.CREATE_RETURNED_NULL, result.getFailureReason());
    }

    @Test
    void createRoom_usesGuildIdFromBindThenStateThenEventSourceId() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        FakeChannelGateway gateway = new FakeChannelGateway(true, "new-chan-1");
        router.setDefaultGateway(gateway);
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);

        CreateRoomResult result = ops.createRoom(new CreateRoomRequest(null, null, null, null, List.of(), null, null));
        assertFalse(result.isSuccess());
        assertEquals(CreateRoomFailureReason.GUILD_ID_MISSING, result.getFailureReason());

        result = ops.createRoom(new CreateRoomRequest("discord:my-guild", null, null, null, List.of(), null, null));
        assertTrue(result.isSuccess());
        assertEquals("new-chan-1", result.getChannelId());
        assertEquals("my-guild", gateway.lastGuildId);
        assertTrue(gateway.lastChannelName != null && gateway.lastChannelName.startsWith("repo-change-"),
                "expected channel name built from state, got: " + gateway.lastChannelName);
    }

    @Test
    void createRoom_buildsChannelNameFromStateAndNormalizes() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        FakeChannelGateway gateway = new FakeChannelGateway(true, "chan-1");
        router.setDefaultGateway(gateway);
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);

        ops.createRoom(new CreateRoomRequest("discord:g1", "g1", null, null, List.of(), "https://github.com/owner/repo", "phase-1-tests"));
        assertTrue(gateway.lastChannelName != null && gateway.lastChannelName.startsWith("repo-phase-1-tests-"),
                "expected channel name from state, got: " + gateway.lastChannelName);

        gateway.lastChannelName = null;
        ops.createRoom(new CreateRoomRequest("discord:g1", "g1", "Luna Room #1", null, List.of(), null, null));
        assertEquals("luna-room-1", gateway.lastChannelName);
    }

    @Test
    void createRoom_addsPermissionOverrideWhenLifecycleOwnerBotIdSet() {
        long lifecycleOwnerAllow = (1L << 10) | (1L << 11);
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        FakeChannelGatewayWithOverride gateway = new FakeChannelGatewayWithOverride(true, "new-channel-id", "owner-discord-user-id");
        router.setDefaultGateway(gateway);
        router.registerSender("luna", (ch, msg, content) -> {}, gateway);
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);

        CreateRoomResult result = ops.createRoom(new CreateRoomRequest("discord:guild-1", "guild-1", "lifecycle-room", "luna", List.of(), null, null));

        assertTrue(result.isSuccess());
        assertEquals("new-channel-id", result.getChannelId());
        assertNotNull(gateway.lastPermissionOverride);
        assertEquals("new-channel-id", gateway.lastPermissionOverride.channelId);
        assertEquals("guild-1", gateway.lastPermissionOverride.guildId);
        assertEquals("owner-discord-user-id", gateway.lastPermissionOverride.targetUserId);
        assertEquals(lifecycleOwnerAllow, gateway.lastPermissionOverride.allow);
        assertEquals(0L, gateway.lastPermissionOverride.deny);
    }

    @Test
    void createThread_returnsThreadCreateFailedWhenRouterNull() {
        DiscordSpaceOperations ops = new DiscordSpaceOperations(null, new LifecycleContextStore());
        CreateThreadResult result = ops.createThread(new CreateThreadRequest(null, "chan-1", null, null));
        assertFalse(result.isSuccess());
        assertEquals(CreateThreadFailureReason.ROUTER_NULL, result.getFailureReason());
    }

    @Test
    void createThread_returnsThreadCreateFailedWhenChannelIdMissing() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(stubGateway(true, null, "thread-1"));
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);
        CreateThreadResult result = ops.createThread(new CreateThreadRequest(null, null, null, null));
        assertFalse(result.isSuccess());
        assertEquals(CreateThreadFailureReason.CHANNEL_ID_MISSING, result.getFailureReason());
    }

    @Test
    void createThread_returnsThreadIdAndSetsDeliveryTargetWhenContextIdPresent() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", java.time.Instant.now(),
                null, "arrietty", null, null, null, null, null);
        store.put(ctx);
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(stubGateway(true, null, "thread-123"));
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);

        CreateThreadResult result = ops.createThread(new CreateThreadRequest(null, "chan-1", null, "ctx-1"));

        assertTrue(result.isSuccess());
        assertEquals("thread-123", result.getThreadId());
        assertTrue(store.getByDeliveryTargetId("thread-123").isPresent());
        assertEquals("ctx-1", store.getByDeliveryTargetId("thread-123").orElseThrow().getContextId());
    }

    @Test
    void createThread_usesLifecycleOwnerGatewayWhenChannelHasContext() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-lifecycle", java.time.Instant.now(),
                null, "arrietty", null, null, null, null, null);
        store.put(ctx);
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        FakeThreadGateway defaultGw = new FakeThreadGateway(true, "thread-default");
        FakeThreadGateway arriettyGw = new FakeThreadGateway(true, "thread-arrietty");
        router.setDefaultGateway(defaultGw);
        router.registerSender("arrietty", null, arriettyGw);
        DiscordSpaceOperations ops = new DiscordSpaceOperations(router, store);

        CreateThreadResult result = ops.createThread(new CreateThreadRequest(null, "chan-lifecycle", "Room updates", null));

        assertTrue(result.isSuccess());
        assertEquals("thread-arrietty", result.getThreadId());
        assertEquals("chan-lifecycle", arriettyGw.lastParentChannelId);
        assertEquals("Room updates", arriettyGw.lastThreadName);
    }

    @Test
    void normalizeChannelName_returnsFallbackForNullOrBlank() {
        assertEquals("lifecycle-room", DiscordSpaceOperations.normalizeChannelName(null));
        assertEquals("lifecycle-room", DiscordSpaceOperations.normalizeChannelName(""));
        assertEquals("lifecycle-room", DiscordSpaceOperations.normalizeChannelName("   "));
    }

    @Test
    void normalizeChannelName_normalizesSpecialCharsAndTruncatesToMaxLength() {
        assertEquals("luna-room-1", DiscordSpaceOperations.normalizeChannelName("Luna Room #1"));
        assertEquals("lifecycle-room", DiscordSpaceOperations.normalizeChannelName("!@#$%"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 150; i++) longName.append("a");
        String result = DiscordSpaceOperations.normalizeChannelName(longName.toString());
        assertEquals(100, result.length());
        assertTrue(result.matches("[a-z0-9_-]+"));
    }

    private static OutboundGateway stubGateway(boolean connected, String channelId) {
        return stubGateway(connected, channelId, null);
    }

    private static OutboundGateway stubGateway(boolean connected, String channelId, String threadId) {
        final String chId = channelId;
        final String thId = threadId;
        return new OutboundGateway() {
            @Override
            public void send(String ch, String msg, String content) {}
            @Override
            public String getSelfUserId() { return null; }
            @Override
            public boolean isConnected() { return connected; }
            @Override
            public String createTextChannel(String guildId, String channelName) { return chId; }
            @Override
            public String createThreadChannel(String parentChannelId, String threadName) { return thId; }
            @Override
            public boolean addPermissionOverride(String ch, String g, String uid, long allow, long deny) { return true; }
        };
    }

    private static class FakeChannelGateway implements OutboundGateway {
        final boolean connected;
        final String createdChannelId;
        String lastGuildId;
        String lastChannelName;

        FakeChannelGateway(boolean connected, String createdChannelId) {
            this.connected = connected;
            this.createdChannelId = createdChannelId;
        }

        @Override
        public void send(String channelId, String messageId, String content) {}
        @Override
        public String getSelfUserId() { return null; }
        @Override
        public boolean isConnected() { return connected; }
        @Override
        public String createTextChannel(String guildId, String channelName) {
            this.lastGuildId = guildId;
            this.lastChannelName = channelName;
            return createdChannelId;
        }
        @Override
        public String createThreadChannel(String parentChannelId, String threadName) { return null; }
        @Override
        public boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny) { return true; }
    }

    private static final class FakeChannelGatewayWithOverride extends FakeChannelGateway {
        final String selfUserId;
        PermissionOverrideCall lastPermissionOverride;

        FakeChannelGatewayWithOverride(boolean connected, String createdChannelId, String selfUserId) {
            super(connected, createdChannelId);
            this.selfUserId = selfUserId;
        }

        @Override
        public String getSelfUserId() { return selfUserId; }

        @Override
        public boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny) {
            this.lastPermissionOverride = new PermissionOverrideCall(channelId, guildId, targetUserId, allow, deny);
            return true;
        }
    }

    private static final class PermissionOverrideCall {
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

    private static final class FakeThreadGateway implements OutboundGateway {
        final boolean connected;
        final String threadIdToReturn;
        String lastParentChannelId;
        String lastThreadName;

        FakeThreadGateway(boolean connected, String threadIdToReturn) {
            this.connected = connected;
            this.threadIdToReturn = threadIdToReturn;
        }

        @Override
        public void send(String channelId, String messageId, String content) {}
        @Override
        public String getSelfUserId() { return null; }
        @Override
        public boolean isConnected() { return connected; }
        @Override
        public String createTextChannel(String guildId, String channelName) { return null; }
        @Override
        public String createThreadChannel(String parentChannelId, String threadName) {
            this.lastParentChannelId = parentChannelId;
            this.lastThreadName = threadName;
            return threadIdToReturn;
        }
        @Override
        public boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny) { return true; }
    }
}
