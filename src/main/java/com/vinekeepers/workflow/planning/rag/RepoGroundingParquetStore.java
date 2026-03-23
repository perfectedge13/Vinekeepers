package com.vinekeepers.workflow.planning.rag;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and saves {@value #FILE_NAME} under {@code <repo>/.vinekeepers/}. Corrupt files are treated as empty on read.
 */
public final class RepoGroundingParquetStore {

    private static final Logger log = LoggerFactory.getLogger(RepoGroundingParquetStore.class);

    public static final String FILE_NAME = "repo-grounding.parquet";

    private static final Schema AVRO_SCHEMA = new Schema.Parser()
            .parse(
                    """
                    {
                      "type": "record",
                      "name": "RepoGroundingRow",
                      "namespace": "com.vinekeepers.planning",
                      "fields": [
                        {"name": "content_hash", "type": "string"},
                        {"name": "repo_ref", "type": "string"},
                        {"name": "branch", "type": "string"},
                        {"name": "rel_path", "type": ["null", "string"], "default": null},
                        {"name": "chunk_text", "type": "string"},
                        {"name": "embedding_model", "type": ["null", "string"], "default": null},
                        {"name": "updated_at_ms", "type": "long"}
                      ]
                    }
                    """);

    private final Configuration hadoopConf = new Configuration();

    public java.nio.file.Path parquetPath(java.nio.file.Path repoRoot) {
        return repoRoot.resolve(".vinekeepers").resolve(FILE_NAME);
    }

    /**
     * Reads all rows from an existing file. Returns empty list if missing; logs and returns empty on corruption.
     */
    public List<GroundingChunkRecord> loadAll(java.nio.file.Path parquetFile) {
        if (parquetFile == null || !Files.isRegularFile(parquetFile)) {
            return List.of();
        }
        List<GroundingChunkRecord> rows = new ArrayList<>();
        Path hadoopPath = new Path(parquetFile.toUri().toString());
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(hadoopPath)
                .withConf(hadoopConf)
                .build()) {
            GenericRecord rec;
            while ((rec = reader.read()) != null) {
                GroundingChunkRecord row = fromGeneric(rec);
                if (row != null) {
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("Planning RAG: ignoring corrupt or unreadable Parquet cache {}: {}", parquetFile, e.getMessage());
            return List.of();
        }
        return rows;
    }

    /**
     * Writes all rows (entire merged dataset) to the repo cache path, atomically replacing when possible.
     */
    public void saveAll(java.nio.file.Path parquetFile, List<GroundingChunkRecord> rows) throws IOException {
        Objects.requireNonNull(parquetFile, "parquetFile");
        java.nio.file.Path parent = parquetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile(parent != null ? parent : parquetFile.getParent(), "grounding-", ".parquet");
        Path out = new Path(tmp.toUri().toString());
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(out)
                .withSchema(AVRO_SCHEMA)
                .withConf(hadoopConf)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build()) {
            for (GroundingChunkRecord r : rows) {
                writer.write(toGeneric(r));
            }
        }
        try {
            Files.move(tmp, parquetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception unsupported) {
            Files.move(tmp, parquetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Merge: replace rows matching (repo_ref, branch) with {@code scopeRows}; keep others. */
    public List<GroundingChunkRecord> mergeByScope(
            List<GroundingChunkRecord> existingAll, String repoRef, String branch, List<GroundingChunkRecord> scopeRows) {
        String ref = repoRef != null ? repoRef.trim() : "";
        String br = branch != null ? branch.trim() : "";
        List<GroundingChunkRecord> kept = new ArrayList<>();
        for (GroundingChunkRecord r : existingAll) {
            if (!(ref.equals(r.getRepoRef()) && br.equals(r.getBranch()))) {
                kept.add(r);
            }
        }
        Map<String, GroundingChunkRecord> byHash = new LinkedHashMap<>();
        for (GroundingChunkRecord r : kept) {
            byHash.put(r.getContentHash(), r);
        }
        for (GroundingChunkRecord r : scopeRows) {
            byHash.put(r.getContentHash(), r);
        }
        return new ArrayList<>(byHash.values());
    }

    private static GroundingChunkRecord fromGeneric(GenericRecord rec) {
        try {
            String hash = asString(rec.get("content_hash"));
            String ref = asString(rec.get("repo_ref"));
            String branch = asString(rec.get("branch"));
            String rel = rec.get("rel_path") != null ? asString(rec.get("rel_path")) : "";
            String text = asString(rec.get("chunk_text"));
            String model = rec.get("embedding_model") != null ? asString(rec.get("embedding_model")) : "";
            long updated = rec.get("updated_at_ms") instanceof Long l ? l : Long.parseLong(asString(rec.get("updated_at_ms")));
            if (hash.isBlank() || ref.isBlank() || branch.isBlank() || text.isBlank()) {
                return null;
            }
            return new GroundingChunkRecord(hash, ref, branch, rel, text, model, updated);
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : "";
    }

    private static GenericRecord toGeneric(GroundingChunkRecord r) {
        GenericRecord rec = new GenericData.Record(AVRO_SCHEMA);
        rec.put("content_hash", r.getContentHash());
        rec.put("repo_ref", r.getRepoRef());
        rec.put("branch", r.getBranch());
        rec.put("rel_path", r.getRelPath().isBlank() ? null : r.getRelPath());
        rec.put("chunk_text", r.getChunkText());
        rec.put("embedding_model", r.getEmbeddingModel().isBlank() ? null : r.getEmbeddingModel());
        rec.put("updated_at_ms", r.getUpdatedAtMs());
        return rec;
    }
}
