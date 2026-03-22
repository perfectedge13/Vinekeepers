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
        List<String> aggregatedFollowUps = new ArrayList<>();
        Map<String, Object> work = new LinkedHashMap<>();
        if (state != null) {
            work.putAll(state);
        }
        Object expansionObj = new RunRequestExpansionLlmAction(openAiChatClient, planStateStore, workProfileRegistry)
                .run(event, work, bind);
        if (expansionObj instanceof Map<?, ?> expMap) {
            for (Map.Entry<?, ?> e : expMap.entrySet()) {
                if (e.getKey() != null) {
                    work.put(e.getKey().toString(), e.getValue());
                    spread.put(e.getKey().toString(), e.getValue());
                }
            }
        }
        mergeExpansionFollowUpsFromWork(work, aggregatedFollowUps);

        new BuildRequestExplorationAction(planStateStore, workProfileRegistry).run(event, work, bind);
        String depthReason = "";
        boolean depthOk = false;

        for (int inner = 0; inner < MAX_BOT_INNER_ROUNDS; inner++) {
            spread.put("planningPhase", "DRAFTING");
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.ARCHITECT, plan, profile, event, work, spread, aggregatedFollowUps);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.AUDITOR, plan, profile, event, work, spread, aggregatedFollowUps);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.SCRIBE, plan, profile, event, work, spread, aggregatedFollowUps);

            new ExpandPlanningDraftsAction(planStateStore, workProfileRegistry).run(event, work, bind);

            Object synthObj =
                    new RunLlmPlanningSynthesisAction(openAiChatClient, planStateStore, workProfileRegistry)
                            .run(event, work, bind);
            @SuppressWarnings("unchecked")
            Map<String, Object> synthSpread =
                    synthObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            mergeSynthFollowUps(synthSpread, aggregatedFollowUps);

            new SynthesizePreCritiqueArtifactsAction(planStateStore, workProfileRegistry).run(event, work, bind);

            plan = planStateStore.getByContextId(contextId).orElse(plan);
            PlanningPacketDepthEvaluator.DepthResult dr = PlanningPacketDepthEvaluator.evaluate(plan);
            depthOk = dr.ok();
            depthReason = dr.reason() != null ? dr.reason() : "";
            spread.put("planningPacketDepthOk", depthOk ? "true" : "false");
            spread.put("planningPacketDepthReason", depthReason);
            spread.put("planningPacketDepthRetryRecommended", depthOk ? "false" : "true");
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
        spread.put("planningClarificationUseStructuredChoices", ranked.useStructuredChoices() ? "true" : "false");
        spread.put("planningClarificationQuestionText", ranked.questionText() != null ? ranked.questionText() : "");
        spread.put(
                "planningClarificationOrchestratorPrompt",
                ranked.orchestratorPrompt() != null ? ranked.orchestratorPrompt() : "");
        boolean readyToPost = depthOk && !ranked.userInputRequired();
        spread.put(
                "planningOrchestratorRoundSummary",
                buildOrchestratorSummary(plan, depthOk, depthReason, ranked, cycleIteration, readyToPost));
        spread.put(
                "planningRevisionNeeded",
                (!depthOk || ranked.userInputRequired()) ? "true" : "false");

        spread.put("planningReadyToPostPacket", readyToPost ? "true" : "false");
        spread.put("planningPhase", ranked.userInputRequired() ? "WAITING_FOR_CLARIFICATION" : (readyToPost ? "READY_FOR_APPROVAL" : "REVISING"));
        spread.put("planningAssumptionsUsed", String.valueOf(ranked.assumptionsToRecord().size()));

        if (!readyToPost && !ranked.userInputRequired() && cycleIteration >= 4) {
            spread.put("planningRoomCycleError", "DEPTH_FAIL_AFTER_RETRIES: " + depthReason);
        }
        return spread;
    }

    private static void mergeExpansionFollowUpsFromWork(Map<String, Object> work, List<String> aggregated) {
        if (work == null || aggregated == null) {
            return;
        }
        Object raw = work.get("planningExpansionFollowUpsJson");
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

    /**
     * User-visible planning round summary: understanding, draft status, assumptions, and optional clarification blocks.
     */
    static String buildOrchestratorSummary(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            int cycleIteration,
            boolean readyToPostPacket) {
        String req = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String gist = req.length() > 200 ? req.substring(0, 199) + "…" : req;
        String arch = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        StringBuilder sb = new StringBuilder();
        sb.append("**Planning round ").append(cycleIteration).append("**\n\n");
        sb.append("**What we're working from:** ");
        if (gist.isBlank()) {
            sb.append("Your feature request from this thread (no separate summary text on file).");
        } else {
            sb.append(gist);
        }
        sb.append(workspaceSummaryLine(plan));

        sb.append("\n\n**Draft status:** ");
        if (ranked.userInputRequired()) {
            sb.append(
                    "The coordinator passes have run on the current draft, but we are **not** posting the planning packet yet until we resolve the clarification below.");
        } else if (readyToPostPacket) {
            sb.append("Depth check passed; the planning packet is ready to post in this thread for review.");
        } else if (depthOk) {
            sb.append("Depth check passed; the next steps will post or refine the packet.");
        } else {
            sb.append("Still strengthening the draft (").append(depthReason).append("). ");
            sb.append("We will retry automatically inside this round or ask you only when something needs a human decision.");
        }
        if (!arch.isBlank()) {
            sb.append("\n\n**Likely touchpoints:** ")
                    .append(arch.length() > 200 ? arch.substring(0, 199) + "…" : arch);
        }

        if (!ranked.assumptionsToRecord().isEmpty()) {
            sb.append("\n\n**Assumptions recorded this round (auto):**\n");
            for (String a : ranked.assumptionsToRecord()) {
                sb.append("- ").append(a).append("\n");
            }
        }

        if (ranked.userInputRequired()) {
            String q = ranked.questionText() != null ? ranked.questionText().trim() : "";
            sb.append("\n**Clarification**\n\n");
            if (!q.isBlank()) {
                sb.append(q).append("\n\n");
            }
            sb.append("**Why this matters:** ");
            if (ranked.blockingQuestionCount() > 0) {
                sb.append(
                        "Your answer affects compatibility, security, migrations, or validation — getting it wrong would be expensive to unwind during implementation.\n\n");
            } else {
                sb.append(
                        "This shapes scope and design choices in the packet so implementation matches what you expect.\n\n");
            }
            sb.append(
                    "**What happens next:** After you answer, we refresh the draft, re-run depth checks, update the planning packet in this thread when ready, and run critique/readiness again **before** any approval step.\n");
            if (ranked.useStructuredChoices()) {
                sb.append(
                        "\nChoose an option below, or pick **Use recommended default** to record our baseline and continue.");
            } else {
                sb.append("\n**Reply in plain text** with your answer. You can give examples or edge cases — no need to match a fixed list.");
            }
        }
        return sb.toString().trim();
    }

    private static String workspaceSummaryLine(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        String path = plan.getRepoLocalPath() != null ? plan.getRepoLocalPath().trim() : "";
        String st = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
        if (path.isBlank() && st.isBlank()) {
            return "";
        }
        StringBuilder w = new StringBuilder();
        w.append("\n**Workspace:** ");
        if (!st.isBlank()) {
            w.append("status ").append(st);
        }
        if (!path.isBlank()) {
            if (!st.isBlank()) {
                w.append("; ");
            }
            w.append("local path `").append(path.length() > 120 ? path.substring(0, 119) + "…" : path).append("`");
        }
        w.append(".");
        return w.toString();
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
        m.put("planningPacketDepthRetryRecommended", "false");
        m.put("planningReadyToPostPacket", "false");
        m.put("planningRevisionNeeded", "false");
        m.put("planningQuestionsAskedThisRound", "0");
        m.put("planningBlockingQuestionCount", "0");
        m.put("planningAssumptionsUsed", "0");
        m.put("planningRolePassLastError", "");
        m.put("planningClarificationUseStructuredChoices", "false");
        m.put("planningClarificationQuestionText", "");
        m.put("planningClarificationOrchestratorPrompt", "");
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
