package com.vinekeepers.core.cursor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorInstructionComposerTest {

    @Test
    void buildInstructionIncludesNovaCode() {
        String out = CursorInstructionComposer.buildInstruction(
                "https://github.com/owner/repo",
                "main",
                "Add unit tests");
        assertNotNull(out);
        assertTrue(out.contains("/nova-code"), "prompt must include /nova-code, got: " + out);
    }

    @Test
    void buildInstructionIncludesRepositoryAndBranchAndRequest() {
        String out = CursorInstructionComposer.buildInstruction(
                "https://github.com/org/proj",
                "develop",
                "Phase 1 completion");
        assertNotNull(out);
        assertTrue(out.contains("https://github.com/org/proj"), "prompt must include repository URL");
        assertTrue(out.contains("develop"), "prompt must include base branch");
        assertTrue(out.contains("Phase 1 completion"), "prompt must include user request");
    }

    @Test
    void buildInstructionDefaultsBranchToMainWhenBlank() {
        String out = CursorInstructionComposer.buildInstruction("https://github.com/a/b", "", "req");
        assertTrue(out.contains("main"), "blank branch should default to main");
    }

    @Test
    void buildInstructionRejectsNullRepository() {
        assertThrows(NullPointerException.class,
                () -> CursorInstructionComposer.buildInstruction(null, "main", "req"));
    }
}
