package com.vinekeepers.workflow.planning.rag;

import java.util.Objects;

/**
 * One grounded text chunk scoped to a repo ref and branch, persisted in Parquet and indexed in Qdrant.
 */
public final class GroundingChunkRecord {

    private final String contentHash;
    private final String repoRef;
    private final String branch;
    private final String relPath;
    private final String chunkText;
    private final String embeddingModel;
    private final long updatedAtMs;

    public GroundingChunkRecord(
            String contentHash,
            String repoRef,
            String branch,
            String relPath,
            String chunkText,
            String embeddingModel,
            long updatedAtMs) {
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.repoRef = Objects.requireNonNull(repoRef, "repoRef");
        this.branch = Objects.requireNonNull(branch, "branch");
        this.relPath = relPath != null ? relPath : "";
        this.chunkText = Objects.requireNonNull(chunkText, "chunkText");
        this.embeddingModel = embeddingModel != null ? embeddingModel : "";
        this.updatedAtMs = updatedAtMs;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getRepoRef() {
        return repoRef;
    }

    public String getBranch() {
        return branch;
    }

    public String getRelPath() {
        return relPath;
    }

    public String getChunkText() {
        return chunkText;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }
}
