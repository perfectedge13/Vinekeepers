package com.vinekeepers.connectors;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConnectorIdentity;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.events.EventBus;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DiscordConnectorAdapter: registerBots no-op for null inputs,
 * per-bot registration when token present, default sender/gateway behavior when token blank.
 */
class DiscordConnectorAdapterTest {

    private static final Persona PERSONA = new Persona("Test", "");
    private static final ModelProfile MODEL = new ModelProfile("stub", "stub");

    private EventBus eventBus;
    private OutboundDeliveryRouter outboundRouter;
    private ConnectorContext context;
    private LifecycleContextStore lifecycleStore;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        lifecycleStore = new LifecycleContextStore();
        outboundRouter = new OutboundDeliveryRouter(lifecycleStore);
        context = new ConnectorContext(eventBus, outboundRouter);
    }

    @Test
    void registerBots_withNullBots_doesNothing() {
        DiscordConnectorConfig config = new DiscordConnectorConfig(null);
        DiscordConnectorAdapter adapter = new DiscordConnectorAdapter(config, Set.of());
        adapter.registerBots(null, context);
        assertTrue(adapter.getDiscordSources().isEmpty());
        assertNull(outboundRouter.getDefaultGateway());
    }

    @Test
    void registerBots_withNullContext_doesNothing() {
        BotDefinition bot = botWithDiscordIdentity("ENV_KEY_NOT_SET");
        DiscordConnectorConfig config = new DiscordConnectorConfig(null);
        DiscordConnectorAdapter adapter = new DiscordConnectorAdapter(config, Set.of());
        adapter.registerBots(List.of(bot), null);
        assertTrue(adapter.getDiscordSources().isEmpty());
    }

    @Test
    void registerBots_withEmptyBotsList_doesNotThrow() {
        DiscordConnectorConfig config = new DiscordConnectorConfig(null);
        DiscordConnectorAdapter adapter = new DiscordConnectorAdapter(config, Set.of());
        adapter.registerBots(List.of(), context);
        assertTrue(adapter.getDiscordSources().isEmpty());
        assertNull(outboundRouter.getDefaultGateway());
    }

    @Test
    void registerBots_withBotWithBlankToken_doesNotAddSource() {
        // Token env key that is not set in environment → Env.get returns "" → adapter skips this bot
        BotDefinition bot = botWithDiscordIdentity("VINEKEEPERS_TEST_DISCORD_TOKEN_NEVER_SET_XYZ");
        DiscordConnectorConfig config = new DiscordConnectorConfig(null);
        DiscordConnectorAdapter adapter = new DiscordConnectorAdapter(config, Set.of("bot-1"));
        adapter.registerBots(List.of(bot), context);
        assertTrue(adapter.getDiscordSources().isEmpty());
        assertNull(outboundRouter.getDefaultGateway());
    }

    @Test
    void registerBots_withBotsWithNoDiscordIdentity_doesNotSetDefaultGateway() {
        BotDefinition bot = new BotDefinition("no-discord", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of());
        DiscordConnectorConfig config = new DiscordConnectorConfig(null);
        DiscordConnectorAdapter adapter = new DiscordConnectorAdapter(config, Set.of());
        adapter.registerBots(List.of(bot), context);
        assertTrue(adapter.getDiscordSources().isEmpty());
        assertNull(outboundRouter.getDefaultGateway());
    }

    @Test
    void registerBots_perBotRegistration_whenTokenBlank_skipsThatBot() {
        BotDefinition withKey = botWithDiscordIdentity("ANOTHER_NEVER_SET_KEY");
        BotDefinition noKey = new BotDefinition("no-key", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of());
        DiscordConnectorConfig config = new DiscordConnectorConfig(null);
        DiscordConnectorAdapter adapter = new DiscordConnectorAdapter(config, Set.of("with-key"));
        adapter.registerBots(List.of(withKey, noKey), context);
        // No token in env, so no sources; default gateway not set
        assertTrue(adapter.getDiscordSources().isEmpty());
        assertNull(outboundRouter.getDefaultGateway());
    }

    private static BotDefinition botWithDiscordIdentity(String tokenEnvKey) {
        ConnectorIdentity discord = new ConnectorIdentity(Map.of("tokenEnvKey", tokenEnvKey, "handlesOwnedSpaces", false));
        return new BotDefinition("bot-1", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of("discord", discord));
    }
}
