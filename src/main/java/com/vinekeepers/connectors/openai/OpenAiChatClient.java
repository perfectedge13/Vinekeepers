package com.vinekeepers.connectors.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal OpenAI-compatible chat-completions client (JSON in / out). Used for optional planning synthesis.
 */
public final class OpenAiChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiChatClient() {
        this(defaultHttpClient(), defaultBaseUrl(), Env.get("OPENAI_API_KEY", ""), Env.get("OPENAI_PLANNING_MODEL", "gpt-4o-mini"));
    }

    public OpenAiChatClient(HttpClient httpClient, String baseUrl, String apiKey, String model) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUrl = trimSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model != null && !model.isBlank() ? model : "gpt-4o-mini";
    }

    private static HttpClient defaultHttpClient() {
        long connectMs = parseLongMs(Env.get("OPENAI_HTTP_CONNECT_TIMEOUT_MS", "15000"), 15000);
        long requestMs = parseLongMs(Env.get("OPENAI_HTTP_REQUEST_TIMEOUT_MS", "120000"), 120000);
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();
    }

    private static String defaultBaseUrl() {
        String preferred = Env.get("OPENAI_BASE_URL", "");
        if (preferred != null && !preferred.isBlank()) {
            return trimSlash(preferred);
        }
        return trimSlash(Env.get("OPENAI_API_BASE_URL", "https://api.openai.com/v1"));
    }

    private static long parseLongMs(String raw, long dflt) {
        if (raw == null || raw.isBlank()) {
            return dflt;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static String trimSlash(String u) {
        if (u == null || u.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String s = u.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @return assistant message content or error prefix {@code ERROR: ...}
     */
    public String complete(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage, null, null);
    }

    /**
     * @param modelOverride   when non-blank, replaces default model for this call
     * @param timeoutMsOverride when non-null and positive, HTTP timeout for this call
     * @return assistant message content or error prefix {@code ERROR: ...}
     */
    public String complete(String systemPrompt, String userMessage, String modelOverride, Long timeoutMsOverride) {
        if (!isConfigured()) {
            return "ERROR: OPENAI_API_KEY not set.";
        }
        try {
            long requestMs = timeoutMsOverride != null && timeoutMsOverride > 0
                    ? timeoutMsOverride
                    : planningRequestTimeoutMs();
            String useModel = modelOverride != null && !modelOverride.isBlank() ? modelOverride.trim() : model;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", useModel);
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userMessage));
            body.put("messages", messages);
            body.put("temperature", 0.2);
            String jsonBody = JSON.writeValueAsString(body);
            URI uri = URI.create(baseUrl + "/chat/completions");
            HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(requestMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("OpenAI HTTP {}: {}", resp.statusCode(), truncate(resp.body(), 500));
                return "ERROR: HTTP " + resp.statusCode();
            }
            JsonNode root = JSON.readTree(resp.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "ERROR: empty choices";
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return "ERROR: blank assistant content";
            }
            return content;
        } catch (Exception e) {
            log.warn("OpenAI request failed: {} — {}", e.getClass().getName(), formatExceptionChain(e));
            String detail =
                    e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
            return "ERROR: " + detail;
        }
    }

    /** Planning calls prefer {@code OPENAI_PLANNING_TIMEOUT_MS}, then {@code OPENAI_HTTP_REQUEST_TIMEOUT_MS}. */
    public static long planningRequestTimeoutMs() {
        long planning = parseLongMs(Env.get("OPENAI_PLANNING_TIMEOUT_MS", ""), -1);
        if (planning > 0) {
            return planning;
        }
        return parseLongMs(Env.get("OPENAI_HTTP_REQUEST_TIMEOUT_MS", "120000"), 120000);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private static String formatExceptionChain(Throwable t) {
        if (t == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 5; c = c.getCause(), depth++) {
            if (depth > 0) {
                sb.append(" | caused by: ");
            }
            sb.append(c.getClass().getSimpleName());
            if (c.getMessage() != null && !c.getMessage().isBlank()) {
                sb.append(": ").append(truncate(c.getMessage(), 200));
            }
        }
        return sb.toString();
    }
}
