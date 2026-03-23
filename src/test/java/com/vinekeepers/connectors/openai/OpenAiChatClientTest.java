package com.vinekeepers.connectors.openai;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiChatClientTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> stringResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void completeNotifiesProgressSinkWhenCallContextHasActivitySummary() throws Exception {
        List<String> lines = new ArrayList<>();
        OpenAiPlanningProgressSink sink = (e, ws, line) -> lines.add(line);

        HttpResponse<String> successResp = stringResponse(200, "{\"choices\":[{\"message\":{\"content\":\"assistant-out\"}}]}");

        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(successResp);

        Event event = new Event("src", "kind", Map.of());
        OpenAiCallContext ctx = OpenAiCallContext.planning(event, Map.of("channelId", "ch1"), "Synthesizing the plan draft");
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini", sink);

        String out = client.complete("system", "user", null, null, ctx);
        assertEquals("assistant-out", out);
        assertEquals(List.of("Synthesizing the plan draft"), lines);
    }

    @Test
    void completeNotifiesProgressSinkOnceBeforeHttpFailure() throws Exception {
        AtomicInteger publishes = new AtomicInteger();
        OpenAiPlanningProgressSink sink = (e, ws, line) -> publishes.incrementAndGet();

        HttpResponse<String> errResp = stringResponse(500, "boom");

        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(errResp);

        Event event = new Event("src", "kind", Map.of());
        OpenAiCallContext ctx = OpenAiCallContext.planning(event, Map.of(), "Calling OpenAI");
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini", sink);

        String out = client.complete("system", "user", null, null, ctx);
        assertTrue(out.startsWith("ERROR: HTTP"), out);
        assertEquals(1, publishes.get());
    }

    @Test
    void completeSkipsProgressSinkWhenActivitySummaryBlank() throws Exception {
        AtomicInteger publishes = new AtomicInteger();
        OpenAiPlanningProgressSink sink = (e, ws, line) -> publishes.incrementAndGet();

        HttpResponse<String> successResp = stringResponse(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(successResp);

        Event event = new Event("src", "kind", Map.of());
        OpenAiCallContext ctx = OpenAiCallContext.planning(event, Map.of(), "  ");
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini", sink);

        assertEquals("ok", client.complete("system", "user", null, null, ctx));
        assertEquals(0, publishes.get());
    }

    @Test
    void completeReturnsNonBlankErrorWhenExceptionMessageIsNull() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenThrow(new IOException());

        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");
        String out = client.complete("system", "user");
        assertTrue(out.startsWith("ERROR:"), out);
        assertTrue(out.length() > "ERROR:".length(), out);
    }

    @Test
    void completeRestoresInterruptAndReturnsInterruptedError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), anyBodyHandler()))
                .thenThrow(new InterruptedException("cancelled"));

        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");
        String out = client.complete("system", "user");
        assertEquals("ERROR: interrupted", out);
        assertTrue(Thread.interrupted());
    }

    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return any();
    }
}
