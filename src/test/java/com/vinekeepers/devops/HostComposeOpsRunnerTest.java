package com.vinekeepers.devops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void validateDirectComposePrerequisitesReportsMissingWorkingDirectory() {
        Path missing = Path.of("target", "missing-compose-dir");
        String msg = HostComposeOpsRunner.validateDirectComposePrerequisitesForTest(missing, "compose.yaml", "docker");
        assertTrue(msg.contains("working directory"));
        assertTrue(msg.contains("config/deploy-targets.yaml"));
    }

    @Test
    void validateDirectComposePrerequisitesReportsMissingComposeFile() throws Exception {
        Path workingDir = Files.createTempDirectory("host-compose-runner-test");
        String msg = HostComposeOpsRunner.validateDirectComposePrerequisitesForTest(workingDir, "compose.yaml", "docker");
        assertTrue(msg.contains("compose.yaml"));
        assertTrue(msg.contains("Mount the stack directory"));
    }

    @Test
    void validateDirectComposePrerequisitesReportsMissingDockerBinary() throws Exception {
        Path workingDir = Files.createTempDirectory("host-compose-runner-test");
        Files.writeString(workingDir.resolve("compose.yaml"), "services: {}\n");
        String msg = HostComposeOpsRunner.validateDirectComposePrerequisitesForTest(
                workingDir, "compose.yaml", "definitely-not-a-real-docker-binary");
        assertTrue(msg.contains("Docker binary"));
        assertTrue(msg.contains("DEPLOY_COMPOSE_BINARY"));
    }

    @Test
    void containerWarningsMentionMissingSocketAndWorkingDirectory() {
        DeployTargetRegistry registry = new DeployTargetRegistry(List.of(
                new DeployTarget(
                        "wikijs",
                        "Wiki.js",
                        null,
                        null,
                        java.util.Map.of(),
                        new DeployTargetCompose("docker-compose.yml", "/definitely/missing/wikijs", List.of("wikijs"), ComposeHostExecutor.DIRECT))));

        List<String> warnings = HostComposeOpsRunner.describeContainerDirectComposeWarningsForTest(
                registry, Path.of("config", "deploy-targets.yaml"));

        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("/var/run/docker.sock"));
        assertTrue(warnings.get(1).contains("wikijs"));
        assertTrue(warnings.get(1).contains("DEPLOY_TARGETS_PATH"));
    }

    @Test
    void containerWarningsMentionMissingComposeFileInsideMountedDirectory() throws Exception {
        Path workingDir = Files.createTempDirectory("host-compose-warning-test");
        DeployTargetRegistry registry = new DeployTargetRegistry(List.of(
                new DeployTarget(
                        "neo4j",
                        "Neo4j",
                        null,
                        null,
                        java.util.Map.of(),
                        new DeployTargetCompose("docker-compose.yml", workingDir.toString(), List.of("neo4j"), ComposeHostExecutor.DIRECT))));

        List<String> warnings = HostComposeOpsRunner.describeContainerDirectComposeWarningsForTest(
                registry, Path.of("config", "deploy-targets.docker.yaml"));

        assertEquals(2, warnings.size());
        assertTrue(warnings.get(1).contains("docker-compose.yml"));
        assertTrue(warnings.get(1).contains("deploy-targets.docker.yaml"));
    }
}
