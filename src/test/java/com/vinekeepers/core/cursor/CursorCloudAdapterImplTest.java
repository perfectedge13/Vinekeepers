package com.vinekeepers.core.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("systemProperties")
class CursorCloudAdapterImplTest {

    @AfterEach
    void clearEnv() {
        System.clearProperty("CURSOR_API_KEY");
        System.clearProperty("CURSOR_API_BASE_URL");
    }

    @Test
    void launchAgentBuildsExpectedRequest() {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<URI> uri = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        CursorCloudTransport transport = (requestMethod, requestUri, bearerToken, requestBody) -> {
            method.set(requestMethod);
            uri.set(requestUri);
            token.set(bearerToken);
            body.set(requestBody);
            return new CursorCloudTransportResponse(201, """
                    {
                      "id": "bc_123",
                      "name": "Luna feature",
                      "status": "CREATING",
                      "source": { "repository": "https://github.com/acme/vinekeepers", "ref": "main" },
                      "target": { "branchName": "luna/add-tests", "url": "https://cursor.com/agents?id=bc_123", "autoCreatePr": true },
                      "createdAt": "2026-03-07T20:00:00Z"
                    }
                    """);
        };
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                transport, new ObjectMapper(), "test-key", "https://api.cursor.com", "gpt-5.2");

        CursorAgentLaunchResult result = impl.launchAgent(new CursorAgentLaunchRequest(
                "Ship it",
                "https://github.com/acme/vinekeepers",
                "main",
                "luna/add-tests",
                true,
                ""
        ));

