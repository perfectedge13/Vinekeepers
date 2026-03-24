package com.vinekeepers.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ArriettyProductionResidueTest {

    @Test
    void productionArchitectureFiles_doNotReferenceDeletedLegacyWorkflow() throws Exception {
        for (Path path : List.of(
                Path.of("README.md"),
                Path.of("specs", "workflow-registry.yml"),
                Path.of("specs", "config-registry.yml"),
                Path.of("specs", "bot-registry.yml"),
                Path.of("mkdoc", "features", "domain", "workflow", "workflow.md"),
                Path.of("mkdoc", "features", "domain", "workflow", "workflow-steps.md"),
                Path.of("mkdoc", "features", "domain", "workflow", "workflow", "how-it-works.md"),
                Path.of("mkdoc", "features", "domain", "workflow", "cursor-gathering", "how-it-works.md"),
                Path.of("mkdoc", "features", "domain", "workflow", "cursor-gathering", "contracts.md"),
                Path.of("mkdoc", "features", "domain", "workflow", "cursor-gathering", "change-log.md"),
                Path.of("mkdoc", "features", "domain", "workflow", "cursor-gathering", "tests.md"),
                Path.of("mkdoc", "architecture.md"))) {
            String text = Files.readString(path);
            assertFalse(text.contains("arrietty_room_legacy"), path + " must not mention deleted production workflow");
        }
    }
}
