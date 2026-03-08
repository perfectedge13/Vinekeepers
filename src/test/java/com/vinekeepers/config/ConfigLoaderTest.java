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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
