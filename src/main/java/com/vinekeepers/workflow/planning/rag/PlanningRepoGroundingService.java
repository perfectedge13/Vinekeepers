package com.vinekeepers.workflow.planning.rag;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loads Parquet cache, chunks the workspace, embeds missing rows into Qdrant, exports merged Parquet, and runs a
 * scoped vector search for planning prompts.
 */
public final class PlanningRepoGroundingService {

    private static final Logger log = LoggerFactory.getLogger(PlanningRepoGroundingService.class);
    private static final int CHUNK_PREVIEW_CHARS = 2000;

    private final OpenAiChatClient openAi;
    private final RepoGroundingParquetStore parquetStore;
    private final RepoFileChunker chunker;
    private final GitRepoBranchResolver branchResolver;

    public PlanningRepoGroundingService(OpenAiChatClient openAi) {
        this.openAi = openAi;
        this.parquetStore = new RepoGroundingParquetStore();
        this.chunker = new RepoFileChunker();
        this.branchResolver = new GitRepoBranchResolver();
    }

    public Map<String, Object> run(Map<String, Object> state, Map<String, Object> bind, FeaturePlanState plan) {
        Map<String, Object> spread = baseSpread();
        PlanningRagConfig cfg = PlanningRagConfig.fromEnv();
        spread.put("planningRagCollection", cfg.collection());

        if (!cfg.featureEnabled()) {
            spread.put("planningRagSource", "disabled_toggle");
            return spread;
        }

        boolean depsOk = cfg.qdrantConfigured() && openAi != null && openAi.isConfigured();
        spread.put("planningRagEnabled", depsOk ? "true" : "false");
        if (!depsOk) {
            spread.put("planningRagSource", "disabled_config");
            spread.put(
                    "planningRagError",
                    !cfg.qdrantConfigured() ? "QDRANT_URL not set" : "OPENAI_API_KEY not set");
            return spread;
        }

        if (plan == null) {
            spread.put("planningRagError", "NO_PLAN");
            return spread;
        }

        if (!workspacePathReady(plan)) {
            spread.put("planningRagError", "WORKSPACE_NOT_READY");
            spread.put("planningRagSource", "no_workspace");
            return spread;
        }

        String local = plan.getRepoLocalPath() != null ? plan.getRepoLocalPath().trim() : "";
        if (local.isBlank()) {
            spread.put("planningRagError", "NO_LOCAL_PATH");
            spread.put("planningRagSource", "no_workspace");
            return spread;
        }

        Path repoRoot = Path.of(local);
        if (!java.nio.file.Files.isDirectory(repoRoot)) {
            spread.put("planningRagError", "LOCAL_PATH_NOT_DIR");
            spread.put("planningRagSource", "no_workspace");
            return spread;
        }

        String repoRef = plan.getRepoRef() != null ? plan.getRepoRef().trim() : "";
        String branch = branchResolver.resolve(repoRoot, state, bind);
        long now = System.currentTimeMillis();
        String model = cfg.embeddingModel();

        Path parquetPath = parquetStore.parquetPath(repoRoot);
        List<GroundingChunkRecord> existingAll = parquetStore.loadAll(parquetPath);
        int importRows = existingAll.size();
        spread.put("planningRagParquetImportRows", String.valueOf(importRows));
        spread.put("planningRagSource", importRows > 0 ? "parquet_import" : "cold_start");

        List<GroundingChunkRecord> diskChunks;
        try {
            diskChunks = chunker.chunkRepo(repoRoot, repoRef, branch, now, model);
        } catch (Exception e) {
            log.warn("Planning RAG: chunk scan failed: {}", e.getMessage());
            spread.put("planningRagError", "CHUNK_SCAN_FAILED");
            spread.put("planningRagSource", "degraded");
            return spread;
        }

        if (diskChunks.size() > cfg.maxChunksPerRun()) {
            diskChunks = new ArrayList<>(diskChunks.subList(0, cfg.maxChunksPerRun()));
            spread.put("planningRagChunkCapApplied", "true");
        }
        spread.put("planningRagChunkScanCount", String.valueOf(diskChunks.size()));

        Set<String> knownHashes = new HashSet<>();
        for (GroundingChunkRecord r : existingAll) {
            if (repoRef.equals(r.getRepoRef()) && branch.equals(r.getBranch())) {
                knownHashes.add(r.getContentHash());
            }
        }

        List<GroundingChunkRecord> toIndex = new ArrayList<>();
        for (GroundingChunkRecord c : diskChunks) {
            if (!knownHashes.contains(c.getContentHash())) {
                toIndex.add(c);
            }
        }

        HttpClient qHttp =
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(15_000)).build();
        QdrantPlanningClient qdrant =
                new QdrantPlanningClient(qHttp, cfg.qdrantUrl(), cfg.qdrantApiKey(), OpenAiChatClient.planningRequestTimeoutMs());

