package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;

import java.util.List;

/**
 * Finalized evaluation-to-orchestration handoff for the live planning path.
 * This is the single semantic decision object the pipeline is allowed to trust.
 */
public record PlanningEvaluationDecision(
        boolean success,
        PlanningCanonicalNextAction nextAction,
        PlanningIntakeStage stage,
        PlanningInteractionState interactionState,
        String repoGroundingState,
        EvaluationConfidence confidence,
        List<CanonicalPlanningGap> gaps,
        boolean askUserRequired,
        EvaluationBestQuestion bestQuestion,
        String topGapId,
        String topGapText,
        List<AssumptionProposal> assumptionsToAdd,
        List<IssueProposal> issuesToAdd,
        List<RiskProposal> risksToAdd,
        List<DecisionProposal> decisionsToAdd,
        boolean readyForPacket,
        boolean packetPostingAllowed,
        boolean reviewAllowed,
        boolean approvalAllowed,
        String blockReason,
        String summary,
        boolean repairAttempted,
        boolean repairExhausted,
        String machineError) {

    public record EvaluationConfidence(int score, String level, String summary) {}

    public record EvaluationBestQuestion(String text, String rationale) {}

    public record AssumptionProposal(String statement, String severity) {}

    public record IssueProposal(String title, String detail, String severity, boolean blocking) {}

    public record RiskProposal(String statement, String impact, String likelihood) {}

    public record DecisionProposal(String decision, String rationale, String status) {}

    public PlanningEvaluationDecision {
        nextAction = nextAction != null ? nextAction : PlanningCanonicalNextAction.BLOCK;
        stage = stage != null ? stage : PlanningIntakeStage.FAILED;
        interactionState = interactionState != null ? interactionState : PlanningInteractionState.NONE;
        repoGroundingState = blankToEmpty(repoGroundingState);
        confidence = confidence != null ? confidence : new EvaluationConfidence(0, "low", "");
        gaps = gaps != null ? List.copyOf(gaps) : List.of();
        bestQuestion = bestQuestion != null ? bestQuestion : new EvaluationBestQuestion("", "");
        topGapId = blankToEmpty(topGapId);
        topGapText = blankToEmpty(topGapText);
        assumptionsToAdd = assumptionsToAdd != null ? List.copyOf(assumptionsToAdd) : List.of();
        issuesToAdd = issuesToAdd != null ? List.copyOf(issuesToAdd) : List.of();
        risksToAdd = risksToAdd != null ? List.copyOf(risksToAdd) : List.of();
        decisionsToAdd = decisionsToAdd != null ? List.copyOf(decisionsToAdd) : List.of();
        blockReason = blankToEmpty(blockReason);
        summary = blankToEmpty(summary);
        machineError = blankToEmpty(machineError);
    }

    public CanonicalPlanningGap chosenAskGap() {
        if (!askUserRequired) {
            return null;
        }
        for (CanonicalPlanningGap gap : gaps) {
            if (gap != null && gap.askable() && (gap.blocking() || gap.branching())) {
                return gap;
            }
        }
        return null;
    }

    public String canonicalQuestionText() {
        return bestQuestion != null && bestQuestion.text() != null ? bestQuestion.text().trim() : "";
    }

    public PlanningCanonicalDecision toCanonicalDecision(
            String source, List<String> explicitAssumptions, String materialStateChangeFingerprint) {
        return PlanningCanonicalDecision.create(
                blankToDefault(source, "planning_evaluation"),
                stage,
                nextAction,
                interactionState,
                repoGroundingState,
                confidence != null ? blankToEmpty(confidence.summary()) : "",
                packetPostingAllowed,
                reviewAllowed,
                approvalAllowed,
                topGapText,
                topGapId,
                canonicalQuestionText(),
                blockReason,
                explicitAssumptions != null ? explicitAssumptions : List.of(),
                materialStateChangeFingerprint);
    }

    private static String blankToDefault(String value, String dflt) {
        return value == null || value.isBlank() ? dflt : value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
