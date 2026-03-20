package com.vinekeepers.core;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.WorkflowAction;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.actions.StartCoordinatorPlanningAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Bootstrap: loadConfig builds router and engine; withDiscord invokes adapter without throwing.
 * HandlesMap from getConnectorIdentity is applied in loadConfig (covered by ConfigLoader + Router tests).
 */
class BootstrapTest {

    @Test
    void bootstrapConstructor_wiresStartCoordinatorPlanningWithNonNullEngine() throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        Field regField = Bootstrap.class.getDeclaredField("actionRegistry");
        regField.setAccessible(true);
        WorkflowActionRegistry registry = (WorkflowActionRegistry) regField.get(bootstrap);
        Field actionsField = WorkflowActionRegistry.class.getDeclaredField("actions");
        actionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, WorkflowAction> actions = (Map<String, WorkflowAction>) actionsField.get(registry);
        WorkflowAction action = actions.get("start_coordinator_planning");
        assertNotNull(action);
        assertInstanceOf(StartCoordinatorPlanningAction.class, action);
        Field engineField = StartCoordinatorPlanningAction.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        assertNotNull(engineField.get(action));
    }

    @Test
    void loadConfig_fromFileWithBotsAndRouting_buildsRouterAndEngine(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: luna
                persona:
                  name: Luna
                  systemPrompt: "You are Luna"
                identities:
                  discord:
                    handlesOwnedSpaces: true
            routing:
              - botId: luna
                filter: {}
            """);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.loadConfig(yaml);
        assertNotNull(bootstrap.getRouter());
        assertNotNull(bootstrap.getEngine());
        // Router has routing: event matches luna
        List<String> botIds = bootstrap.getRouter().route(new Event("discord:g:ch", "message", Map.of()));
        assertEquals(List.of("luna"), botIds);
    }

    @Test
    void loadConfig_fromMissingPath_doesNotThrow() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.loadConfig(Path.of("nonexistent-bots.yaml"));
        assertNotNull(bootstrap.getRouter());
        assertNotNull(bootstrap.getEngine());
    }

    @Test
    void withDiscord_afterLoadConfig_doesNotThrow() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.loadConfig(Path.of("nonexistent")); // no config loaded, lastLoadedBots empty
        assertDoesNotThrow(() -> bootstrap.withDiscord());
    }

    @Test
    void withDiscord_afterLoadConfigWithBotsNoTokens_doesNotThrow(@TempDir Path dir) throws Exception {
        Path yaml = dir.resolve("bots.yaml");
        Files.writeString(yaml, """
            bots:
              - id: test-bot
                persona:
                  name: Test
                  systemPrompt: ""
            routing: []
            """);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.loadConfig(yaml);
        // No discord token env keys → adapter runs but adds no sources; should not throw
        assertDoesNotThrow(() -> bootstrap.withDiscord());
        assertNotNull(bootstrap.getEventBus());
        assertNotNull(bootstrap.getEngine());
    }
}