        long embedMs = 0;
        long upsertMs = 0;
        if (!toIndex.isEmpty()) {
            try {
                qdrant.ensureCollection(cfg.collection(), cfg.vectorSize());
            } catch (Exception e) {
                log.warn("Planning RAG: Qdrant collection ensure failed: {}", e.getMessage());
                spread.put("planningRagError", "QDRANT_COLLECTION_FAILED");
                spread.put("planningRagSource", "degraded");
                finishWithoutSearch(spread, existingAll, diskChunks, repoRef, branch, parquetPath);
                return spread;
            }

            int indexed = 0;
            int batchSize = cfg.embedBatchSize();
            for (int i = 0; i < toIndex.size(); i += batchSize) {
                int end = Math.min(toIndex.size(), i + batchSize);
                List<GroundingChunkRecord> batch = toIndex.subList(i, end);
                List<String> texts = new ArrayList<>();
                for (GroundingChunkRecord r : batch) {
                    texts.add(r.getChunkText());
                }
                long t0 = System.nanoTime();
                List<float[]> vectors = openAi.embedTexts(texts, model, null);
                embedMs += (System.nanoTime() - t0) / 1_000_000L;
                if (vectors.size() != batch.size()) {
                    log.warn("Planning RAG: embedding batch failed size mismatch");
                    spread.put("planningRagError", "EMBED_FAILED");
                    spread.put("planningRagSource", "degraded");
                    finishWithoutSearch(spread, existingAll, diskChunks, repoRef, branch, parquetPath);
                    return spread;
                }

                List<QdrantPlanningClient.QdrantPoint> points = new ArrayList<>();
                for (int j = 0; j < batch.size(); j++) {
                    GroundingChunkRecord r = batch.get(j);
                    UUID id = deterministicPointId(repoRef, branch, r.getContentHash());
                    String preview = preview(r.getChunkText());
                    points.add(new QdrantPlanningClient.QdrantPoint(
                            id, vectors.get(j), repoRef, branch, r.getContentHash(), r.getRelPath(), preview));
                }
                long u0 = System.nanoTime();
                try {
                    qdrant.upsertPoints(cfg.collection(), points);
                } catch (Exception e) {
                    log.warn("Planning RAG: Qdrant upsert failed: {}", e.getMessage());
                    spread.put("planningRagError", "QDRANT_UPSERT_FAILED");
                    spread.put("planningRagSource", "degraded");
                    finishWithoutSearch(spread, existingAll, diskChunks, repoRef, branch, parquetPath);
                    return spread;
                }
                upsertMs += (System.nanoTime() - u0) / 1_000_000L;
                indexed += batch.size();
            }
            spread.put("planningRagIndexedNewCount", String.valueOf(indexed));
        } else {
            spread.put("planningRagIndexedNewCount", "0");
        }

        spread.put("planningRagEmbedLatencyMs", String.valueOf(embedMs));
        spread.put("planningRagUpsertLatencyMs", String.valueOf(upsertMs));

        exportParquetQuietly(parquetPath, existingAll, diskChunks, repoRef, branch);
        spread.put("planningRagSource", "indexed");

        String query = buildQueryText(plan);
        if (query.isBlank()) {
            spread.put("planningRagAvailable", "true");
            spread.put("planningRagRetrievalText", "");
            spread.put("planningRagSearchLatencyMs", "0");
            return spread;
        }

        if (diskChunks.isEmpty()) {
            spread.put("planningRagAvailable", "true");
            spread.put("planningRagRetrievalText", "");
            spread.put("planningRagSearchLatencyMs", "0");
            return spread;
        }

        try {
            qdrant.ensureCollection(cfg.collection(), cfg.vectorSize());
        } catch (Exception e) {
            log.warn("Planning RAG: Qdrant ensure before search failed: {}", e.getMessage());
            spread.put("planningRagError", "QDRANT_SEARCH_PREP_FAILED");
            spread.put("planningRagAvailable", "false");
            spread.put("planningRagRetrievalText", "");
            spread.put("planningRagSearchLatencyMs", "0");
            return spread;
        }

