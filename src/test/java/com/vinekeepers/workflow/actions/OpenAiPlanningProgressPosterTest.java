package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiPlanningProgressPosterTest {

    @AfterEach
    void clearDiscordProgressProperty() {
        System.clearProperty("OPENAI_PLANNING_DISCORD_PROGRESS");
    }

    @Test
    void publishPostsUpdateWrappedLineWhenEnabled() {
        PostChannelMessageAction post = mock(PostChannelMessageAction.class);
        when(post.run(any(), any(), any())).thenReturn("OK");

        Event event = new Event("s", "k", Map.of());
        Map<String, Object> state = Map.of("channelId", "c1");

        new OpenAiPlanningProgressPoster(post).publish(event, state, "  Asking the model  ");

        verify(post).run(eq(event), eq(state), eq(Map.of("content", "**Update:** Asking the model")));
    }

    @Test
    void publishNoOpWhenDiscordProgressDisabled() {
        System.setProperty("OPENAI_PLANNING_DISCORD_PROGRESS", "false");
        PostChannelMessageAction post = mock(PostChannelMessageAction.class);

        Event event = new Event("s", "k", Map.of());
        new OpenAiPlanningProgressPoster(post).publish(event, Map.of(), "hello");

        verify(post, never()).run(any(), any(), any());
    }

    @Test
    void publishNoOpWhenLineBlankOrArgsNull() {
        PostChannelMessageAction post = mock(PostChannelMessageAction.class);
        OpenAiPlanningProgressPoster poster = new OpenAiPlanningProgressPoster(post);
        Event event = new Event("s", "k", Map.of());
        Map<String, Object> state = Map.of();

        poster.publish(null, state, "x");
        poster.publish(event, null, "x");
        poster.publish(event, state, null);
        poster.publish(event, state, "   ");

        verify(post, never()).run(any(), any(), any());
    }

    @Test
    void publishSwallowsPostExceptions() {
        PostChannelMessageAction post = mock(PostChannelMessageAction.class);
        when(post.run(any(), any(), any())).thenThrow(new RuntimeException("network"));

        Event event = new Event("s", "k", Map.of());
        assertDoesNotThrow(() -> new OpenAiPlanningProgressPoster(post).publish(event, Map.of("channelId", "c"), "msg"));
    }
}
