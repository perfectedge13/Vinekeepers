package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.workflow.actions.AssertPlanningRoutePhaseAction;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.workflow.actions.PlanningRouteInvariantFailedAction;
import com.vinekeepers.workflow.v2.WorkflowRulesEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanningEvaluationPhaseHandoffIntegrationTest {

    @Test
    void packetReadyEvaluationSetsPacketPhaseForGraphBranch() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> evaluationResponse = assistantResponse(
                """
                {
                  "confidence": { "score": 78, "level": "medium", "summary": "Coherent plan with conservative assumptions." },
                  "gaps": [
                    {
                      "id": "missing_implementation_scope",
                      "kind": "MISSING_IMPLEMENTATION_SCOPE",
                      "description": "Concrete repo paths not yet inspected; proceeding with stated assumptions.",
                      "blocking": false,
                      "askable": false,
                      "assumable": true
                    }
                  ],
                  "ask_user_required": false,
                  "best_question": { "text": "", "rationale": "" },
                  "assumptions_to_add": [],
                  "issues_to_add": [],
                  "risks_to_add": [],
                  "decisions_to_add": [],
                  "ready_for_packet": true
                }
                """);
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(evaluationResponse);
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");

        TestContext ctx = createContext("ctx-packet-phase-handoff", client);
        Map<String, Object> state = baseEvalState(ctx.plan().getContextId());
        Map<String, Object> spread = ctx.pipeline().runEvaluationOnly(null, state, Map.of());

        assertEquals("READY_FOR_PACKET", spread.get(PlanningRoutingBridge.NEXT_ACTION_KEY));
        assertEquals(
                PlanningRoutingBridge.PHASE_PLANNING_PACKET,
                spread.get(PlanningRoutingBridge.NEXT_PHASE_KEY));

        Optional<String> transition =
                WorkflowRulesEngine.firstMatchingTransition(spread, rsEvaluationBranchRules());
        assertEquals(Optional.of("planning_packet"), transition);
    }

    @Test
    void staleBlockedMarkersDoNotPreventPacketReadyEvaluation() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> evaluationResponse = assistantResponse(
                """
                {
                  "confidence": { "score": 78, "level": "medium", "summary": "ok" },
                  "gaps": [
                    {
                      "id": "g1",
                      "kind": "WEAK_VALIDATION",
                      "description": "non-blocking",
                      "blocking": false,
                      "askable": false,
                      "assumable": true
                    }
                  ],
                  "ask_user_required": false,
                  "best_question": { "text": "", "rationale": "" },
                  "assumptions_to_add": [],
                  "issues_to_add": [],
                  "risks_to_add": [],
                  "decisions_to_add": [],
                  "ready_for_packet": true
                }
                """);
        when(http.send(any(HttpRequest.class), anyBodyHandler())).thenReturn(evaluationResponse);
        OpenAiChatClient client =
                new OpenAiChatClient(http, "https://api.openai.com/v1", "sk-test-key", "gpt-4o-mini");

        TestContext ctx = createContext("ctx-stale-blocked-handoff", client);
        Map<String, Object> state = baseEvalState(ctx.plan().getContextId());
        state.put(PlanningRoutingBridge.NEXT_ACTION_KEY, "BLOCKED");
        state.put(PlanningRoutingBridge.NEXT_PHASE_KEY, PlanningRoutingBridge.PHASE_PLANNING_BLOCKED);
        state.put("planningCanonicalNextAction", "BLOCK");
        state.put(PlanningCanonicalDecisionSupport.CANONICAL_STAGE_KEY, "FAILED");
        state.put("planningBlockingReason", "stale blocked reason from prior pass");
        state.put("planningPacketPostingAllowed", "false");

        Map<String, Object> spread = ctx.pipeline().runEvaluationOnly(null, state, Map.of());

        assertEquals("READY_FOR_PACKET", spread.get(PlanningRoutingBridge.NEXT_ACTION_KEY));
        assertEquals(
                PlanningRoutingBridge.PHASE_PLANNING_PACKET,
                spread.get(PlanningRoutingBridge.NEXT_PHASE_KEY));
        assertEquals(Optional.of("planning_packet"), WorkflowRulesEngine.firstMatchingTransition(spread, rsEvaluationBranchRules()));
    }

    @Test
    void missingPlanningNextPhaseDoesNotMatchEvaluationBranchRules() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(PlanningRoutingBridge.NEXT_ACTION_KEY, "READY_FOR_PACKET");
        state.put(PlanningRoutingBridge.NEXT_PHASE_KEY, "");
        assertTrue(WorkflowRulesEngine.firstMatchingTransition(state, rsEvaluationBranchRules()).isEmpty());
    }

    @Test
    void assertPacketPhaseThrowsWhenActionDisagrees() {
        AssertPlanningRoutePhaseAction action = new AssertPlanningRoutePhaseAction();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(PlanningRoutingBridge.NEXT_PHASE_KEY, PlanningRoutingBridge.PHASE_PLANNING_PACKET);
        state.put(PlanningRoutingBridge.NEXT_ACTION_KEY, "BLOCKED");
        Map<String, Object> bind = Map.of(AssertPlanningRoutePhaseAction.BIND_EXPECTED_PHASE, "planning_packet");
        assertThrows(IllegalStateException.class, () -> action.run(new Event("t", "k", Map.of()), state, bind));
    }

    @Test
    void planningRouteInvariantFailedActionSetsCycleErrorWithoutBlockedTemplate() {
        PlanningRouteInvariantFailedAction action = new PlanningRouteInvariantFailedAction();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(PlanningRoutingBridge.NEXT_PHASE_KEY, "");
        state.put(PlanningRoutingBridge.NEXT_ACTION_KEY, "READY_FOR_PACKET");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) action.run(new Event("t", "k", Map.of()), state, Map.of());
        assertEquals(PlanningRouteInvariantFailedAction.MACHINE_CODE, spread.get("planningRoomCycleError"));
        assertEquals("true", spread.get("planningRouteInvariantTriggered"));
    }

    private static Map<String, Object> baseEvalState(String contextId) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contextId", contextId);
        state.put(PlanningCyclePipeline.PARTIAL_DEPTH_OK_KEY, "true");
        state.put(PlanningCyclePipeline.PARTIAL_DEPTH_REASON_KEY, "");
        state.put(PlanningCyclePipeline.PARTIAL_LAST_ROLE_SUMMARY_KEY, "silent synthesis complete");
        state.put(PlanningCyclePipeline.PARTIAL_LAST_SYNTH_KEY, "evaluation next");
        return state;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rsEvaluationBranchRules() {
        return List.of(
                Map.of(
                        "when",
                        Map.of("equals", Map.of("key", "planningNextPhase", "value", "planning_clarification")),
                        "then",
                        List.of(Map.of("transition", "planning_clarification"))),
                Map.of(
                        "when",
                        Map.of("equals", Map.of("key", "planningNextPhase", "value", "planning_packet")),
                        "then",
                        List.of(Map.of("transition", "planning_packet"))),
                Map.of(
                        "when",
                        Map.of("equals", Map.of("key", "planningNextPhase", "value", "planning_blocked")),
                        "then",
                        List.of(Map.of("transition", "planning_blocked"))));
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
                                "initialRequest", "Packet-ready handoff test"),
                        Map.of("profileId", "software_feature_planning_v2")));
        FeaturePlanState plan = planStore.getByContextId(contextId).orElseThrow();
        WorkProfileDefinition profile = registry.get("software_feature_planning_v2").orElseThrow();
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(openAiChatClient, planStore, registry);
        return new TestContext(pipeline, plan, profile, planStore);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> assistantResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                """
                {"choices":[{"message":{"content":%s}}]}
                """
                        .formatted(new ObjectMapper().valueToTree(body).toString()));
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
            FeaturePlanStateStore planStore) {}
}