        long s0 = System.nanoTime();
        List<float[]> qv = openAi.embedTexts(List.of(query), model, null);
        long searchMs = (System.nanoTime() - s0) / 1_000_000L;
        if (qv.size() != 1) {
            spread.put("planningRagError", "QUERY_EMBED_FAILED");
            spread.put("planningRagSource", "degraded");
            spread.put("planningRagAvailable", "false");
            return spread;
        }
        try {
            long s1 = System.nanoTime();
            List<QdrantPlanningClient.SearchHit> hits =
                    qdrant.search(cfg.collection(), qv.get(0), repoRef, branch, cfg.searchTopK());
            searchMs += (System.nanoTime() - s1) / 1_000_000L;
            spread.put("planningRagSearchLatencyMs", String.valueOf(searchMs));
            spread.put("planningRagRetrievalHitCount", String.valueOf(hits.size()));
            spread.put("planningRagRetrievalText", formatHits(hits));
            spread.put("planningRagAvailable", "true");
        } catch (Exception e) {
            log.warn("Planning RAG: search failed: {}", e.getMessage());
            spread.put("planningRagError", "SEARCH_FAILED");
            spread.put("planningRagSearchLatencyMs", String.valueOf(searchMs));
            spread.put("planningRagAvailable", "false");
        }
        return spread;
    }

    private void finishWithoutSearch(
            Map<String, Object> spread,
            List<GroundingChunkRecord> existingAll,
            List<GroundingChunkRecord> diskChunks,
            String repoRef,
            String branch,
            Path parquetPath) {
        exportParquetQuietly(parquetPath, existingAll, diskChunks, repoRef, branch);
        spread.put("planningRagAvailable", "false");
        spread.put("planningRagRetrievalText", "");
        spread.put("planningRagSearchLatencyMs", "0");
    }

    private void exportParquetQuietly(
            Path parquetPath,
            List<GroundingChunkRecord> existingAll,
            List<GroundingChunkRecord> diskChunks,
            String repoRef,
            String branch) {
        try {
            List<GroundingChunkRecord> merged = parquetStore.mergeByScope(existingAll, repoRef, branch, diskChunks);
            parquetStore.saveAll(parquetPath, merged);
        } catch (Exception e) {
            log.warn("Planning RAG: Parquet export failed (continuing without cache file): {}", e.getMessage());
        }
    }

    private static Map<String, Object> baseSpread() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planningRagEnabled", "false");
        m.put("planningRagAvailable", "false");
        m.put("planningRagSource", "none");
        m.put("planningRagError", "");
        m.put("planningRagIndexedNewCount", "0");
        m.put("planningRagChunkScanCount", "0");
        m.put("planningRagParquetImportRows", "0");
        m.put("planningRagEmbedLatencyMs", "0");
        m.put("planningRagUpsertLatencyMs", "0");
        m.put("planningRagSearchLatencyMs", "0");
        m.put("planningRagRetrievalHitCount", "0");
        m.put("planningRagRetrievalText", "");
        m.put("planningRagCollection", "");
        m.put("planningRagChunkCapApplied", "false");
        return m;
    }

    private static boolean workspacePathReady(FeaturePlanState plan) {
        String s = plan.getRepoWorkspaceStatus();
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            RepoWorkspaceStatus st = RepoWorkspaceStatus.valueOf(s.trim().toUpperCase());
            return st == RepoWorkspaceStatus.MATERIALIZED || st == RepoWorkspaceStatus.RESOLVED_LOCAL;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static UUID deterministicPointId(String repoRef, String branch, String hash) {
        String raw = repoRef + "\n" + branch + "\n" + hash;
        return UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String preview(String chunk) {
        if (chunk == null) {
            return "";
        }
        return chunk.length() <= CHUNK_PREVIEW_CHARS ? chunk : chunk.substring(0, CHUNK_PREVIEW_CHARS);
    }

    private static String buildQueryText(FeaturePlanState plan) {
        String title = plan.getTitle() != null ? plan.getTitle().trim() : "";
        String req = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        if (!title.isBlank() && !req.isBlank()) {
            return title + "\n\n" + req;
        }
        return !title.isBlank() ? title : req;
    }

    private static String formatHits(List<QdrantPlanningClient.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (QdrantPlanningClient.SearchHit h : hits) {
            if (h.snippet() == null || h.snippet().isBlank()) {
                continue;
            }
            sb.append("--- snippet ")
                    .append(n++)
                    .append(" (")
                    .append(h.relPath() != null && !h.relPath().isBlank() ? h.relPath() : "unknown path")
                    .append(") ---\n");
            sb.append(h.snippet().trim()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
