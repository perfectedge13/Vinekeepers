package com.vinekeepers.devops;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostComposeOpsRunnerTest {

    @Test
    void buildDockerComposeUpWithServices() {
        DeployTargetCompose c = new DeployTargetCompose("dc.yml", "/w", List.of("a", "b"), ComposeHostExecutor.DIRECT);
        List<String> cmd = HostComposeOpsRunner.buildDockerComposeCommandForTest(c, ComposeOperation.UP, List.of("a"));
        assertTrue(cmd.contains("docker"));
        assertTrue(cmd.contains("compose"));
        assertTrue(cmd.contains("-f"));
        assertTrue(cmd.contains("dc.yml"));
        assertTrue(cmd.contains("up"));
        assertTrue(cmd.contains("-d"));
        assertTrue(cmd.contains("a"));
    }

    @Test
    void buildDockerComposeRestartWithServices() {
        DeployTargetCompose c = new DeployTargetCompose("dc.yml", "/w", List.of("web", "api"), ComposeHostExecutor.DIRECT);
        List<String> cmd = HostComposeOpsRunner.buildDockerComposeCommandForTest(c, ComposeOperation.RESTART, List.of("web"));
        assertEquals(List.of("docker", "compose", "-f", "dc.yml", "restart", "web"), cmd);
    }

    @Test
    void buildCursorPromptContainsDockerCompose() {
        DeployTargetCompose c = new DeployTargetCompose("dc.yml", "/w", List.of("svc"), ComposeHostExecutor.DIRECT);
        String prompt = HostComposeOpsRunner.buildCursorPromptForTest(c, ComposeOperation.PS, List.of());
        assertTrue(prompt.contains("docker"));
        assertTrue(prompt.contains("compose"));
    }

    @Test
    void composeOperationParse() {
        assertEquals(ComposeOperation.STOP, ComposeOperation.parse("stop"));
        assertEquals(ComposeOperation.PS, ComposeOperation.parse("ps"));
        assertEquals(ComposeOperation.RESTART, ComposeOperation.parse("restart"));
    }
}
