package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        CreateChannelAction action = new CreateChannelAction(null);
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
        assertTrue(gateway.lastChannelName != null && gateway.lastChannelName.startsWith("owner-repo-phase-1-tests-"),
                "expected channel name from state project+codeChange, got: " + gateway.lastChannelName);
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
        assertTrue(gateway.lastChannelName.contains("owner-repo"), "expected repo segment lowercase");
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

    private static final class FakeCreateChannelGateway implements DiscordGateway {
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
}
