package com.vinekeepers.workflow.planning.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "planning-rag-env-props", mode = ResourceAccessMode.READ_WRITE)
class PlanningRagConfigTest {

    private static final String[] KEYS = {
        "VINEKEEPERS_PLANNING_RAG",
        "QDRANT_URL",
        "VINEKEEPERS_QDRANT_URL",
        "QDRANT_API_KEY",
        "VINEKEEPERS_QDRANT_API_KEY",
        "QDRANT_COLLECTION",
        "OPENAI_EMBEDDING_MODEL",
        "PLANNING_RAG_EMBED_BATCH_SIZE",
        "PLANNING_RAG_MAX_CHUNKS",
        "PLANNING_RAG_TOP_K",
        "OPENAI_EMBEDDING_DIMENSIONS"
    };

    @AfterEach
    void clearProps() {
        for (String k : KEYS) {
            System.clearProperty(k);
        }
    }

    @Test
    void constructor_clampsNumericFields() {
        PlanningRagConfig c =
                new PlanningRagConfig(true, "http://q", "", "c", "m", 0, 0, 99, 99999);
        assertEquals(1, c.embedBatchSize());
        assertEquals(1, c.maxChunksPerRun());
        assertEquals(32, c.searchTopK());
        assertEquals(8192, c.vectorSize());
    }

    @Test
    void fromEnv_featureOff_viaProperty() {
        System.setProperty("VINEKEEPERS_PLANNING_RAG", "false");
        PlanningRagConfig c = PlanningRagConfig.fromEnv();
        assertFalse(c.featureEnabled());
    }

    @Test
    void fromEnv_qdrantUrlFromEitherProperty() {
        System.setProperty("VINEKEEPERS_QDRANT_URL", "http://alt:6333");
        PlanningRagConfig c = PlanningRagConfig.fromEnv();
        assertTrue(c.qdrantConfigured());
        assertEquals("http://alt:6333", c.qdrantUrl());
    }

    @Test
    void fromEnv_primaryQdrantUrlWins() {
        System.setProperty("QDRANT_URL", "http://main:6333");
        System.setProperty("VINEKEEPERS_QDRANT_URL", "http://alt:6333");
        PlanningRagConfig c = PlanningRagConfig.fromEnv();
        assertEquals("http://main:6333", c.qdrantUrl());
    }
}
