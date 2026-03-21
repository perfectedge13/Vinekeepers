package com.vinekeepers.config;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.Router;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    private final ConfigLoader loader = new ConfigLoader();

    @Test
    void loadFromPathParsesBotsAndRouting(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: test-bot
                persona:
                  name: Test
                  systemPrompt: "You are a test bot"
            routing:
              - botId: test-bot
                filter: {}
            """);
        BotConfig config = loader.loadFromPath(yaml);
        assertNotNull(config);
        assertNotNull(config.getBots());
        assertEquals(1, config.getBots().size());
        assertNotNull(config.getRouting());
        assertEquals(1, config.getRouting().size());
    }

    @Test
    void buildBotsReturnsEmptyForEmptyConfig() {
        BotConfig config = new BotConfig();
        config.setBots(null);
        List<BotDefinition> bots = loader.buildBots(config);
        assertTrue(bots.isEmpty());
    }

    @Test
    void addRoutingsAddsToRouter(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            routing:
              - botId: bot-a
                filter: {}
            """);
        BotConfig config = loader.loadFromPath(yaml);
        Router router = new Router();
        loader.addRoutings(config, router);
        assertNotNull(router);
        // Router has no public size; we just ensure no exception and buildRouter works
        Router fromConfig = loader.buildRouter(config);
        assertNotNull(fromConfig);
    }

    @Test
    void addRoutingsWithNullRoutingDoesNotThrow() {
        BotConfig config = new BotConfig();
        config.setRouting(null);
        Router router = new Router();
        loader.addRoutings(config, router);
    }

    @Test
    void buildBotsParsesWorkflowTypeAndParams(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: luna
                persona:
                  name: Luna
                  systemPrompt: "You are Luna"
                workflow:
                  type: configured
                  params:
                    workflowRef: luna_cursor
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertNotNull(bots);
        assertEquals(1, bots.size());
        BotDefinition bot = bots.get(0);
        assertEquals("luna", bot.getId());
        assertEquals("configured", bot.getWorkflowType());
        assertEquals(Map.of("workflowRef", "luna_cursor"), bot.getWorkflowParams());
    }

    @Test
    void buildBotsWithNoWorkflowUsesStub(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: test-bot
                persona:
                  name: Test
                  systemPrompt: ""
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertNotNull(bots);
        assertEquals(1, bots.size());
        assertEquals("stub", bots.get(0).getWorkflowType());
        assertTrue(bots.get(0).getWorkflowParams().isEmpty());
    }

    @Test
    void loadFromPathParsesWorkflowsSection(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: flow-bot
                persona:
                  name: Flow
                  systemPrompt: ""
                workflow:
                  type: configured
                  params:
                    workflowRef: my_flow
            workflows:
              my_flow:
                steps:
                  - type: done
                    message: Done
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        assertNotNull(config.getWorkflows());
        assertEquals("my_flow", config.getWorkflows().keySet().iterator().next());
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertEquals("configured", bots.get(0).getWorkflowType());
        assertEquals(Map.of("workflowRef", "my_flow"), bots.get(0).getWorkflowParams());
    }

    @Test
    void buildRouterParsesDiscordMentionRouting(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            routing:
              - botId: luna
                filter:
                  discordMention: luna
            """);
        BotConfig config = loader.loadFromPath(yaml);
        Router router = loader.buildRouter(config);

        assertEquals(List.of("luna"), router.route(new com.vinekeepers.events.Event(
                "discord:g:ch",
                "message",
                Map.of("content", "ping @Luna"))));
    }

    @Test
    void buildRouterParsesDiscordAuthorsRouting(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            routing:
              - botId: luna
                filter:
                  discordMention: luna
                  discordAuthors: ["novawilde13_72571"]
            """);
        BotConfig config = loader.loadFromPath(yaml);
        Router router = loader.buildRouter(config);

        com.vinekeepers.events.Event withAuthorUsername = new com.vinekeepers.events.Event(
                "discord:g:ch", "message",
                Map.of("content", "ping @Luna", "author", "novawilde13_72571", "authorId", "snowflake-id"));
        assertEquals(List.of("luna"), router.route(withAuthorUsername));

        com.vinekeepers.events.Event otherAuthor = new com.vinekeepers.events.Event(
                "discord:g:ch", "message",
                Map.of("content", "ping @Luna", "author", "other_user", "authorId", "other-id"));
        assertTrue(router.route(otherAuthor).isEmpty());
    }

    @Test
    void buildRouterParsesDiscordChannelsExclude(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            routing:
              - botId: luna
                filter:
                  discordMention: luna
                  discordAuthors: ["novawilde13_72571"]
                  discordChannelsExclude: ["excluded-channel-id"]
            """);
        BotConfig config = loader.loadFromPath(yaml);
        Router router = loader.buildRouter(config);
        com.vinekeepers.events.Event inExcluded = new com.vinekeepers.events.Event(
                "discord:g:ch", "message",
                Map.of("channelId", "excluded-channel-id", "content", "ping @Luna",
                        "author", "novawilde13_72571", "mentions", List.of("luna")));
        assertTrue(router.route(inExcluded).isEmpty());
    }

    @Test
    void buildBotsParsesDiscordTokenEnvKey(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: luna
                persona:
                  name: Luna
                  systemPrompt: "You are Luna"
                discordTokenEnvKey: DISCORD_LUNA_TOKEN
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertNotNull(bots);
        assertEquals(1, bots.size());
        assertEquals("DISCORD_LUNA_TOKEN", bots.get(0).getConnectorIdentity("discord").orElseThrow().getAttribute("tokenEnvKey"));
    }

    @Test
    void buildBotsWithNoDiscordTokenEnvKeyReturnsNull(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: test-bot
                persona:
                  name: Test
                  systemPrompt: ""
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertTrue(bots.get(0).getConnectorIdentity("discord").isEmpty());
    }

    @Test
    void loadFromPathParsesDefaultDiscordTokenEnvKey(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            defaultDiscordTokenEnvKey: MY_DISCORD_TOKEN_KEY
            bots:
              - id: bot1
                persona:
                  name: Bot1
                  systemPrompt: ""
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        assertEquals("MY_DISCORD_TOKEN_KEY", config.getDefaultDiscordTokenEnvKey());
    }

    @Test
    void buildBotsParsesHandlesOwnedSpacesTrue(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: arrietty
                persona:
                  name: Arrietty
                  systemPrompt: ""
                handlesOwnedSpaces: true
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertTrue(Boolean.TRUE.equals(bots.get(0).getConnectorIdentity("discord").orElseThrow().getAttribute("handlesOwnedSpaces")));
    }

    @Test
    void buildBotsParsesHandlesOwnedSpacesAbsentAsFalse(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: luna
                persona:
                  name: Luna
                  systemPrompt: ""
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertTrue(bots.get(0).getConnectorIdentity("discord").isEmpty());
    }

    // --- Dual-read: new shape identities.discord only ---

    @Test
    void buildBotsParsesIdentitiesDiscordNewShapeOnly(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: nova
                persona:
                  name: Nova
                  systemPrompt: ""
                identities:
                  discord:
                    tokenEnvKey: DISCORD_NOVA_TOKEN
                    handlesOwnedSpaces: true
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertEquals("DISCORD_NOVA_TOKEN", bots.get(0).getConnectorIdentity("discord").orElseThrow().getAttribute("tokenEnvKey"));
        assertTrue(Boolean.TRUE.equals(bots.get(0).getConnectorIdentity("discord").orElseThrow().getAttribute("handlesOwnedSpaces")));
    }

    @Test
    void buildBotsDualReadNewWinsOverLegacy(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: dual
                persona:
                  name: Dual
                  systemPrompt: ""
                discordTokenEnvKey: LEGACY_TOKEN
                handlesOwnedSpaces: false
                identities:
                  discord:
                    tokenEnvKey: NEW_SHAPE_TOKEN
                    handlesOwnedSpaces: true
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        // New shape wins: token and handlesOwnedSpaces from identities.discord
        assertEquals("NEW_SHAPE_TOKEN", bots.get(0).getConnectorIdentity("discord").orElseThrow().getAttribute("tokenEnvKey"));
        assertTrue(Boolean.TRUE.equals(bots.get(0).getConnectorIdentity("discord").orElseThrow().getAttribute("handlesOwnedSpaces")));
    }

    // --- Malformed identity config: warn and ignore / predictable behavior ---

    @Test
    void buildBotsIdentitiesNotMapIgnoresNewShape(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: bad-identities
                persona:
                  name: Bad
                  systemPrompt: ""
                identities: 123
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertTrue(bots.get(0).getConnectorIdentity("discord").isEmpty());
    }

    @Test
    void buildBotsIdentitiesDiscordNotMapIgnoresNewShape(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: bad-discord
                persona:
                  name: Bad
                  systemPrompt: ""
                identities:
                  discord: "not-a-map"
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertTrue(bots.get(0).getConnectorIdentity("discord").isEmpty());
    }

    @Test
    void buildBotsTokenEnvKeyMalformedTreatedAsAbsent(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: bad-token
                persona:
                  name: Bad
                  systemPrompt: ""
                identities:
                  discord:
                    tokenEnvKey: 42
                    handlesOwnedSpaces: true
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        var identity = bots.get(0).getConnectorIdentity("discord").orElseThrow();
        assertNull(identity.getAttribute("tokenEnvKey"));
        assertTrue(Boolean.TRUE.equals(identity.getAttribute("handlesOwnedSpaces")));
    }

    @Test
    void buildBotsHandlesOwnedSpacesMalformedTreatedAsFalse(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: bad-handles
                persona:
                  name: Bad
                  systemPrompt: ""
                identities:
                  discord:
                    tokenEnvKey: TOKEN_KEY
                    handlesOwnedSpaces: "yes"
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        var identity = bots.get(0).getConnectorIdentity("discord").orElseThrow();
        assertEquals("TOKEN_KEY", identity.getAttribute("tokenEnvKey"));
        assertFalse(Boolean.TRUE.equals(identity.getAttribute("handlesOwnedSpaces")));
    }

    @Test
    void buildBotsHandlesOwnedSpacesStringTrueFalseAccepted(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: str-true
                persona:
                  name: Str
                  systemPrompt: ""
                identities:
                  discord:
                    tokenEnvKey: T
                    handlesOwnedSpaces: "true"
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertTrue(Boolean.TRUE.equals(bots.get(0).getConnectorIdentity("discord").orElseThrow().getAttribute("handlesOwnedSpaces")));
    }

    @Test
    void buildBotsNoDiscordIdentityDoesNotCrash(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: no-identity
                persona:
                  name: NoId
                  systemPrompt: ""
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        assertEquals(1, bots.size());
        assertTrue(bots.get(0).getConnectorIdentity("discord").isEmpty());
        assertTrue(bots.get(0).getConnectorIdentity("unknown").isEmpty());
    }

    @Test
    void buildBotsConnectorIdentityTypedAccessors(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: typed
                persona:
                  name: Typed
                  systemPrompt: ""
                identities:
                  discord:
                    tokenEnvKey: MY_TOKEN
                    handlesOwnedSpaces: true
            routing: []
            """);
        BotConfig config = loader.loadFromPath(yaml);
        List<BotDefinition> bots = loader.buildBots(config);
        var identity = bots.get(0).getConnectorIdentity("discord").orElseThrow();
        assertEquals("MY_TOKEN", identity.getString("tokenEnvKey"));
        assertTrue(identity.getBoolean("handlesOwnedSpaces"));
    }
}
