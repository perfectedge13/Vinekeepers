package com.vinekeepers.bot;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BotDefinition, including getConnectorIdentity present/absent.
 */
class BotDefinitionTest {

    private static final Persona PERSONA = new Persona("Test", "");
    private static final ModelProfile MODEL = new ModelProfile("stub", "stub");

    @Test
    void getConnectorIdentity_discordPresent_returnsIdentity() {
        ConnectorIdentity discordId = new ConnectorIdentity(Map.of("tokenEnvKey", "DISCORD_BOT_TOKEN", "handlesOwnedSpaces", true));
        BotDefinition bot = new BotDefinition("bot-1", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of("discord", discordId));

        Optional<ConnectorIdentity> opt = bot.getConnectorIdentity("discord");
        assertTrue(opt.isPresent());
        assertEquals("DISCORD_BOT_TOKEN", opt.get().getAttribute("tokenEnvKey"));
        assertTrue(Boolean.TRUE.equals(opt.get().getAttribute("handlesOwnedSpaces")));
    }

    @Test
    void getConnectorIdentity_discordAbsent_returnsEmpty() {
        BotDefinition bot = new BotDefinition("bot-2", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of());

        assertTrue(bot.getConnectorIdentity("discord").isEmpty());
    }

    @Test
    void getConnectorIdentity_nullConnectorId_returnsEmpty() {
        ConnectorIdentity discordId = new ConnectorIdentity(Map.of("tokenEnvKey", "X"));
        BotDefinition bot = new BotDefinition("bot-3", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of("discord", discordId));

        assertTrue(bot.getConnectorIdentity(null).isEmpty());
    }

    @Test
    void getConnectorIdentity_blankConnectorId_returnsEmpty() {
        ConnectorIdentity discordId = new ConnectorIdentity(Map.of("tokenEnvKey", "X"));
        BotDefinition bot = new BotDefinition("bot-4", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of("discord", discordId));

        assertTrue(bot.getConnectorIdentity("").isEmpty());
        assertTrue(bot.getConnectorIdentity("   ").isEmpty());
    }

    @Test
    void getConnectorIdentity_unknownConnectorId_returnsEmpty() {
        ConnectorIdentity discordId = new ConnectorIdentity(Map.of("tokenEnvKey", "X"));
        BotDefinition bot = new BotDefinition("bot-5", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, Map.of("discord", discordId));

        assertTrue(bot.getConnectorIdentity("github").isEmpty());
    }

    @Test
    void constructor_withNullConnectorIdentities_usesEmptyMap() {
        BotDefinition bot = new BotDefinition("bot-6", PERSONA, MODEL, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null, null);
        assertTrue(bot.getConnectorIdentity("discord").isEmpty());
    }
}
