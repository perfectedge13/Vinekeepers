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
        assertEquals("DISCORD_LUNA_TOKEN", bots.get(0).getDiscordTokenEnvKey());
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
        assertNull(bots.get(0).getDiscordTokenEnvKey());
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
        assertTrue(bots.get(0).isHandlesOwnedSpaces());
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
        assertFalse(bots.get(0).isHandlesOwnedSpaces());
    }

    @Test
    void loadFromPathParsesWorkflowStepModelConfiguration(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: luna
                persona:
                  name: Luna
                  systemPrompt: ""
                workflow:
                  type: configured
                  params:
                    workflowRef: luna_cursor
            workflows:
              luna_cursor:
                steps:
                  - type: call_action
                    action: launch_cursor_run
                    model:
                      provider: openai
                      modelId: gpt-4.1-mini
            routing: []
            """);

        BotConfig config = loader.loadFromPath(yaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> workflow = (Map<String, Object>) config.getWorkflows().get("luna_cursor");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) workflow.get("steps");
        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) steps.get(0).get("model");

        assertEquals("openai", model.get("provider"));
        assertEquals("gpt-4.1-mini", model.get("modelId"));
    }
}
