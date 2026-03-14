package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExtractEventFieldsStepTest {

    @Test
    void extractFromPayloadKeyStoresInState() {
        Event event = new Event("test", "message", Map.of("repo", "owner/proj"));
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "payload.repo", "storeIn", "project")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        assertEquals("owner/proj", state.get("project"));
    }

    @Test
    void extractFromContextFieldStoresInState() {
        Event event = new Event("discord:g:ch-1", "message",
                Map.of("channelId", "ch-1", "content", "hello world"));
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "context.channelId", "storeIn", "channel"),
                Map.of("from", "context.text", "storeIn", "text")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        assertEquals("ch-1", state.get("channel"));
        assertEquals("hello world", state.get("text"));
    }

    @Test
    void missingPathUsesDefaultWhenProvided() {
        Event event = new Event("test", "message", Map.of());
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "payload.missing", "storeIn", "value", "default", "fallback")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        assertEquals("fallback", state.get("value"));
    }

    @Test
    void missingPathNoDefaultSkipsPut() {
        Event event = new Event("test", "message", Map.of());
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "payload.nonexistent", "storeIn", "out")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        assertNull(state.get("out"));
    }

    @Test
    void transformsAppliedToResolvedValue() {
        Event event = new Event("test", "message", Map.of("name", "  Mixed CASE  "));
        List<Map<String, Object>> fromEvent = List.of(
                Map.<String, Object>of("from", "payload.name", "storeIn", "normalized",
                        "transforms", List.of("trim", "lower"))
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        assertEquals("mixed case", state.get("normalized"));
    }

    @Test
    void multipleMappingsAllApplied() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "content", "hi", "authorId", "u42"));
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "context.channelId", "storeIn", "chan"),
                Map.of("from", "context.text", "storeIn", "msg"),
                Map.of("from", "context.actorId", "storeIn", "actor")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        assertEquals("ch-1", state.get("chan"));
        assertEquals("hi", state.get("msg"));
        assertEquals("u42", state.get("actor"));
    }

    @Test
    void listValuedContextStoredAsList() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("mentions", List.of("luna", "bot")));
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "context.mentions", "storeIn", "mentioned")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        Object mentioned = state.get("mentioned");
        assertInstanceOf(List.class, mentioned);
        assertEquals(List.of("luna", "bot"), mentioned);
    }

    @Test
    void invalidFromPathSkipsMapping() {
        Event event = new Event("test", "message", Map.of("key", "v"));
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "unknown.path", "storeIn", "x")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        step.execute(event, state, 0);

        assertNull(state.get("x"));
    }

    @Test
    void advanceToNextStep() {
        List<Map<String, Object>> fromEvent = List.of(
                Map.of("from", "payload.x", "storeIn", "x")
        );
        ExtractEventFieldsStep step = new ExtractEventFieldsStep(fromEvent);
        Event event = new Event("test", "message", Map.of("x", "1"));
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();

        StepResult result = step.execute(event, state, 2);

        assertEquals(3, result.getNextStepIndex());
    }
}
