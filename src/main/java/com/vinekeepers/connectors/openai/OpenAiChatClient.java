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
    private final OpenAiPlanningProgressSink progressSink;

    public OpenAiChatClient() {
        this(
                defaultHttpClient(),
                defaultBaseUrl(),
                Env.get("OPENAI_API_KEY", ""),
                Env.get("OPENAI_PLANNING_MODEL", "gpt-4o-mini"),
                OpenAiPlanningProgressSink.NOOP);
    }

    public OpenAiChatClient(OpenAiPlanningProgressSink progressSink) {
        this(
                defaultHttpClient(),
                defaultBaseUrl(),
                Env.get("OPENAI_API_KEY", ""),
                Env.get("OPENAI_PLANNING_MODEL", "gpt-4o-mini"),
                progressSink);
    }

    public OpenAiChatClient(HttpClient httpClient, String baseUrl, String apiKey, String model) {
        this(httpClient, baseUrl, apiKey, model, OpenAiPlanningProgressSink.NOOP);
    }

    public OpenAiChatClient(
            HttpClient httpClient, String baseUrl, String apiKey, String model, OpenAiPlanningProgressSink progressSink) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUrl = trimSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model != null && !model.isBlank() ? model : "gpt-4o-mini";
        this.progressSink = progressSink != null ? progressSink : OpenAiPlanningProgressSink.NOOP;
    }

    private static HttpClient defaultHttpClient() {
        long connectMs = parseLongMs(Env.get("OPENAI_HTTP_CONNECT_TIMEOUT_MS", "15000"), 15000);
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
        return complete(systemPrompt, userMessage, null, null, null);
    }

    /**
     * @param modelOverride   when non-blank, replaces default model for this call
     * @param timeoutMsOverride when non-null and positive, HTTP timeout for this call
     * @return assistant message content or error prefix {@code ERROR: ...}
     */
    public String complete(String systemPrompt, String userMessage, String modelOverride, Long timeoutMsOverride) {
        return complete(systemPrompt, userMessage, modelOverride, timeoutMsOverride, null);
    }

    /**
     * @param callContext when non-null and {@link OpenAiCallContext#activitySummary()} is non-blank, notifies
     *                    {@link OpenAiPlanningProgressSink} and applies server logging policy for this exchange
     * @return assistant message content or error prefix {@code ERROR: ...}
     */
    public String complete(
            String systemPrompt, String userMessage, String modelOverride, Long timeoutMsOverride, OpenAiCallContext callContext) {
        if (!isConfigured()) {
            return "ERROR: OPENAI_API_KEY not set.";
        }
        notifyProgressIfNeeded(callContext);
        long startNs = System.nanoTime();
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
                if (callContext != null) {
                    logPlanningBodies(
                            activityLabel(callContext),
                            systemPrompt,
                            userMessage,
                            null,
                            "HTTP " + resp.statusCode());
                }
                return "ERROR: HTTP " + resp.statusCode();
            }
            JsonNode root = JSON.readTree(resp.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                if (callContext != null) {
                    logPlanningBodies(activityLabel(callContext), systemPrompt, userMessage, null, "empty choices");
                }
                return "ERROR: empty choices";
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                if (callContext != null) {
                    logPlanningBodies(
                            activityLabel(callContext), systemPrompt, userMessage, null, "blank assistant content");
                }
                return "ERROR: blank assistant content";
            }
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            if (callContext != null) {
                log.info(
                        "OpenAI planning call finished activity=\"{}\" durationMs={} model={}",
                        activityLabel(callContext),
                        durationMs,
                        useModel);
                logPlanningBodies(activityLabel(callContext), systemPrompt, userMessage, content, null);
            } else {
                log.debug("OpenAI complete ok durationMs={} model={}", durationMs, useModel);
            }
            return content;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            log.warn(
                    "OpenAI request interrupted durationMs={} thread={}",
                    durationMs,
                    Thread.currentThread().getName());
            if (callContext != null) {
                logPlanningBodies(activityLabel(callContext), systemPrompt, userMessage, null, "interrupted");
            }
            return "ERROR: interrupted";
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            log.warn(
                    "OpenAI request failed after {}ms: {} — {}",
                    durationMs,
                    e.getClass().getName(),
                    formatExceptionChain(e));
            String detail =
                    e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
            if (callContext != null) {
                logPlanningBodies(activityLabel(callContext), systemPrompt, userMessage, null, detail);
            }
            return "ERROR: " + detail;
        }
    }

    private void notifyProgressIfNeeded(OpenAiCallContext ctx) {
        if (ctx == null) {
            return;
        }
        String line = ctx.activitySummary();
        if (line == null || line.isBlank()) {
            return;
        }
        try {
            progressSink.publish(ctx.event(), ctx.workflowState(), line);
        } catch (Exception e) {
            log.debug("OpenAI progress notification skipped: {}", e.getMessage());
        }
    }

    private static String activityLabel(OpenAiCallContext ctx) {
        if (ctx == null || ctx.activitySummary() == null || ctx.activitySummary().isBlank()) {
            return "openai";
        }
        return truncateOneLine(ctx.activitySummary(), 120);
    }

    private static void logPlanningBodies(
            String activity, String systemPrompt, String userMessage, String assistantText, String failureHint) {
        if (!logPlanningBodiesEnabled()) {
            if (failureHint != null && !failureHint.isBlank()) {
                log.info("OpenAI planning call activity=\"{}\" outcome={}", activity, truncateOneLine(failureHint, 200));
            }
            return;
        }
        int max = logBodyMaxChars();
        log.info(
                "OpenAI planning activity=\"{}\" systemPrompt(truncated)={}",
                activity,
                truncate(redactSecrets(systemPrompt != null ? systemPrompt : ""), max));
        log.info(
                "OpenAI planning activity=\"{}\" userMessage(truncated)={}",
                activity,
                truncate(redactSecrets(userMessage != null ? userMessage : ""), max));
        if (assistantText != null && !assistantText.isBlank()) {
            log.info(
                    "OpenAI planning activity=\"{}\" assistantReply(truncated)={}",
                    activity,
                    truncate(redactSecrets(assistantText), max));
        } else if (failureHint != null && !failureHint.isBlank()) {
            log.info(
                    "OpenAI planning activity=\"{}\" assistantReply= failure={}",
                    activity,
                    truncateOneLine(redactSecrets(failureHint), max));
        }
    }

    private static boolean logPlanningBodiesEnabled() {
        String v = Env.get("OPENAI_LOG_PLANNING_BODIES", "true");
        return v == null || !"false".equalsIgnoreCase(v.trim());
    }

    private static int logBodyMaxChars() {
        return (int) Math.min(32_768L, Math.max(256L, parseLongMs(Env.get("OPENAI_LOG_BODY_MAX_CHARS", "4096"), 4096)));
    }

    private static String redactSecrets(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replaceAll("(?i)(sk-[a-z0-9]{8})[a-z0-9]+", "$1…");
    }

    private static String truncateOneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", " ").replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
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
