package com.vinekeepers.workflow.planning.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoFileChunkerTest {

    @Test
    void chunkRepo_emitsChunksWithRepoRefAndBranch(@TempDir Path repo) throws Exception {
        Path src = Files.createDirectories(repo.resolve("src"));
        Files.writeString(src.resolve("Hello.java"), "class Hello { String x = \"0123456789\".repeat(400); }");
        RepoFileChunker chunker = new RepoFileChunker();
        List<GroundingChunkRecord> chunks = chunker.chunkRepo(repo, "my/repo", "feature/x", 99L, "text-embedding-3-small");
        assertFalse(chunks.isEmpty());
        for (GroundingChunkRecord c : chunks) {
            assertEquals("my/repo", c.getRepoRef());
            assertEquals("feature/x", c.getBranch());
            assertEquals("text-embedding-3-small", c.getEmbeddingModel());
            assertEquals(99L, c.getUpdatedAtMs());
            assertTrue(c.getRelPath().replace('\\', '/').endsWith("Hello.java"));
        }
    }

    @Test
    void chunkRepo_skipsVinekeepersTree(@TempDir Path repo) throws Exception {
        Path vk = Files.createDirectories(repo.resolve(".vinekeepers"));
        Files.writeString(vk.resolve("note.txt"), "secret");
        RepoFileChunker chunker = new RepoFileChunker();
        List<GroundingChunkRecord> chunks = chunker.chunkRepo(repo, "r", "b", 1L, "m");
        assertTrue(chunks.isEmpty());
    }
}
