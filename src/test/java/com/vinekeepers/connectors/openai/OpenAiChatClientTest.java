package com.vinekeepers.connectors.openai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiChatClientTest {

    @Test
    void completeReturnsNonBlankErrorWhenExceptionMessageIsNull() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException());

        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");
        String out = client.complete("system", "user");
        assertTrue(out.startsWith("ERROR:"), out);
        assertTrue(out.length() > "ERROR:".length(), out);
    }

    @Test
    void completeRestoresInterruptAndReturnsInterruptedError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("cancelled"));

        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");
        String out = client.complete("system", "user");
        assertEquals("ERROR: interrupted", out);
        assertTrue(Thread.interrupted());
    }
}
