package com.vinekeepers.workflow.planning.rag;

import com.vinekeepers.env.Env;

/**
 * Optional planning RAG configuration from environment. Invalid or incomplete config disables attempts gracefully.
 */
public final class PlanningRagConfig {

    private final boolean featureToggle;
    private final String qdrantUrl;
    private final String qdrantApiKey;
    private final String collection;
    private final String embeddingModel;
    private final int embedBatchSize;
    private final int maxChunksPerRun;
    private final int searchTopK;
    private final int vectorSize;

    public PlanningRagConfig(
            boolean featureToggle,
            String qdrantUrl,
            String qdrantApiKey,
            String collection,
            String embeddingModel,
            int embedBatchSize,
            int maxChunksPerRun,
            int searchTopK,
            int vectorSize) {
        this.featureToggle = featureToggle;
        this.qdrantUrl = qdrantUrl != null ? qdrantUrl.trim() : "";
        this.qdrantApiKey = qdrantApiKey != null ? qdrantApiKey.trim() : "";
        this.collection = collection != null && !collection.isBlank() ? collection.trim() : "vinekeepers_planning_grounding";
        this.embeddingModel =
                embeddingModel != null && !embeddingModel.isBlank()
                        ? embeddingModel.trim()
                        : Env.get("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small");
        this.embedBatchSize = Math.max(1, Math.min(256, embedBatchSize));
        this.maxChunksPerRun = Math.max(1, maxChunksPerRun);
        this.searchTopK = Math.max(1, Math.min(32, searchTopK));
        this.vectorSize = Math.max(32, Math.min(8192, vectorSize));
    }

    public static PlanningRagConfig fromEnv() {
        String toggle = Env.get("VINEKEEPERS_PLANNING_RAG", "true");
        boolean on = toggle == null || !"false".equalsIgnoreCase(toggle.trim());
        String url = firstNonBlank(Env.get("QDRANT_URL", ""), Env.get("VINEKEEPERS_QDRANT_URL", ""));
        String key = firstNonBlank(Env.get("QDRANT_API_KEY", ""), Env.get("VINEKEEPERS_QDRANT_API_KEY", ""));
        String coll = Env.get("QDRANT_COLLECTION", "vinekeepers_planning_grounding");
        String model = Env.get("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small");
        int batch = parseInt(Env.get("PLANNING_RAG_EMBED_BATCH_SIZE", "64"), 64);
        int max = parseInt(Env.get("PLANNING_RAG_MAX_CHUNKS", "4000"), 4000);
        int topK = parseInt(Env.get("PLANNING_RAG_TOP_K", "8"), 8);
        int dim = parseInt(Env.get("OPENAI_EMBEDDING_DIMENSIONS", "1536"), 1536);
        return new PlanningRagConfig(on, url, key, coll, model, batch, max, topK, dim);
    }

    /** When false, RAG is not attempted (spread: disabled). */
    public boolean featureEnabled() {
        return featureToggle;
    }

    /** True when URL present; API key optional for local Qdrant. */
    public boolean qdrantConfigured() {
        return !qdrantUrl.isBlank();
    }

    public String qdrantUrl() {
        return qdrantUrl;
    }

    public String qdrantApiKey() {
        return qdrantApiKey;
    }

    public String collection() {
        return collection;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public int embedBatchSize() {
        return embedBatchSize;
    }

    public int maxChunksPerRun() {
        return maxChunksPerRun;
    }

    public int searchTopK() {
        return searchTopK;
    }

    public int vectorSize() {
        return vectorSize;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b != null ? b.trim() : "";
    }

    private static int parseInt(String raw, int dflt) {
        if (raw == null || raw.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
