package com.vinekeepers.gadget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GadgetProjectRegistryTest {

    @Test
    void loadParsesProjects(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("gadget-projects.yaml");
        Files.writeString(f, """
                projects:
                  - id: app_a
                    label: Application A
                    playbook: playbooks/a.yml
                """);
        GadgetProjectRegistry reg = GadgetProjectRegistry.load(f);
        assertEquals(1, reg.getProjects().size());
        assertTrue(reg.findById("app_a").isPresent());
        assertEquals("playbooks/a.yml", reg.findById("app_a").orElseThrow().getPlaybook());
    }

    @Test
    void loadMissingFileReturnsEmpty() {
        GadgetProjectRegistry reg = GadgetProjectRegistry.load(Path.of("nonexistent-gadget-projects.yaml"));
        assertTrue(reg.getProjects().isEmpty());
    }

    @Test
    void loadParsesGitRemoteAndExtraVars(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("gadget-projects.yaml");
        Files.writeString(f, """
                projects:
                  - id: svc
                    label: Service
                    playbook: playbooks/svc.yml
                    gitRemote: https://example.com/repo.git
                    extraVars:
                      checkout_path: /srv/app
                """);
        GadgetProjectRegistry reg = GadgetProjectRegistry.load(f);
        var p = reg.findById("svc").orElseThrow();
        assertEquals("https://example.com/repo.git", p.getGitRemote());
        assertEquals("/srv/app", p.getExtraVars().get("checkout_path"));
    }
}
