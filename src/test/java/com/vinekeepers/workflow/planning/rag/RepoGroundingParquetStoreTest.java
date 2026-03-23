package com.vinekeepers.workflow.planning.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoGroundingParquetStoreTest {

    @Test
    void loadAll_missingFile_returnsEmpty() {
        RepoGroundingParquetStore store = new RepoGroundingParquetStore();
        assertTrue(store.loadAll(Path.of("/nonexistent/parquet/file.parquet")).isEmpty());
    }

    @Test
    void mergeByScope_replacesScopeRows_andDedupesByHash() {
        RepoGroundingParquetStore store = new RepoGroundingParquetStore();
        List<GroundingChunkRecord> existing =
                List.of(
                        new GroundingChunkRecord("h1", "r", "b1", "a.java", "old", "m", 1L),
                        new GroundingChunkRecord("h2", "r", "b2", "b.java", "keep", "m", 2L));
        List<GroundingChunkRecord> scopeNew =
                List.of(
                        new GroundingChunkRecord("h1", "r", "b1", "a.java", "new", "m", 3L),
                        new GroundingChunkRecord("h3", "r", "b1", "c.java", "x", "m", 4L));
        List<GroundingChunkRecord> merged = store.mergeByScope(existing, "r", "b1", scopeNew);
        assertEquals(3, merged.size());
        GroundingChunkRecord h1 =
                merged.stream().filter(r -> "h1".equals(r.getContentHash())).findFirst().orElseThrow();
        assertEquals("new", h1.getChunkText());
        assertTrue(merged.stream().anyMatch(r -> "h2".equals(r.getContentHash())));
        assertTrue(merged.stream().anyMatch(r -> "h3".equals(r.getContentHash())));
    }

    @Test
    void parquetPath_underVinekeepersDir(@TempDir Path repo) {
        RepoGroundingParquetStore store = new RepoGroundingParquetStore();
        Path p = store.parquetPath(repo);
        assertTrue(p.toString().replace('\\', '/').endsWith(".vinekeepers/repo-grounding.parquet"));
    }
}
