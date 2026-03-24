package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanningCyclePipelineFailureHandlingTest {

    @Test
    void applyImmediateSynthesisFailure_blankCategoryFallsBackToCycleError() throws Exception {
        TestContext ctx = createContext("ctx-immediate");
        SilentPlanningSynthesisService synthesis =
                new SilentPlanningSynthesisService(null, ctx.planStore(), ctx.registry());
        Method m =
                SilentPlanningSynthesisService.class.getDeclaredMethod(
                        "applyImmediateSynthesisFailure", String.class, Map.class, Map.class);
        m.setAccessible(true);

        Map<String, Object> spread = new LinkedHashMap<>();
        Map<String, Object> synthSpread = new LinkedHashMap<>();
        synthSpread.put("planningLlmError", "Upsert requirements_spec/feature_summary does not match this planning profile.");
        synthSpread.put("planningLlmUpsertCount", "0");

        assertDoesNotThrow(() -> m.invoke(synthesis, ctx.plan().getContextId(), spread, synthSpread));
        assertEquals("SYNTHESIS_UPSERTS_NOT_APPLIED", spread.get("planningRoomCycleError"));
    }

    @Test
    void applyImmediateSynthesisFailure_usesExplicitSynthesisCategoryWhenPresent() throws Exception {
        TestContext ctx = createContext("ctx-immediate-category");
        SilentPlanningSynthesisService synthesis =
                new SilentPlanningSynthesisService(null, ctx.planStore(), ctx.registry());
        Method m =
                SilentPlanningSynthesisService.class.getDeclaredMethod(
                        "applyImmediateSynthesisFailure", String.class, Map.class, Map.class);
        m.setAccessible(true);

        Map<String, Object> spread = new LinkedHashMap<>();
        Map<String, Object> synthSpread = new LinkedHashMap<>();
        synthSpread.put("planningLlmError", "ERROR: upstream timeout");
        synthSpread.put("planningLlmUpsertCount", "0");
        synthSpread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name());

        assertDoesNotThrow(() -> m.invoke(synthesis, ctx.plan().getContextId(), spread, synthSpread));
        assertEquals(PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name(), spread.get("planningSynthesisFailureCategory"));
        assertEquals(PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name(), spread.get("planningRoomCycleError"));
    }

    @Test
    void runEvaluationOnly_failClosesOnInvalidEvaluation() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> evaluationResponse = assistantResponse(
                """
                {
                  "confidence": { "score": 44, "level": "medium", "summary": "Invalid ask result." },
                  "gaps": [
                    { "id": "gap_one", "kind": "WEAK_VALIDATION", "description": "Validation is thin.", "blocking": false, "askable": false, "assumable": true }
                  ],
                  "ask_user_required": true,
                  "best_question": { "text": "Who approves this rollout?", "rationale": "Should fail closed." },
                  "assumptions_to_add": [],
                  "issues_to_add": [],
                  "risks_to_add": [],
                  "decisions_to_add": [],
                  "ready_for_packet": false
                }
                """);
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(evaluationResponse);
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");
        TestContext ctx = createContext("ctx-invalid-eval", client);

        Map<String, Object> spread =
                ctx.pipeline().runEvaluationOnly(
                        null,
                        Map.of(
                                "contextId", ctx.plan().getContextId(),
                                PlanningCyclePipeline.PARTIAL_DEPTH_OK_KEY, "true",
                                PlanningCyclePipeline.PARTIAL_DEPTH_REASON_KEY, "",
                                PlanningCyclePipeline.PARTIAL_LAST_ROLE_SUMMARY_KEY, "silent synthesis complete",
                                PlanningCyclePipeline.PARTIAL_LAST_SYNTH_KEY, "evaluation next"),
                        Map.of());

        assertEquals("EVALUATION_INVALID_NO_ELIGIBLE_ASK_GAP", spread.get("planningRoomCycleError"));
        assertEquals("false", spread.get("planningPacketPostingAllowed"));
        assertEquals("BLOCK", spread.get("planningCanonicalNextAction"));
        assertFalse("true".equals(spread.get("planningCanonicalUserInputRequired")));
        assertFalse(spread.containsKey("planningUserInputRequired"));
    }

    private static TestContext createContext(String contextId) {
        return createContext(contextId, null);
    }

    private static TestContext createContext(String contextId, OpenAiChatClient openAiChatClient) {
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        WorkProfileRegistry registry = TestWorkProfiles.loadFromRepoConfig();
        InitializeFeaturePlanStateAction init =
                new InitializeFeaturePlanStateAction(planStore, new FeatureRoomStateStore(), registry);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of(
                                "contextId", contextId,
                                "channelId", "room-" + contextId,
                                "repoRef", "perfectedge13/Vinekeepers",
                                "initialRequest", "Improve planner stability"),
                        Map.of("profileId", "software_feature_planning_v2")));
        FeaturePlanState plan = planStore.getByContextId(contextId).orElseThrow();
        WorkProfileDefinition profile = registry.get("software_feature_planning_v2").orElseThrow();
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(openAiChatClient, planStore, registry);
        return new TestContext(pipeline, plan, profile, planStore, registry);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> assistantResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"choices":[{"message":{"content":%s}}]}
                """.formatted(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body).toString()));
        return response;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return (HttpResponse.BodyHandler) any(HttpResponse.BodyHandler.class);
    }

    private record TestContext(
            PlanningCyclePipeline pipeline,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            FeaturePlanStateStore planStore,
            WorkProfileRegistry registry) {}
}
