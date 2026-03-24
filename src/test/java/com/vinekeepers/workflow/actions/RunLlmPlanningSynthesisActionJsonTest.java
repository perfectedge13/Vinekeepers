package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningFailureCategory;
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

class RunLlmPlanningSynthesisActionJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> stringResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void extractJsonObject_stripsFence() {
        String raw = "```json\n{\"upserts\":[],\"question_if_needed\":\"\",\"top_unresolved_gap\":\"\",\"recommended_action\":\"CONTINUE_SYNTHESIS\",\"explicit_assumptions\":[]}\n```";
        String out = RunLlmPlanningSynthesisAction.extractJsonObject(raw);
        assertTrue(out.contains("\"upserts\""));
        assertTrue(out.startsWith("{"));
    }

    @Test
    void extractJsonObject_embeddedInText() {
        String raw = "Here you go: {\"a\":1} thanks";
        String out = RunLlmPlanningSynthesisAction.extractJsonObject(raw);
        assertEquals("{\"a\":1}", out);
    }

    @Test
    void extractJsonObject_ignoresBracesInsideStrings() {
        String raw = "prefix {\"message\":\"Use {braces} literally\",\"upserts\":[]} trailing";
        String out = RunLlmPlanningSynthesisAction.extractJsonObject(raw);
        assertEquals("{\"message\":\"Use {braces} literally\",\"upserts\":[]}", out);
    }

    @Test
    void run_repairsMalformedJsonBeforeApplyingUpserts() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> malformed = assistantResponse(
                "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"mode\":\"replace\",\"data\":{\"current_state_summary\":\"Existing planning uses one shared model\"}} \"question_if_needed\":\"\",\"top_unresolved_gap\":\"\",\"recommended_action\":\"CONTINUE_SYNTHESIS\",\"explicit_assumptions\":[]}");
        HttpResponse<String> repaired = assistantResponse(
                "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"mode\":\"replace\",\"data\":{\"current_state_summary\":\"Existing planning uses one shared model\"}}],\"question_if_needed\":\"\",\"top_unresolved_gap\":\"\",\"recommended_action\":\"CONTINUE_SYNTHESIS\",\"explicit_assumptions\":[]}");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(malformed).thenReturn(repaired);
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
                                "ctx-1",
                                "channelId",
                                "room-1",
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "Improve planner"),
                        Map.of("profileId", "software_feature_planning_v2")));

        var action = new RunLlmPlanningSynthesisAction(client, planStore, registry);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(null, Map.of("contextId", "ctx-1"), Map.of());

        assertEquals("true", spread.get("planningLlmOk"));
        assertEquals("", spread.get("planningLlmError"));
        assertEquals("1", spread.get("planningLlmUpsertCount"));

        SectionState sec = planStore.getByContextId("ctx-1")
                .orElseThrow()
                .getArtifacts()
                .get("requirements_spec")
                .getSectionsById()
                .get("narrative");
        assertEquals("Existing planning uses one shared model", sec.getValues().get("current_state_summary"));
    }

    @Test
    void run_failsWhenUpsertsUsePlaceholderFieldKeys() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> invalid = assistantResponse(
                "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"mode\":\"replace\",\"data\":{\"fieldId\":\"Wrong placeholder\"}}],\"question_if_needed\":\"\",\"top_unresolved_gap\":\"\",\"recommended_action\":\"CONTINUE_SYNTHESIS\",\"explicit_assumptions\":[]}");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(invalid);
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
                                "ctx-2",
                                "channelId",
                                "room-2",
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "Improve planner"),
                        Map.of("profileId", "software_feature_planning_v2")));

        var action = new RunLlmPlanningSynthesisAction(client, planStore, registry);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(null, Map.of("contextId", "ctx-2"), Map.of());

        assertEquals("false", spread.get("planningLlmOk"));
        assertEquals("0", spread.get("planningLlmUpsertCount"));
        assertTrue(String.valueOf(spread.get("planningLlmError")).contains("fieldId"));
    }

    @Test
    void run_failsWhenUpsertsUseFieldIdAsSectionId() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> invalid = assistantResponse(
                "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"feature_summary\",\"mode\":\"replace\",\"data\":{\"current_state_summary\":\"Wrong section\"}}],\"question_if_needed\":\"\",\"top_unresolved_gap\":\"\",\"recommended_action\":\"CONTINUE_SYNTHESIS\",\"explicit_assumptions\":[]}");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(invalid);
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
                                "ctx-bad-section",
                                "channelId",
                                "room-bad-section",
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "Improve planner"),
                        Map.of("profileId", "software_feature_planning_v2")));

        var action = new RunLlmPlanningSynthesisAction(client, planStore, registry);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(null, Map.of("contextId", "ctx-bad-section"), Map.of());

        assertEquals("false", spread.get("planningLlmOk"));
        assertEquals("0", spread.get("planningLlmUpsertCount"));
        assertEquals(PlanningFailureCategory.SYNTHESIS_UPSERT_REJECTED.name(), spread.get("planningSynthesisFailureCategory"));
        assertTrue(String.valueOf(spread.get("planningLlmError")).contains("does not match this planning profile"));
    }

    @Test
    void run_marksRepairExhaustedWhenJsonRepairReturnsError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> invalid = assistantResponse("this is not json {");
        HttpResponse<String> repairError = assistantResponse("ERROR:upstream");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(invalid).thenReturn(repairError);
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
                                "ctx-repair-ex",
                                "channelId",
                                "room-repair",
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "x"),
                        Map.of("profileId", "software_feature_planning_v2")));

        var action = new RunLlmPlanningSynthesisAction(client, planStore, registry);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(null, Map.of("contextId", "ctx-repair-ex"), Map.of());

        assertEquals("true", spread.get("planningSynthesisRepairExhausted"));
        assertEquals(PlanningFailureCategory.SYNTHESIS_REPAIR_EXHAUSTED.name(), spread.get("planningSynthesisFailureCategory"));
        assertEquals("false", spread.get("planningSynthesisParseOk"));
    }

    @Test
    void run_classifiesTransportFailureSeparately() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> transportError = assistantResponse("ERROR: upstream timeout");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(transportError);
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
                                "ctx-transport",
                                "channelId",
                                "room-transport",
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "Improve planner"),
                        Map.of("profileId", "software_feature_planning_v2")));

        var action = new RunLlmPlanningSynthesisAction(client, planStore, registry);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(null, Map.of("contextId", "ctx-transport"), Map.of());

        assertEquals(PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name(), spread.get("planningSynthesisFailureCategory"));
        assertEquals("ERROR: upstream timeout", spread.get("planningLlmError"));
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
