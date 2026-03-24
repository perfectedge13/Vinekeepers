package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationEnginePolicy;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.ClarificationResolutionDecision;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanAssumption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sole production entry for coordinator open-gap evaluation and plan mutations from assumption/defer sweeps. Internally uses
 * {@link CoordinatorClarificationGapEvaluator} only to refine raw rule hits into {@link CanonicalPlanningGap} immediately.
 * Question wording and ledger upsert are owned by {@link com.vinekeepers.workflow.planning.PlanningCyclePipeline}.
 */
public final class CanonicalPlanningGapEngine {

    public record AssessedGap(
            CanonicalPlanningGap gap, ClarificationResolutionDecision decision, String assumptionToRecord) {}

    /**
     * Result of gap derivation and resolution sweeps before question composition and ledger projection.
     *
     * @param rawOpenGaps open coordinator gaps from the evaluator after final plan state
     * @param candidateAskGap first gap the resolution policy chose for {@link ClarificationResolutionDecision#ASK_USER}, if any
     */
    public record CanonicalGapDerivationOutcome(
            FeaturePlanState plan,
            List<CoordinatorClarificationGapEvaluator.OpenGap> rawOpenGaps,
            Optional<CanonicalPlanningGap> candidateAskGap,
            boolean hardClarificationBlock,
            String hardClarificationBlockReason) {}

    private static final Comparator<AssessedGap> PLAN_GAP_ORDER =
            Comparator.comparing((AssessedGap ag) -> ag.gap().blocking() ? 0 : 1)
                    .thenComparing((AssessedGap ag) -> ag.gap().severity() == CanonicalGapSeverity.HIGH ? 0 : 1)
                    .thenComparing(ag -> ag.gap().kind().ordinal())
                    .thenComparing(ag -> ag.gap().gapId());

    private CanonicalPlanningGapEngine() {}

    /** Gap assessment for tests and diagnostics. */
    public static List<AssessedGap> assessCanonicalGaps(
            FeaturePlanState plan,
            CoordinatorClarificationSettings settings,
            boolean semanticGapsAllowed,
            String planningRepoEvidenceJson,
            boolean critiqueBlockingFollowUp) {
        if (plan == null || settings == null || !settings.isCanonicalV1() || !semanticGapsAllowed) {
            return List.of();
        }
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(plan, settings);
        if (open.isEmpty()) {
            return List.of();
        }
        CoordinatorClarificationEnginePolicy pol = settings.getEnginePolicy();
        double evidence = PlanningConfidenceService.repoEvidenceGroundingScore(plan, planningRepoEvidenceJson);
        double askThreshold = pol.getRepoEvidenceAskThreshold();
        if (critiqueBlockingFollowUp && pol.isAllowClarificationAfterCritique()) {
            askThreshold = Math.max(0.15, askThreshold - 0.12);
        }
        int turns = plan.getClarificationTurnsCompleted();
        int budget = pol.getMaxClarificationTurns();
        boolean budgetExhausted = budget > 0 && turns >= budget;

        List<AssessedGap> assessed = new ArrayList<>();
        for (CoordinatorClarificationGapEvaluator.OpenGap o : open) {
            CoordinatorClarificationGapRule rule = settings.findGapRule(o.gapId()).orElse(null);
            CanonicalPlanningGap cg = CanonicalPlanningGap.fromCoordinatorOpenGap(o, rule, 0);
            assessed.add(
                    CanonicalGapResolutionPolicy.resolveOneGap(
                            cg,
                            settings,
                            evidence,
                            askThreshold,
                            pol,
                            budgetExhausted,
                            gapAskCount(plan, cg.gapId())));
        }
        assessed.sort(PLAN_GAP_ORDER);
        return List.copyOf(assessed);
    }

    private static int gapAskCount(FeaturePlanState plan, String gapId) {
        if (gapId == null || gapId.isBlank() || plan == null) {
            return 0;
        }
        return PlanningGapAskCounts.countForGap(plan.getPlanningGapAskCountsJson(), gapId);
    }

