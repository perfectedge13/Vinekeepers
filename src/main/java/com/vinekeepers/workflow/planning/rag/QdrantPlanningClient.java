package com.vinekeepers.workflow.planning.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal Qdrant REST client: ensure collection, upsert points, vector search with payload filter.
 */
public final class QdrantPlanningClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final long timeoutMs;

    public QdrantPlanningClient(HttpClient http, String baseUrl, String apiKey, long timeoutMs) {
        this.http = Objects.requireNonNull(http, "http");
        this.baseUrl = trimSlash(baseUrl != null ? baseUrl : "");
        this.apiKey = apiKey != null ? apiKey : "";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 30_000L;
    }

    public void ensureCollection(String collection, int vectorSize) throws Exception {
        String url = baseUrl + "/collections/" + enc(collection);
        HttpRequest get = baseRequest(url).GET().build();
        HttpResponse<String> resp = http.send(get, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            return;
        }
        ObjectNode body = JSON.createObjectNode();
        ObjectNode vectors = JSON.createObjectNode();
        vectors.put("size", vectorSize);
        vectors.put("distance", "Cosine");
        body.set("vectors", vectors);
        HttpRequest put = baseRequest(url)
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> putResp = http.send(put, HttpResponse.BodyHandlers.ofString());
        if (putResp.statusCode() < 200 || putResp.statusCode() >= 300) {
            throw new IllegalStateException("Qdrant create collection HTTP " + putResp.statusCode() + ": " + putResp.body());
        }
    }

    public void upsertPoints(String collection, List<QdrantPoint> points) throws Exception {
        if (points == null || points.isEmpty()) {
            return;
        }
        String url = baseUrl + "/collections/" + enc(collection) + "/points?wait=true";
        ObjectNode root = JSON.createObjectNode();
        ArrayNode arr = root.putArray("points");
        for (QdrantPoint p : points) {
            ObjectNode pt = arr.addObject();
            pt.put("id", p.id().toString());
            ArrayNode vec = pt.putArray("vector");
            for (float v : p.vector()) {
                vec.add(v);
            }
            ObjectNode payload = pt.putObject("payload");
            payload.put("repo_ref", p.repoRef());
            payload.put("branch", p.branch());
            payload.put("content_hash", p.contentHash());
            payload.put("rel_path", p.relPath() != null ? p.relPath() : "");
            payload.put("chunk_text", p.chunkPreview() != null ? p.chunkPreview() : "");
        }
        HttpRequest req = baseRequest(url)
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(root)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Qdrant upsert HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 400));
        }
    }

    public List<SearchHit> search(
            String collection, float[] queryVector, String repoRef, String branch, int limit) throws Exception {
        String url = baseUrl + "/collections/" + enc(collection) + "/points/search";
        ObjectNode body = JSON.createObjectNode();
        ArrayNode vec = body.putArray("vector");
        for (float v : queryVector) {
            vec.add(v);
        }
        body.put("limit", limit);
        body.put("with_payload", true);
        ObjectNode filter = body.putObject("filter");
        ArrayNode must = filter.putArray("must");
        ObjectNode m1 = must.addObject();
        m1.putObject("key").put("repo_ref", "repo_ref");
        m1.remove("key");
        must.removeAll();
        // Qdrant filter format: { "must": [ {"key": "repo_ref", "match": {"value": "x"}} ] }
        ObjectNode f1 = JSON.createObjectNode();
        f1.put("key", "repo_ref");
        f1.set("match", JSON.createObjectNode().put("value", repoRef));
        ObjectNode f2 = JSON.createObjectNode();
        f2.put("key", "branch");
        f2.set("match", JSON.createObjectNode().put("value", branch));
        ArrayNode mustArr = filter.putArray("must");
        mustArr.add(f1);
        mustArr.add(f2);

        HttpRequest req = baseRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Qdrant search HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 400));
        }
        JsonNode root = JSON.readTree(resp.body());
        JsonNode result = root.path("result");
        List<SearchHit> hits = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode h : result) {
                JsonNode payload = h.path("payload");
                String text = payload.path("chunk_preview").asText("");
                if (text.isBlank()) {
                    text = payload.path("chunk_text").asText("");
                }
                String rel = payload.path("rel_path").asText("");
                float score = (float) h.path("score").asDouble(0);
                hits.add(new SearchHit(rel, text, score));
            }
        }
        return hits;
    }

    private HttpRequest.Builder baseRequest(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs));
        if (!apiKey.isBlank()) {
            b.header("api-key", apiKey);
        }
        b.header("Content-Type", "application/json");
        return b;
    }

    private static String enc(String collection) {
        return java.net.URLEncoder.encode(collection, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String trimSlash(String u) {
        String s = u.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    public record QdrantPoint(
            UUID id,
            float[] vector,
            String repoRef,
            String branch,
            String contentHash,
            String relPath,
            String chunkPreview) {}

    public record SearchHit(String relPath, String snippet, float score) {}
}
