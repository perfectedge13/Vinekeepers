package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.AssumptionEntry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.PlanningRole;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.RolePassResult;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One planning-room cycle: exploration seed, architect/auditor/scribe LLM passes (when configured),
 * deterministic draft expansion, synthesis LLM, pre-critique fill, depth check with inner bot retries,
 * question ranking with safe defaults, and spread keys for YAML branching.
 */
public final class ExecutePlanningRoomCycleAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final int MAX_BOT_INNER_ROUNDS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenAiChatClient openAiChatClient;
    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public ExecutePlanningRoomCycleAction(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        if (planStateStore == null || workProfileRegistry == null) {
            spread.put("planningRoomCycleError", "MISSING_DEPS");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningRoomCycleError", "NO_CONTEXT");
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningRoomCycleError", "NO_PLAN");
            return spread;
        }
        String profileId = plan.getProfileId();
        WorkProfileDefinition profile = profileId != null && !profileId.isBlank()
                ? workProfileRegistry.get(profileId).orElse(null)
                : null;
        if (profile == null || profile.findSection("request_exploration", "analysis").isEmpty()) {
            spread.put("planningRoomCycleError", "PROFILE_NOT_V2");
            return spread;
        }

        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0);
        cycleIteration++;
        spread.put("planningRoomCycleIteration", String.valueOf(cycleIteration));

        spread.put("planningPhase", "REQUEST_EXPANSION");
        new BuildRequestExplorationAction(planStateStore, workProfileRegistry).run(event, state, bind);

        List<String> aggregatedFollowUps = new ArrayList<>();
        String depthReason = "";
        boolean depthOk = false;

        for (int inner = 0; inner < MAX_BOT_INNER_ROUNDS; inner++) {
            spread.put("planningPhase", "DRAFTING");
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.ARCHITECT, plan, profile, event, state, spread, aggregatedFollowUps);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.AUDITOR, plan, profile, event, state, spread, aggregatedFollowUps);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.SCRIBE, plan, profile, event, state, spread, aggregatedFollowUps);

            new ExpandPlanningDraftsAction(planStateStore, workProfileRegistry).run(event, state, bind);

            Object synthObj =
                    new RunLlmPlanningSynthesisAction(openAiChatClient, planStateStore, workProfileRegistry)
                            .run(event, state, bind);
            @SuppressWarnings("unchecked")
            Map<String, Object> synthSpread =
                    synthObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            mergeSynthFollowUps(synthSpread, aggregatedFollowUps);

            new SynthesizePreCritiqueArtifactsAction(planStateStore, workProfileRegistry).run(event, state, bind);

            plan = planStateStore.getByContextId(contextId).orElse(plan);
            PlanningPacketDepthEvaluator.DepthResult dr = PlanningPacketDepthEvaluator.evaluate(plan);
            depthOk = dr.ok();
            depthReason = dr.reason() != null ? dr.reason() : "";
            spread.put("planningPacketDepthOk", depthOk ? "true" : "false");
            spread.put("planningPacketDepthReason", depthReason);
            if (depthOk) {
                break;
            }
        }

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        RankedClarification ranked = PlanningQuestionRankingPolicy.rank(plan, aggregatedFollowUps, 3);
        for (String assumption : ranked.assumptionsToRecord()) {
            plan = appendAssumption(plan, assumption);
        }
        planStateStore.update(plan);

        spread.put("planningQuestionsAskedThisRound", ranked.userInputRequired() ? "1" : "0");
        spread.put("planningBlockingQuestionCount", String.valueOf(ranked.blockingQuestionCount()));
        spread.put("planningUserInputRequired", ranked.userInputRequired() ? "true" : "false");
        spread.put("planningClarificationChoicesJson", ranked.choicesJson());
        spread.put("planningClarificationMetaJson", ranked.metaJson());
        spread.put("planningOrchestratorRoundSummary", buildOrchestratorSummary(plan, depthOk, depthReason, ranked, cycleIteration));
        spread.put(
                "planningRevisionNeeded",
                (!depthOk || ranked.userInputRequired()) ? "true" : "false");

        boolean readyToPost = depthOk && !ranked.userInputRequired();
        spread.put("planningReadyToPostPacket", readyToPost ? "true" : "false");
        spread.put("planningPhase", ranked.userInputRequired() ? "WAITING_FOR_CLARIFICATION" : (readyToPost ? "READY_FOR_APPROVAL" : "REVISING"));
        spread.put("planningAssumptionsUsed", String.valueOf(ranked.assumptionsToRecord().size()));

        if (!readyToPost && !ranked.userInputRequired() && cycleIteration >= 4) {
            spread.put("planningRoomCycleError", "DEPTH_FAIL_AFTER_RETRIES: " + depthReason);
        }
        return spread;
    }

    private static void mergeSynthFollowUps(Map<String, Object> synthesisSpread, List<String> aggregated) {
        if (synthesisSpread == null) {
            return;
        }
        Object raw = synthesisSpread.get("planningFollowUpQuestionsJson");
        if (raw == null) {
            return;
        }
        try {
            List<String> qs = JSON.readValue(raw.toString(), new TypeReference<>() {});
            for (String q : qs) {
                if (q != null && !q.isBlank()) {
                    aggregated.add(q.trim());
                }
            }
        } catch (JsonProcessingException ignored) {
            // ignore
        }
    }

    private void runRole(
            PlanningRole role,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Event event,
            Map<String, Object> state,
            Map<String, Object> spread,
            List<String> aggregatedFollowUps) {
        spread.put("planningPhase", "CRITIQUING");
        RolePassResult r = PlanningRolePassRunner.run(
                role, openAiChatClient, plan, profile, event, state, planStateStore, workProfileRegistry);
        if (!r.followUps().isEmpty()) {
            aggregatedFollowUps.addAll(r.followUps());
        }
        if (r.error() != null && !r.error().isBlank() && !r.skipped()) {
            spread.put("planningRolePassLastError", role.name() + ": " + r.error());
        }
    }

    private static FeaturePlanState appendAssumption(FeaturePlanState plan, String text) {
        if (text == null || text.isBlank()) {
            return plan;
        }
        String id = "asm-auto-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return plan.withAppendedAssumption(new AssumptionEntry(id, text.trim(), null));
    }

    private static String buildOrchestratorSummary(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            int cycleIteration) {
        String req = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String gist = req.length() > 200 ? req.substring(0, 199) + "…" : req;
        String arch = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        StringBuilder sb = new StringBuilder();
        sb.append("**Planning round ").append(cycleIteration).append("** — ");
        if (gist.isBlank()) {
            sb.append("Working from your feature request in context.");
        } else {
            sb.append("Working from: ").append(gist);
        }
        sb.append("\n\n");
        sb.append("Architect, auditor, and scribe passes have run on the draft (see the packet when posted). ");
        if (!arch.isBlank()) {
            sb.append("Likely touchpoints start with: ").append(arch.length() > 160 ? arch.substring(0, 159) + "…" : arch).append(" ");
        }
        if (ranked.userInputRequired()) {
            sb.append("\n\nWe need one quick decision from you before we freeze the packet.");
        } else if (depthOk) {
            sb.append("\n\nDepth check passed; we can post the planning packet for final review.");
        } else {
            sb.append("\n\nStill strengthening the draft (").append(depthReason).append("). ");
            sb.append("We will retry automatically or ask if something only you can decide.");
        }
        if (!ranked.assumptionsToRecord().isEmpty()) {
            sb.append("\n\n_Recorded ").append(ranked.assumptionsToRecord().size()).append(" baseline assumption(s) so we do not overload you with low-risk questions._");
        }
        return sb.toString().trim();
    }

    private static Map<String, Object> baseSpread() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planningRoomCycleError", "");
        m.put("planningPhase", "");
        m.put("planningUserInputRequired", "false");
        m.put("planningClarificationChoicesJson", "[]");
        m.put("planningClarificationMetaJson", "{}");
        m.put("planningOrchestratorRoundSummary", "");
        m.put("planningPacketDepthOk", "false");
        m.put("planningPacketDepthReason", "");
        m.put("planningReadyToPostPacket", "false");
        m.put("planningRevisionNeeded", "false");
        m.put("planningQuestionsAskedThisRound", "0");
        m.put("planningBlockingQuestionCount", "0");
        m.put("planningAssumptionsUsed", "0");
        m.put("planningRolePassLastError", "");
        return m;
    }

    private static int parseInt(String s, int dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
