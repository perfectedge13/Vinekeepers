package com.vinekeepers.tools;

import com.vinekeepers.bot.ToolPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRunnerTest {

    @Test
    void runExecutesRegisteredToolWhenAllowed() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        ToolRunner runner = new ToolRunner(registry);

        Object result = runner.run("echo", Map.of("message", "hello"), ToolPolicy.allowAll());

        assertEquals("hello", result);
    }

    @Test
    void runRejectsDeniedToolBeforeExecution() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        ToolRunner runner = new ToolRunner(registry);
        ToolPolicy policy = new ToolPolicy(Set.of("echo"), Set.of("echo"));

        SecurityException error = assertThrows(SecurityException.class,
                () -> runner.run("echo", Map.of("message", "blocked"), policy));

        assertEquals("Tool not allowed: echo", error.getMessage());
    }

    @Test
    void runRejectsUnknownTool() {
        ToolRunner runner = new ToolRunner(new ToolRegistry());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> runner.run("missing", Map.of(), ToolPolicy.allowAll()));

        assertEquals("Unknown tool: missing", error.getMessage());
    }
}
