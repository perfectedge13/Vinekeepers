package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.actions.BuildRequestExplorationAction;
import com.vinekeepers.workflow.actions.ExpandPlanningDraftsAction;
import com.vinekeepers.workflow.actions.RunLlmPlanningSynthesisAction;
import com.vinekeepers.workflow.actions.RunRequestExpansionLlmAction;
import com.vinekeepers.workflow.actions.SynthesizePreCritiqueArtifactsAction;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.RolePassResult;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Silent (non-interactive) drafting: expansion, role passes, synthesis, pre-critique artifacts, depth. Runs before any
 * coordinator clarification selection each cycle.
 */
public final class SilentPlanningSynthesisService {

    public record SilentPlanningSynthesisResult(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            String lastRoleRoundSummary,
            String lastSynthLlmLine) {}

    public record InnerRoundResult(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            String lastRoleRoundSummary,
            String lastSynthLlmLine) {}

    private final OpenAiChatClient openAiChatClient;
    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public SilentPlanningSynthesisService(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    public void runExpansionPhase(
            Event event, Map<String, Object> work, Map<String, Object> spread, Map<String, Object> bind) {
        Object expansionObj =
                new RunRequestExpansionLlmAction(openAiChatClient, planStateStore, workProfileRegistry)
                        .run(event, work, bind);
        if (expansionObj instanceof Map<?, ?> expMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> exp = (Map<String, Object>) expMap;
            mergeSpreadIntoWorkAndOuter(exp, work, spread);
        }
        new BuildRequestExplorationAction(planStateStore, workProfileRegistry).run(event, work, bind);
    }

    public SilentPlanningSynthesisResult runSilentSynthesisPhase(
            Event event,
            String contextId,
            WorkProfileDefinition profile,
            Map<String, Object> work,
            Map<String, Object> spread,
            Map<String, Object> bind,
            FeaturePlanState plan,
            boolean skipExpansion,
            String previousSynthLine,
            int maxInnerRounds) {
        if (!skipExpansion) {
            runExpansionPhase(event, work, spread, bind);
        } else {
            spread.put("planningSelectiveRerunActive", "true");
            spread.put(
                    "planningSelectiveRerunNote",
                    "Thanks — I'm refreshing the draft from your last answer (skipping a full re-scan this pass).");
        }
        FeaturePlanState planPtr = plan;
        boolean depthOk = false;
        String depthReason = "";
        String lastRoleRoundSummary = "";
        String lastSynthLlmLine = previousSynthLine != null ? previousSynthLine : "";
        int rounds = Math.max(1, maxInnerRounds);
        for (int inner = 0; inner < rounds; inner++) {
            InnerRoundResult round =
                    runSingleInnerRound(
                            event,
                            contextId,
                            profile,
                            work,
                            spread,
                            bind,
                            planPtr,
                            lastSynthLlmLine);
            planPtr = round.plan();
            depthOk = round.depthOk();
            depthReason = round.depthReason();
            lastRoleRoundSummary = round.lastRoleRoundSummary();
            lastSynthLlmLine = round.lastSynthLlmLine();
            spread.put(
                    "planningCycleProgressSummary",
                    "Silent synthesis round "
                            + (inner + 1)
                            + "/"
                            + rounds
                            + (depthOk ? " — draft is packet-ready enough for evaluation." : " — continuing silent synthesis."));
            if (depthOk) {
                break;
            }
        }
        return new SilentPlanningSynthesisResult(planPtr, depthOk, depthReason, lastRoleRoundSummary, lastSynthLlmLine);
    }

    public InnerRoundResult runSingleInnerRound(
            Event event,
            String contextId,
            WorkProfileDefinition profile,
            Map<String, Object> work,
            Map<String, Object> spread,
            Map<String, Object> bind,
            FeaturePlanState plan,
            String previousSynthLine) {
        FeaturePlanState planPtr = planStateStore.getByContextId(contextId).orElse(plan);
        List<String> roleTags = new ArrayList<>();
        List<PlanningCoordinatorRole> passOrder = ConfigurablePassRunner.resolveOrder(work, bind);
        spread.put("planningRolePassOrderResolved", passOrder.toString());
        for (PlanningCoordinatorRole passRole : passOrder) {
            runRole(passRole, planPtr, profile, event, work, spread, roleTags);
            planPtr = planStateStore.getByContextId(contextId).orElse(planPtr);
        }
        String lastRoleRoundSummary = summarizeRoleRound(passOrder, roleTags);

        new ExpandPlanningDraftsAction(planStateStore, workProfileRegistry).run(event, work, bind);

        Object synthObj =
                new RunLlmPlanningSynthesisAction(openAiChatClient, planStateStore, workProfileRegistry)
                        .run(event, work, bind);
        @SuppressWarnings("unchecked")
        Map<String, Object> synthSpread = synthObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        mergeSpreadIntoWorkAndOuter(synthSpread, work, spread);
        applyImmediateSynthesisFailure(contextId, spread, synthSpread);
        String lastSynthLlmLine = pickSynthLlmLine(synthSpread, previousSynthLine);

        new SynthesizePreCritiqueArtifactsAction(planStateStore, workProfileRegistry).run(event, work, bind);

        planPtr = planStateStore.getByContextId(contextId).orElse(planPtr);
        boolean relaxOpenQ = PlanningPacketDepthEvaluator.relaxOpenQuestionSupplementalChecks(work);
        PlanningPacketDepthEvaluator.DepthResult dr =
                PlanningPacketDepthEvaluator.evaluate(planPtr, profile, relaxOpenQ);
        boolean depthOk = dr.ok();
        String depthReason = dr.reason() != null ? dr.reason() : "";
        spread.put("planningPacketDepthOk", depthOk ? "true" : "false");
        spread.put("planningPacketDepthReason", depthReason);
        spread.put("planningPacketDepthRetryRecommended", depthOk ? "false" : "true");
        return new InnerRoundResult(planPtr, depthOk, depthReason, lastRoleRoundSummary, lastSynthLlmLine);
    }

    private static void mergeSpreadIntoWorkAndOuter(
            Map<String, Object> from, Map<String, Object> work, Map<String, Object> spread) {
        if (from == null) {
            return;
        }
        for (Map.Entry<String, Object> e : from.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = e.getKey().toString();
            work.put(k, e.getValue());
            spread.put(k, e.getValue());
        }
    }

    private static String pickSynthLlmLine(Map<String, Object> synthSpread, String previous) {
        if (synthSpread == null) {
            return previous;
        }
        String err = getString(synthSpread, "planningLlmError");
        if (err != null && !err.isBlank()) {
            return truncateOneLine(PlanningUserFacingCopy.humanizePlanningCycleFailureFragment(err), 140);
        }
        String skip = getString(synthSpread, "planningLlmSkipReason");
        if (skip != null && !skip.isBlank() && !"OK".equalsIgnoreCase(skip)) {
            return truncateOneLine(
                    PlanningUserFacingCopy.humanizePlanningCycleFailureFragment("Synthesis skipped: " + skip), 140);
        }
        return previous;
    }

    private void applyImmediateSynthesisFailure(
            String contextId, Map<String, Object> spread, Map<String, Object> synthSpread) {
        if (synthSpread == null || spread == null) {
            return;
        }
        String llmErr = getString(synthSpread, "planningLlmError");
        if (llmErr == null || llmErr.isBlank()) {
            return;
        }
        int applied = parseInt(getString(synthSpread, "planningLlmUpsertCount"), 0);
        if (applied > 0) {
            return;
        }
        String cat = blankToEmpty(getString(synthSpread, "planningSynthesisFailureCategory"));
        boolean structuredRoleFailed =
                "true".equalsIgnoreCase(getString(spread, PlanningCyclePipeline.PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY));
        FeaturePlanState planForRecover = planStateStore.getByContextId(contextId).orElse(null);
        boolean recoverable = hasRecoverablePlanningDraft(planForRecover) && !structuredRoleFailed;
        spread.put("planningRecoverableDraftAfterSynthesis", recoverable ? "true" : "false");
        if (!cat.isBlank()) {
            spread.put("planningSynthesisFailureCategory", cat);
            String existing = getString(spread, "planningRoomCycleError");
            if (existing == null || existing.isBlank()) {
                spread.put("planningRoomCycleError", cat);
                spread.put(
                        "planningRoomCycleErrorUserMessage",
                        PlanningUserFacingCopy.humanizePlanningRoomCycleErrorLine(cat));
            }
            return;
        }
        String existing = getString(spread, "planningRoomCycleError");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        spread.put("planningRoomCycleError", "SYNTHESIS_UPSERTS_NOT_APPLIED");
        spread.put(
                "planningRoomCycleErrorUserMessage",
                PlanningUserFacingCopy.humanizePlanningRoomCycleErrorLine("SYNTHESIS_UPSERTS_NOT_APPLIED"));
    }

    private static boolean hasRecoverablePlanningDraft(FeaturePlanState plan) {
        if (plan == null) {
            return false;
        }
        if (com.vinekeepers.workflow.planreview.PlanStructuredMaterialDiagnostics.hasStructuredMaterialPlanningGaps(plan)) {
            return true;
        }
        String ex = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        String fs = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        return (ex != null && !ex.isBlank()) || (fs != null && !fs.isBlank());
    }

    private void runRole(
            PlanningCoordinatorRole role,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Event event,
            Map<String, Object> state,
            Map<String, Object> spread,
            List<String> roleRoundTags) {
        RolePassResult r =
                PlanningRolePassRunner.run(
                        role, openAiChatClient, plan, profile, event, state, planStateStore, workProfileRegistry);
        String label = roleUserLabel(role);
        if (r.skipped() && "NO_API_KEY".equals(r.error())) {
            roleRoundTags.add("SKIP_NO_KEY");
            spread.put("planningRolePassLastError", label + ": skipped (no API key)");
        } else if (r.skipped()) {
            String reason = r.error() != null ? r.error() : "skipped";
            roleRoundTags.add("SKIP_OTHER:" + label + ": " + truncateOneLine(reason, 80));
            spread.put("planningRolePassLastError", label + ": skipped (" + truncateOneLine(reason, 100) + ")");
        } else if (r.error() != null && looksLikeInterruptedLlmError(r.error())) {
            roleRoundTags.add("INTERRUPTED:" + label);
            spread.put(PlanningCyclePipeline.PLANNING_PASS_INTERRUPTED_KEY, "true");
            spread.put("planningRolePassLastError", label + ": interrupted");
        } else if (r.error() != null
                && r.error().startsWith(StructuredLlmArtifactUpsertPass.STRUCTURED_JSON_PARSE_PREFIX)) {
            roleRoundTags.add("PARSE_ERR:" + label);
            spread.put(PlanningCyclePipeline.PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY, "true");
            String detail =
                    r.error()
                            .substring(StructuredLlmArtifactUpsertPass.STRUCTURED_JSON_PARSE_PREFIX.length())
                            .trim();
            spread.put(
                    "planningRolePassLastError",
                    label + ": invalid structured JSON — " + truncateOneLine(detail, 120));
        } else if (r.error() != null && !r.error().isBlank()) {
            spread.put("planningRolePassLastError", role.name() + ": " + r.error());
            roleRoundTags.add("ERR:" + label + ": " + truncateOneLine(r.error(), 100));
        } else {
            roleRoundTags.add("OK:" + label);
        }
    }

    private static String summarizeRoleRound(List<PlanningCoordinatorRole> order, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "Role passes not run.";
        }
        if (tags.size() >= 3 && tags.stream().allMatch("SKIP_NO_KEY"::equals)) {
            return "Coordinator passes skipped (OpenAI API key not configured).";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            String t = tags.get(i);
            String who = i < order.size() ? roleUserLabel(order.get(i)) : "Role";
            if ("SKIP_NO_KEY".equals(t)) {
                parts.add(who + " skipped (no API key)");
            } else if (t != null && t.startsWith("SKIP_OTHER:")) {
                parts.add(t.substring("SKIP_OTHER:".length()).trim());
            } else if (t != null && t.startsWith("INTERRUPTED:")) {
                parts.add(t.substring("INTERRUPTED:".length()).trim() + " interrupted");
            } else if (t != null && t.startsWith("PARSE_ERR:")) {
                parts.add(t.substring("PARSE_ERR:".length()).trim() + " invalid JSON");
            } else if (t != null && t.startsWith("ERR:")) {
                parts.add(t.substring(4).trim());
            } else if (t != null && t.startsWith("OK:")) {
                parts.add(who + " ok");
            } else {
                parts.add(t != null ? t : who);
            }
        }
        return String.join("; ", parts);
    }

    private static String roleUserLabel(PlanningCoordinatorRole role) {
        return switch (role) {
            case COORDINATOR -> "Coordinator";
        };
    }

    private static boolean looksLikeInterruptedLlmError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String t = error.trim();
        if ("ERROR: interrupted".equalsIgnoreCase(t)) {
            return true;
        }
        String lower = t.toLowerCase();
        return lower.contains("interruptedexception") || lower.contains("thread was interrupted");
    }

    private static String truncateOneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", " ").replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static String blankToEmpty(String s) {
        return s != null ? s.trim() : "";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
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
}