    public static CanonicalGapDerivationOutcome deriveCanonicalGapsAndApplySweeps(
            String contextId,
            FeaturePlanState plan,
            Map<String, Object> state,
            WorkProfileDefinition profile,
            boolean draftingCompletedThisInvocation,
            FeaturePlanStateStore planStateStore) {
        CoordinatorClarificationSettings coord = profile.getCoordinatorClarification();
        if (!coord.isCanonicalV1()) {
            return new CanonicalGapDerivationOutcome(plan, List.of(), Optional.empty(), false, "");
        }
        boolean semanticAllowed = semanticClarificationAllowed(plan, state, draftingCompletedThisInvocation);
        boolean critiqueSweep =
                plan != null
                        && plan.getPlanningCanonicalDecision() != null
                        && "post_critique".equalsIgnoreCase(plan.getPlanningCanonicalDecision().source())
                        && plan.getPlanningCanonicalDecision().nextAction()
                                == com.vinekeepers.state.planning.PlanningCanonicalNextAction.ASK_ONE_QUESTION;
        for (int sweep = 0; sweep < 3; sweep++) {
            List<AssessedGap> assessedSweep =
                    assessCanonicalGaps(
                            plan,
                            coord,
                            semanticAllowed,
                            stringFromState(state, "planningRepoEvidenceJson"),
                            critiqueSweep);
            boolean progressed = false;
            for (AssessedGap ag : assessedSweep) {
                if (ag.decision() == ClarificationResolutionDecision.ASSUME_AND_CONTINUE
                        && ag.assumptionToRecord() != null
                        && !ag.assumptionToRecord().isBlank()) {
                    plan = appendAssumption(plan, ag.assumptionToRecord());
                    progressed = true;
                } else if (ag.decision() == ClarificationResolutionDecision.LOW_PRIORITY_DEFER) {
                    plan =
                            plan.withClarificationEngineNote(
                                    "Deferred coordinator gap `" + ag.gap().gapId() + "` (low priority / budget policy).");
                } else if (ag.decision() == ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE) {
                    plan =
                            plan.withClarificationEngineNote(
                                    "Clarification budget exhausted on blocking gap `" + ag.gap().gapId() + "`.");
                }
            }
            if (progressed) {
                planStateStore.update(plan);
                plan = planStateStore.getByContextId(contextId).orElse(plan);
            } else {
                break;
            }
        }
        List<AssessedGap> assessedFinal =
                assessCanonicalGaps(
                        plan,
                        coord,
                        semanticAllowed,
                        stringFromState(state, "planningRepoEvidenceJson"),
                        critiqueSweep);
        for (AssessedGap ag : assessedFinal) {
            if (ag.decision() == ClarificationResolutionDecision.ASSUME_AND_CONTINUE
                    && ag.assumptionToRecord() != null
                    && !ag.assumptionToRecord().isBlank()) {
                plan = appendAssumption(plan, ag.assumptionToRecord());
            } else if (ag.decision() == ClarificationResolutionDecision.LOW_PRIORITY_DEFER) {
                plan =
                        plan.withClarificationEngineNote(
                                "Deferred coordinator gap `" + ag.gap().gapId() + "` (low priority / budget policy).");
            } else if (ag.decision() == ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE) {
                plan =
                        plan.withClarificationEngineNote(
                                "Clarification budget exhausted on blocking gap `" + ag.gap().gapId() + "`.");
            }
        }
        planStateStore.update(plan);
        plan = planStateStore.getByContextId(contextId).orElse(plan);
        assessedFinal =
                assessCanonicalGaps(
                        plan,
                        coord,
                        semanticAllowed,
                        stringFromState(state, "planningRepoEvidenceJson"),
                        critiqueSweep);
        List<CoordinatorClarificationGapEvaluator.OpenGap> openRawFinal =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(plan, coord, semanticAllowed);
        CanonicalPlanningGap topAsk = null;
        for (AssessedGap ag : assessedFinal) {
            if (ag.decision() == ClarificationResolutionDecision.ASK_USER) {
                topAsk = ag.gap();
                break;
            }
        }
        String hardClarificationBlockReason =
                assessedFinal.stream()
                        .filter(ag -> ag.decision() == ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE)
                        .map(AssessedGap::assumptionToRecord)
                        .filter(note -> note != null && !note.isBlank())
                        .findFirst()
                        .orElse("");
        boolean hardClarificationBlock = !hardClarificationBlockReason.isBlank() && topAsk == null;
        return new CanonicalGapDerivationOutcome(
                plan,
                List.copyOf(openRawFinal),
                Optional.ofNullable(topAsk),
                hardClarificationBlock,
                hardClarificationBlockReason);
    }

    public static boolean semanticClarificationAllowed(
            FeaturePlanState plan, Map<String, Object> state, boolean draftingCompletedThisInvocation) {
        if (draftingCompletedThisInvocation) {
            return true;
        }
        if (plan != null && plan.isAutonomousPlanningPassCompleted()) {
            return true;
        }
        return "true".equalsIgnoreCase(stringFromState(state, "planningAutonomousFirstPassCompleted"));
    }

    private static String stringFromState(Map<String, Object> state, String key) {
        if (state == null || key == null) {
            return "";
        }
        Object v = state.get(key);
        return v != null ? v.toString() : "";
    }

    private static FeaturePlanState appendAssumption(FeaturePlanState plan, String text) {
        if (text == null || text.isBlank()) {
            return plan;
        }
        String id = "asm-auto-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return plan.withAppendedAssumption(PlanAssumption.fromLegacyText(id, text.trim(), null));
    }
}
