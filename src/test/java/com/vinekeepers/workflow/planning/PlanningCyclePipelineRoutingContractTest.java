package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.workflow.actions.PostPlanningPacketThreadAction;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanningCyclePipelineRoutingContractTest {

    @Test
    void evaluationSnapshotOverridesStalePacketAndClarificationSignals() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> evaluationResponse = assistantResponse(
                """
                {
                  "confidence": { "score": 41, "level": "medium", "summary": "Blocked on a real gap." },
                  "gaps": [
                    {
                      "id": "missing_authority",
                      "kind": "MISSING_AUTHORITY",
                      "description": "Need an explicit approver for this rollout.",
                      "blocking": true,
                      "askable": false,
                      "assumable": false
                    }
                  ],
                  "ask_user_required": false,
                  "best_question": { "text": "", "rationale": "" },
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

        TestContext ctx = createContext("ctx-routing-contract", client);
        Map<String, Object> staleState = new LinkedHashMap<>();
        staleState.put("contextId", ctx.plan().getContextId());
        staleState.put("channelId", "thread-routing-contract");
        staleState.put(PlanningCyclePipeline.PARTIAL_DEPTH_OK_KEY, "true");
        staleState.put(PlanningCyclePipeline.PARTIAL_DEPTH_REASON_KEY, "");
        staleState.put(PlanningCyclePipeline.PARTIAL_LAST_ROLE_SUMMARY_KEY, "legacy synthesis said ask user");
        staleState.put(PlanningCyclePipeline.PARTIAL_LAST_SYNTH_KEY, "legacy synthesis said packet next");
        staleState.put("planningNextAction", "READY_FOR_PACKET");
        staleState.put("planningPacketPostingAllowed", "true");
        staleState.put("planningClarificationQuestionText", "Stale question should be cleared.");
        staleState.put("planningCanonicalUserInputRequired", "true");
        staleState.put("planningCanonicalNextAction", "READY_FOR_PACKET");

        Map<String, Object> spread = ctx.pipeline().runEvaluationOnly(null, staleState, Map.of());

        assertEquals("BLOCKED", spread.get("planningNextAction"));
        assertEquals(
                PlanningRoutingBridge.PHASE_PLANNING_BLOCKED,
                spread.get(PlanningRoutingBridge.NEXT_PHASE_KEY));
        assertEquals("false", spread.get("planningPacketPostingAllowed"));
        assertEquals("", spread.get("planningNextQuestion"));
        assertEquals("", spread.get("planningClarificationQuestionText"));
        assertEquals("false", spread.get("planningCanonicalUserInputRequired"));

        List<String> sent = new ArrayList<>();
        FeatureRoomStateStore roomStore = new FeatureRoomStateStore();
        roomStore.put(
                new FeatureRoomState(
                        ctx.plan().getContextId(),
                        "f1",
                        "slug",
                        "thread-routing-contract",
                        "thread-routing-contract",
                        "org/r",
                        "Improve planner stability",
                        "ACTIVE",
                        List.of(new RoomParticipant(PlanningRole.ORCHESTRATOR, "orch", "ri", "O", true)),
                        "u",
                        Instant.now()));
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore(), roomStore);
        router.registerSender("orch", (ch, m, body) -> sent.add(body), null);
        PostPlanningPacketThreadAction action = new PostPlanningPacketThreadAction(router, ctx.planStore());

        @SuppressWarnings("unchecked")
        Map<String, Object> packetSpread =
                (Map<String, Object>) action.run(new Event("t", "k", Map.of()), merge(staleState, spread), Map.of());

        assertEquals("false", packetSpread.get("planningPacketPosted"));
        assertTrue(String.valueOf(packetSpread.get("planningPacketPostError")).contains("does not allow packet posting"));
        assertEquals(0, sent.size(), "pre-packet promise copy must not render when planningNextAction is not READY_FOR_PACKET");
    }

    private static Map<String, Object> merge(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new LinkedHashMap<>(left);
        merged.putAll(right);
        return merged;
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
        return new TestContext(pipeline, plan, profile, planStore);
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
            FeaturePlanStateStore planStore) {}
}
