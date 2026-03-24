package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanningCyclePipelineCanonicalClarificationTest {

    @Test
    void canonicalProjectionUsesCanonicalGapMergePath() {
        CoordinatorClarificationSettings coord =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(new CoordinatorClarificationGapRule("missing_scope", true, "", List.of(), List.of(), List.of())));
        WorkProfileDefinition profile =
                new WorkProfileDefinition(
                        "p",
                        "",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        List.of(),
                        coord);
        CanonicalPlanningGap gap = CanonicalPlanningGap.fromEvaluation(
                "missing_scope",
                "MISSING_IMPLEMENTATION_SCOPE",
                "What implementation scope is in bounds for this feature?",
                true,
                true,
                false,
                "requirements_spec/narrative/scope_summary",
                List.of());

        ClarificationProjection ranked =
                CanonicalClarificationSpreadBuilder.projectCanonicalPlanningGap(
                        UnresolvedItemLedger.empty(),
                        profile,
                        coord,
                        gap,
                        "What implementation scope is in bounds for this feature?",
                        List.of(),
                        QuestionMode.OPEN);

        assertTrue(ranked.userInputRequired());
        assertEquals("missing_scope", ranked.canonicalGapId());
        assertTrue(ranked.metaJson().contains("requirements_spec/narrative/scope_summary"));
    }

    @Test
    void canonicalProjectionSkipsStructuredChoicesWithoutGapBoundedUiOptIn() {
        CoordinatorClarificationSettings coord =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "g_or",
                                        false,
                                        "Use option A or option B for timeouts?",
                                        List.of("timeout"),
                                        List.of(),
                                        List.of())));
        WorkProfileDefinition profile =
                new WorkProfileDefinition(
                        "p",
                        "",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        List.of(),
                        coord);
        CanonicalPlanningGap gap = CanonicalPlanningGap.fromEvaluation(
                "g_or",
                "BRANCHING_DECISION",
                "Use option A or option B for timeouts?",
                false,
                true,
                false,
                "requirements_spec/narrative/scope_summary",
                List.of());

        ClarificationProjection ranked =
                CanonicalClarificationSpreadBuilder.projectCanonicalPlanningGap(
                        UnresolvedItemLedger.empty(),
                        profile,
                        coord,
                        gap,
                        "Use option A or option B for timeouts?",
                        List.of(),
                        QuestionMode.OPEN);
        assertFalse(ranked.useStructuredChoices());
        assertTrue(ranked.userInputRequired());
    }

    @Test
    void silentSynthesisSkipsFullRescanAfterMergedClarification() {
        WorkProfileRegistry registry = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanState plan = bareFeaturePlanV2();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan);
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(null, store, registry);
        Map<String, Object> spread =
                pipeline.runSilentSynthesisOnly(
                        null,
                        Map.of(
                                "contextId", "c",
                                "planningJustMergedClarification", "true"),
                        Map.of());
        assertTrue("true".equals(spread.get("planningSelectiveRerunActive")));
        assertTrue(String.valueOf(spread.get("planningSelectiveRerunNote")).contains("skipping a full re-scan"));
    }

    @Test
    void evaluationOnlyProjectsSingleCanonicalAsk() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> evaluationResponse = assistantResponse(
                """
                {
                  "confidence": { "score": 41, "level": "medium", "summary": "One authority question remains." },
                  "gaps": [
                    { "id": "missing_authority", "kind": "MISSING_AUTHORITY", "description": "Need rollout approver.", "blocking": true, "askable": true, "assumable": false, "merge_target_path": "requirements_spec/narrative/scope_summary" }
                  ],
                  "ask_user_required": true,
                  "best_question": { "text": "Who approves the rollout boundary for this feature?", "rationale": "This unlocks packet readiness." },
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
        TestContext ctx = createContext("ctx-eval-success", client);

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

        assertEquals("ASK_USER", spread.get("planningNextAction"));
        assertEquals("ASK_USER", spread.get("planningCanonicalNextAction"));
        assertEquals("true", spread.get("planningCanonicalUserInputRequired"));
        assertEquals("Who approves the rollout boundary for this feature?", spread.get("planningClarificationQuestionText"));
        assertTrue(String.valueOf(spread.get("planningClarificationMetaJson")).contains("missing_authority"));
        assertFalse(spread.containsKey("planningUserInputRequired"));
    }

    private static TestContext createContext(String contextId, OpenAiChatClient client) {
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
                                "initialRequest", "Improve planning evaluation"),
                        Map.of("profileId", "software_feature_planning_v2")));
        return new TestContext(
                new PlanningCyclePipeline(client, planStore, registry),
                planStore.getByContextId(contextId).orElseThrow());
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

    private record TestContext(PlanningCyclePipeline pipeline, FeaturePlanState plan) {}

    private static FeaturePlanState bareFeaturePlanV2() {
        return new FeaturePlanState(
                "c",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "Short request",
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "software_feature_planning_v2",
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
