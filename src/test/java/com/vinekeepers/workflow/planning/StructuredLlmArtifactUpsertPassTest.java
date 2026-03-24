package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
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

class StructuredLlmArtifactUpsertPassTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> stringResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void execute_repairsMalformedJsonBeforeApplyingUpserts() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> malformed = assistantResponse(
                "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"mode\":\"replace\",\"data\":{\"feature_summary\":\"Recovered after repair\"}} \"implementation_scope_notes\":\"\",\"top_unresolved_gap\":\"\",\"draft_question_candidate\":\"\",\"explicit_assumptions\":[],\"repo_evidence_this_pass\":\"not_inspected\"}");
        HttpResponse<String> repaired = assistantResponse(
                "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"mode\":\"replace\",\"data\":{\"feature_summary\":\"Recovered after repair\"}}],\"implementation_scope_notes\":\"\",\"top_unresolved_gap\":\"\",\"draft_question_candidate\":\"\",\"explicit_assumptions\":[],\"repo_evidence_this_pass\":\"not_inspected\"}");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(malformed).thenReturn(repaired);
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");

        TestContext ctx = createContext("ctx-structured-repair");
        PlanningRolePassRunner.RolePassResult result = StructuredLlmArtifactUpsertPass.execute(
                client,
                ctx.plan(),
                ctx.profile(),
                null,
                Map.of("contextId", ctx.plan().getContextId()),
                ctx.planStore(),
                ctx.registry(),
                "COORDINATOR",
                "Reply with JSON only.",
                "Update the draft.");

        assertEquals("", result.error());
        assertEquals(1, result.upsertsApplied());
        assertEquals(
                "Recovered after repair",
                ctx.planStore()
                        .getByContextId(ctx.plan().getContextId())
                        .orElseThrow()
                        .getArtifacts()
                        .get("requirements_spec")
                        .getSectionsById()
                        .get("narrative")
                        .getValues()
                        .get("feature_summary"));
    }

    @Test
    void execute_returnsSchemaErrorWhenStructuredUpsertsUsePlaceholderFieldId() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> invalid = assistantResponse(
                "{\"upserts\":[{\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"mode\":\"replace\",\"data\":{\"fieldId\":\"Wrong placeholder\"}}],\"implementation_scope_notes\":\"\",\"top_unresolved_gap\":\"\",\"draft_question_candidate\":\"\",\"explicit_assumptions\":[],\"repo_evidence_this_pass\":\"not_inspected\"}");
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(invalid);
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");

        TestContext ctx = createContext("ctx-structured-invalid");
        PlanningRolePassRunner.RolePassResult result = StructuredLlmArtifactUpsertPass.execute(
                client,
                ctx.plan(),
                ctx.profile(),
                null,
                Map.of("contextId", ctx.plan().getContextId()),
                ctx.planStore(),
                ctx.registry(),
                "COORDINATOR",
                "Reply with JSON only.",
                "Update the draft.");

        assertEquals(0, result.upsertsApplied());
        assertTrue(result.error().contains("fieldId"));
    }

    private static TestContext createContext(String contextId) {
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        var registry = TestWorkProfiles.loadFromRepoConfig();
        var init = new InitializeFeaturePlanStateAction(planStore, new FeatureRoomStateStore(), registry);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of(
                                "contextId",
                                contextId,
                                "channelId",
                                "room-" + contextId,
                                "repoRef",
                                "perfectedge13/Vinekeepers",
                                "initialRequest",
                                "Improve planner"),
                        Map.of("profileId", "software_feature_planning_v2")));
        FeaturePlanState plan = planStore.getByContextId(contextId).orElseThrow();
        return new TestContext(planStore, registry, plan);
    }

    private static HttpResponse<String> assistantResponse(String content) throws Exception {
        return stringResponse(
                200,
                JSON.writeValueAsString(Map.of("choices", new Object[] {Map.of("message", Map.of("content", content))})));
    }

    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return any();
    }

    private record TestContext(
            FeaturePlanStateStore planStore,
            com.vinekeepers.profile.WorkProfileRegistry registry,
            FeaturePlanState plan) {
        private com.vinekeepers.profile.WorkProfileDefinition profile() {
            return registry.get("software_feature_planning_v2").orElseThrow();
        }
    }
}
