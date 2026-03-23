package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.workflow.planning.PlanningContentGenerator;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunRequestExpansionLlmActionJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> stringResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void run_repairsMalformedJsonBeforeApplyingExpansionFields() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> repaired = assistantResponse(
                "{\"current_state\":\"Existing planning uses one shared model\",\"upserts\":[],\"question_if_needed\":\"\"}");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(repaired);
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");

        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        var registry = TestWorkProfiles.loadFromRepoConfig();
        var init = new InitializeFeaturePlanStateAction(planStore, new FeatureRoomStateStore(), registry);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of(
                                "contextId",
                                "ctx-expansion-repair",
                                "channelId",
                                "room-expansion-repair",
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "Improve planner"),
                        Map.of("profileId", "software_feature_planning_v2")));

        PlanningContentGenerator generator =
                (c, system, user, model, timeout, callCtx) ->
                        "{\"current_state\":\"Existing planning uses one shared model\",\"upserts\":[] \"question_if_needed\":\"\"}";
        var action = new RunRequestExpansionLlmAction(generator, client, planStore, registry);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(null, Map.of("contextId", "ctx-expansion-repair"), Map.of());

        assertEquals("true", spread.get("planningLlmOk"));
        assertEquals("true", spread.get("planningExpansionRepairAttempted"));
        assertEquals("false", spread.get("planningExpansionFallbackUsed"));
        SectionState sec = planStore.getByContextId("ctx-expansion-repair")
                .orElseThrow()
                .getArtifacts()
                .get("requirements_spec")
                .getSectionsById()
                .get("narrative");
        assertEquals("Existing planning uses one shared model", sec.getValues().get("current_state_summary"));
    }

    @Test
    void run_fallsBackWhenExpansionUpsertsUsePlaceholderFieldKeys() throws Exception {
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        var registry = TestWorkProfiles.loadFromRepoConfig();
        var init = new InitializeFeaturePlanStateAction(planStore, new FeatureRoomStateStore(), registry);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of(
                                "contextId",
                                "ctx-expansion-invalid",
                                "channelId",
                                "room-expansion-invalid",
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "Improve planner"),
                        Map.of("profileId", "software_feature_planning_v2")));

        HttpClient http = mock(HttpClient.class);
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");
        PlanningContentGenerator generator =
                (c, system, user, model, timeout, callCtx) ->
                        "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"mode\":\"replace\",\"data\":{\"fieldId\":\"Wrong placeholder\"}}],\"question_if_needed\":\"\"}";
        var action = new RunRequestExpansionLlmAction(generator, client, planStore, registry);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(null, Map.of("contextId", "ctx-expansion-invalid"), Map.of());

        assertEquals("false", spread.get("planningLlmOk"));
        assertEquals("true", spread.get("planningExpansionFallbackUsed"));
        assertEquals("1", spread.get("planningExpansionUpsertsAttempted"));
        assertEquals("1", spread.get("planningExpansionUpsertsRejected"));
        assertTrue(String.valueOf(spread.get("planningLlmError")).contains("fieldId"));
    }

    private static HttpResponse<String> assistantResponse(String content) throws Exception {
        return stringResponse(
                200,
                JSON.writeValueAsString(Map.of("choices", new Object[] {Map.of("message", Map.of("content", content))})));
    }

    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return any();
    }
}
