package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPlanningProgressIfChangedActionTest {

    @Test
    void skipsSendWhenNotWorthy() {
        List<String> sent = new ArrayList<>();
        PostChannelMessageAction inner =
                new PostChannelMessageAction((channelId, messageId, content) -> sent.add(content));
        PostPlanningProgressIfChangedAction action = new PostPlanningProgressIfChangedAction(inner);

        Event event = new Event("e", "discord", Map.of());
        Map<String, Object> state =
                Map.of(
                        "channelId",
                        "ch1",
                        "planningProgressPostWorthy",
                        "false",
                        "planningCycleProgressSummary",
                        "x");
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) action.run(event, state, Map.of("content", "ignored"));
        assertEquals("true", result.get("planningProgressPostSkipped"));
        assertTrue(sent.isEmpty());
    }

    @Test
    void postsAndRecordsHashWhenWorthy() {
        List<String> sent = new ArrayList<>();
        PostChannelMessageAction inner =
                new PostChannelMessageAction((channelId, messageId, content) -> sent.add(content));
        PostPlanningProgressIfChangedAction action = new PostPlanningProgressIfChangedAction(inner);

        Event event = new Event("e", "discord", Map.of());
        Map<String, Object> state =
                Map.of(
                        "channelId",
                        "ch1",
                        "planningProgressPostWorthy",
                        "true",
                        "planningProgressPostFingerprint",
                        "abc123hash",
                        "greeting",
                        "hi");
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>)
                        action.run(event, state, Map.of("content", "Hello {{greeting}}"));
        assertEquals("false", result.get("planningProgressPostSkipped"));
        assertEquals("abc123hash", result.get("planningLastProgressPostHash"));
        assertEquals(1, sent.size());
        assertEquals("Hello hi", sent.get(0));
    }
}