        assertEquals("POST", method.get());
        assertEquals("https://api.cursor.com/v0/agents", uri.get().toString());
        assertEquals("test-key", token.get());
        assertTrue(body.get().contains("\"repository\":\"https://github.com/acme/vinekeepers\""));
        assertTrue(body.get().contains("\"branchName\":\"luna/add-tests\""));
        assertEquals("bc_123", result.id());
        assertEquals("CREATING", result.status());
    }

    @Test
    void getAgentParsesSummaryAndPrUrl() {
        CursorCloudTransport transport = (requestMethod, requestUri, bearerToken, requestBody) ->
                new CursorCloudTransportResponse(200, """
                        {
                          "id": "bc_123",
                          "name": "Luna feature",
                          "status": "FINISHED",
                          "summary": "Added the requested feature",
                          "source": { "repository": "https://github.com/acme/vinekeepers", "ref": "main" },
                          "target": { "branchName": "luna/add-tests", "url": "https://cursor.com/agents?id=bc_123", "prUrl": "https://github.com/acme/vinekeepers/pull/10" },
                          "createdAt": "2026-03-07T20:00:00Z"
                        }
                        """);
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                transport, new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorAgentDetails result = impl.getAgent("bc_123");

        assertEquals("FINISHED", result.status());
        assertEquals("https://github.com/acme/vinekeepers/pull/10", result.prUrl());
        assertEquals("Added the requested feature", result.summary());
    }

    @Test
    void getConversationReturnsAssistantMessages() {
        CursorCloudTransport transport = (requestMethod, requestUri, bearerToken, requestBody) ->
                new CursorCloudTransportResponse(200, """
                        {
                          "id": "bc_123",
                          "messages": [
                            { "id": "msg_1", "type": "user_message", "text": "Build it" },
                            { "id": "msg_2", "type": "assistant_message", "text": "Working on it" }
                          ]
                        }
                        """);
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                transport, new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorAgentConversation conversation = impl.getConversation("bc_123");

        assertEquals("bc_123", conversation.id());
        assertEquals(2, conversation.messages().size());
        assertEquals("msg_2", conversation.latestAssistantMessage().orElseThrow().id());
    }

    @Test
    void launchAgentWithoutApiKeyFailsFast() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (requestMethod, requestUri, bearerToken, requestBody) -> new CursorCloudTransportResponse(200, "{}"),
                new ObjectMapper(),
                "",
                "https://api.cursor.com",
                ""
        );

        CursorCloudException error = assertThrows(CursorCloudException.class, () ->
                impl.launchAgent(new CursorAgentLaunchRequest("Ship it", "https://github.com/acme/vinekeepers", "main", "branch", true, "")));

        assertEquals("CURSOR_API_KEY is not configured.", error.getMessage());
    }

    @Test
    void apiErrorsSurfaceCursorMessage() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (requestMethod, requestUri, bearerToken, requestBody) -> new CursorCloudTransportResponse(403, """
                        { "error": { "message": "plan limits exceeded", "code": "forbidden" } }
                        """),
                new ObjectMapper(),
                "test-key",
                "https://api.cursor.com",
                ""
        );

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertEquals("Cursor API request failed: plan limits exceeded", error.getMessage());
        assertEquals("forbidden", error.getCode());
        assertEquals(403, error.getStatusCode());
    }

    @Test
    void transportExceptionSurfacesMessage() {
        String transportMessage = "Connection refused";
        IOException cause = new IOException(transportMessage);
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (requestMethod, requestUri, bearerToken, requestBody) -> {
                    throw cause;
                },
                new ObjectMapper(),
                "test-key",
                "https://api.cursor.com",
                ""
        );

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertTrue(error.getMessage().contains(transportMessage), "getMessage() should contain cause message");
        assertEquals(cause, error.getCause());
        assertTrue(error.getCause() instanceof IOException);
        assertEquals(transportMessage, error.getCause().getMessage());
    }

    /** Verifies transport receives the configured API key (used as Bearer token in Authorization header). */
    @Test
    void getAgentSendsConfiguredApiKeyAsBearerTokenToTransport() {
        AtomicReference<String> capturedToken = new AtomicReference<>();
        CursorCloudTransport transport = (requestMethod, requestUri, bearerToken, requestBody) -> {
            capturedToken.set(bearerToken);
            return new CursorCloudTransportResponse(200, "{\"id\":\"bc_123\",\"status\":\"FINISHED\",\"source\":{},\"target\":{},\"createdAt\":\"2026-03-07T20:00:00Z\"}");
        };
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                transport, new ObjectMapper(), "sk-abc123", "https://api.cursor.com", "");

        impl.getAgent("bc_123");

        assertEquals("sk-abc123", capturedToken.get(), "Transport must receive raw API key for Bearer auth");
    }

    @Test
    void non2xxErrorAsStringSurfacesInException() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (m, u, t, b) -> new CursorCloudTransportResponse(500, """
                        { "error": "Internal server error" }
                        """),
                new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertEquals("Cursor API request failed: Internal server error", error.getMessage());
        assertEquals(500, error.getStatusCode());
    }

    @Test
    void non2xxTopLevelMessageSurfacesInException() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (m, u, t, b) -> new CursorCloudTransportResponse(401, """
                        { "message": "Unauthorized" }
                        """),
                new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertEquals("Cursor API request failed: Unauthorized", error.getMessage());
        assertEquals(401, error.getStatusCode());
    }

    @Test
    void non2xxPlainTextBodySurfacesInException() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (m, u, t, b) -> new CursorCloudTransportResponse(502, "Bad Gateway"),
                new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertEquals("Cursor API request failed: Bad Gateway", error.getMessage());
        assertEquals(502, error.getStatusCode());
    }

    @Test
    void non2xxEmptyBodySurfacesGenericMessage() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (m, u, t, b) -> new CursorCloudTransportResponse(504, ""),
                new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertTrue(error.getMessage().startsWith("Cursor API request failed"), "Empty body should yield generic message");
        assertTrue(error.getMessage().contains("504"), "Message should include status for empty body");
        assertEquals(504, error.getStatusCode());
    }

    @Test
    void transportReceivesBearerTokenAsConfiguredKey() {
        AtomicReference<String> capturedToken = new AtomicReference<>();
        CursorCloudTransport transport = (requestMethod, requestUri, bearerToken, requestBody) -> {
            capturedToken.set(bearerToken);
            return new CursorCloudTransportResponse(201, "{\"id\":\"a1\",\"name\":\"x\",\"status\":\"CREATING\",\"source\":{},\"target\":{},\"createdAt\":\"2026-03-07T20:00:00Z\"}");
        };
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                transport, new ObjectMapper(), "sk-bearer-key-42", "https://api.cursor.com", "");

        impl.launchAgent(new CursorAgentLaunchRequest(
                "Run", "https://github.com/org/repo", "main", "feature", true, ""));

        assertEquals("sk-bearer-key-42", capturedToken.get(), "Transport must receive configured API key for Bearer header");
    }

    @Test
    void non2xxEmptyJsonObjectSurfacesGenericMessage() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (m, u, t, b) -> new CursorCloudTransportResponse(503, "{}"),
                new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertTrue(error.getMessage().startsWith("Cursor API request failed"), "Empty JSON body should yield generic message");
        assertTrue(error.getMessage().contains("503"), "Message should include status for empty JSON object");
        assertEquals(503, error.getStatusCode());
    }

    @Test
    void non2xxNonJsonBodySurfacesInException() {
        String body = "<html><body>Service Unavailable</body></html>";
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl(
                (m, u, t, b) -> new CursorCloudTransportResponse(503, body),
                new ObjectMapper(), "test-key", "https://api.cursor.com", "");

        CursorCloudException error = assertThrows(CursorCloudException.class, () -> impl.getAgent("bc_123"));

        assertTrue(error.getMessage().contains("Cursor API request failed"));
        assertTrue(error.getMessage().contains("Service Unavailable") || error.getMessage().contains(body),
                "Non-JSON body content should surface in exception message");
        assertEquals(503, error.getStatusCode());
    }
}
