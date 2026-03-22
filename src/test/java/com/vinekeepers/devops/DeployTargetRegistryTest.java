package com.vinekeepers.devops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployTargetRegistryTest {

    @Test
    void loadParsesTargets(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("deploy-targets.yaml");
        Files.writeString(f, """
                targets:
                  - id: app_a
                    label: Application A
                    playbook: playbooks/a.yml
                """);
        DeployTargetRegistry reg = DeployTargetRegistry.load(f);
        assertEquals(1, reg.getTargets().size());
        assertTrue(reg.findById("app_a").isPresent());
        assertEquals("playbooks/a.yml", reg.findById("app_a").orElseThrow().getPlaybook());
    }

    @Test
    void loadParsesLegacyProjectsKey(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("legacy.yaml");
        Files.writeString(f, """
                projects:
                  - id: legacy_x
                    label: X
                    playbook: p.yml
                """);
        DeployTargetRegistry reg = DeployTargetRegistry.load(f);
        assertEquals(1, reg.getTargets().size());
        assertTrue(reg.findById("legacy_x").isPresent());
    }

    @Test
    void loadMissingFileReturnsEmpty() {
        DeployTargetRegistry reg = DeployTargetRegistry.load(Path.of("nonexistent-deploy-targets.yaml"));
        assertTrue(reg.getTargets().isEmpty());
    }

    @Test
    void loadParsesGitRemoteExtraVarsAndCompose(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("t.yaml");
        Files.writeString(f, """
                targets:
                  - id: svc
                    label: Service
                    playbook: playbooks/svc.yml
                    gitRemote: https://example.com/repo.git
                    extraVars:
                      checkout_path: /srv/app
                    compose:
                      file: docker-compose.yml
                      workingDirectory: /work
                      services:
                        - web
                        - db
                      hostOpsExecutor: cursor_agent
                """);
        DeployTargetRegistry reg = DeployTargetRegistry.load(f);
        DeployTarget p = reg.findById("svc").orElseThrow();
        assertEquals("https://example.com/repo.git", p.getGitRemote());
        assertEquals("/srv/app", p.getExtraVars().get("checkout_path"));
        assertTrue(p.getCompose().isConfigured());
        assertEquals("docker-compose.yml", p.getCompose().getFile());
        assertEquals("/work", p.getCompose().getWorkingDirectory());
        assertEquals(2, p.getCompose().getServices().size());
        assertEquals(ComposeHostExecutor.CURSOR_AGENT, p.getCompose().getExecutor());
    }
}
