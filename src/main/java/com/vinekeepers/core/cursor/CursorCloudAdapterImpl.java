package com.vinekeepers.core.cursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official Cursor Cloud Agents API adapter implementation.
 */
public final class CursorCloudAdapterImpl implements CursorCloudAdapter {

    private static final Logger log = LoggerFactory.getLogger(CursorCloudAdapterImpl.class);

    private final CursorCloudTransport transport;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;

    public CursorCloudAdapterImpl() {
        this(
                new HttpCursorCloudTransport(HttpClient.newHttpClient()),
                new ObjectMapper(),
                Env.get("CURSOR_API_KEY", ""),
                Env.get("CURSOR_API_BASE_URL", "https://api.cursor.com"),
                Env.get("CURSOR_MODEL", "")
        );
    }

    CursorCloudAdapterImpl(CursorCloudTransport transport, ObjectMapper objectMapper,
                           String apiKey, String baseUrl, String defaultModel) {
        this.transport = transport;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.defaultModel = defaultModel != null ? defaultModel.trim() : "";
        if (log.isDebugEnabled()) {
            log.debug("Cursor API adapter configured: baseUrl={}, key={}", this.baseUrl, maskKey(this.apiKey));
        }
    }

    @Override
    public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
        requireApiKey();
        if (request == null) {
            throw new CursorCloudException("Cursor launch request is required.");
        }
        if (isBlank(request.promptText())) {
            throw new CursorCloudException("Cursor launch prompt is required.");
        }
        if (isBlank(request.repositoryUrl())) {
            throw new CursorCloudException("Cursor repository URL is required.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", Map.of("text", request.promptText()));
        if (!isBlank(resolveModel(request.model()))) {
            payload.put("model", resolveModel(request.model()));
        }
        payload.put("source", Map.of(
                "repository", request.repositoryUrl(),
                "ref", defaultIfBlank(request.baseRef(), "main")
        ));
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("autoCreatePr", request.autoCreatePr());
        if (!isBlank(request.branchName())) {
            target.put("branchName", request.branchName());
        }
        payload.put("target", target);
        JsonNode root = send("POST", "/v0/agents", payload);
        return new CursorAgentLaunchResult(
                text(root, "id"),
                text(root, "name"),
                text(root, "status"),
                text(root.path("source"), "repository"),
                text(root.path("source"), "ref"),
                text(root.path("target"), "branchName"),
                text(root.path("target"), "url"),
                text(root.path("target"), "prUrl"),
                root.path("target").path("autoCreatePr").asBoolean(false),
                instant(root, "createdAt")
        );
    }

    @Override
    public CursorAgentDetails getAgent(String agentId) {
        requireApiKey();
        JsonNode root = send("GET", "/v0/agents/" + encode(agentId), null);
        return new CursorAgentDetails(
                text(root, "id"),
                text(root, "name"),
                text(root, "status"),
                text(root.path("source"), "repository"),
                text(root.path("source"), "ref"),
                text(root.path("target"), "branchName"),
                text(root.path("target"), "url"),
                text(root.path("target"), "prUrl"),
                text(root, "summary"),
                instant(root, "createdAt")
        );
    }

    @Override
    public CursorAgentConversation getConversation(String agentId) {
        requireApiKey();
        JsonNode root = send("GET", "/v0/agents/" + encode(agentId) + "/conversation", null);
        List<CursorAgentMessage> messages = new ArrayList<>();
        if (root.path("messages").isArray()) {
            for (JsonNode item : root.path("messages")) {
                messages.add(new CursorAgentMessage(
                        text(item, "id"),
                        text(item, "type"),
                        text(item, "text")
                ));
            }
        }
        return new CursorAgentConversation(text(root, "id"), messages);
    }

    @Override
    public void addFollowup(String agentId, String promptText) {
        requireApiKey();
        if (isBlank(promptText)) {
            throw new CursorCloudException("Cursor follow-up prompt is required.");
        }
        send("POST", "/v0/agents/" + encode(agentId) + "/followup",
                Map.of("prompt", Map.of("text", promptText)));
    }

    private JsonNode send(String method, String path, Object body) {
        String requestBody = "";
        if (body != null) {
            try {
                requestBody = objectMapper.writeValueAsString(body);
            } catch (IOException e) {
                throw new CursorCloudException("Could not serialize Cursor API request.", e);
            }
        }
        URI uri = URI.create(baseUrl + path);
        CursorCloudTransportResponse response;
        try {
            response = transport.exchange(method, uri, apiKey, requestBody);
        } catch (CursorCloudException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cursor API transport exception: uri={}, exception={}, message={}",
                    uri, e.getClass().getName(), e.getMessage(), e);
            throw new CursorCloudException("Cursor API request failed: " + e.getMessage(), e);
        }
        String responseBody = response.body() != null ? response.body() : "";
        try {
            JsonNode root = responseBody.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(responseBody);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return root;
            }
            JsonNode errorNode = root.path("error");
            String message = text(errorNode, "message");
            String code = text(errorNode, "code");
            log.warn("Cursor API non-2xx: statusCode={}, errorCode={}, errorMessage={}",
                    response.statusCode(), code, message);
            throw new CursorCloudException(
                    !isBlank(message) ? message : "Cursor API request failed.",
                    code,
                    response.statusCode(),
                    null
            );
        } catch (IOException e) {
            throw new CursorCloudException("Could not parse Cursor API response.", e);
        }
    }

    private void requireApiKey() {
        if (isBlank(apiKey)) {
            throw new CursorCloudException("CURSOR_API_KEY is not configured.");
        }
    }

    private String resolveModel(String explicitModel) {
        return !isBlank(explicitModel) ? explicitModel : defaultModel;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static String normalizeBaseUrl(String value) {
        String raw = !isBlank(value) ? value.trim() : "https://api.cursor.com";
        if (raw.endsWith("/")) {
            return raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text != null && !text.isBlank() ? text : null;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (isBlank(value)) {
            return null;
        }
        return Instant.parse(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(defaultIfBlank(value, ""), StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return "empty";
        }
        if (key.length() <= 4) {
            return "***";
        }
        return key.substring(0, 4) + "***";
    }

    private static final class HttpCursorCloudTransport implements CursorCloudTransport {

        private final HttpClient httpClient;

        private HttpCursorCloudTransport(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public CursorCloudTransportResponse exchange(String method, URI uri, String bearerToken, String body) throws Exception {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .header("Authorization", "Bearer " + bearerToken)
                        .header("Accept", "application/json");
                if ("POST".equalsIgnoreCase(method)) {
                    builder.header("Content-Type", "application/json");
                    builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                } else {
                    builder.GET();
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                return new CursorCloudTransportResponse(response.statusCode(), response.body());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Cursor API transport exception: uri={}, exception={}, message={}",
                        uri, e.getClass().getName(), e.getMessage(), e);
                throw new CursorCloudException("Cursor API request failed: " + e.getMessage(), e);
            }
        }
    }
}
