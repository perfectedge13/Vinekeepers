package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;

/**
 * Legacy material-loop types and structured-material helpers. Spread keys live in {@link PlanningMaterialSpreadKeys};
 * cycle pacing lives in {@link PlanningMaterialCyclePacing}. No policy {@code derive} entry point remains in production.
 */
public final class PlanningPostDraftGovernor {

    private PlanningPostDraftGovernor() {}

    public enum LoopOutcome {
        ASK,
        REDRAFT,
        POST,
        ASSUME,
        BLOCK
    }

    public record Result(
            PlanningPostDraftAction action,
            LoopOutcome outcome,
            String noticeMarkdown,
            boolean forceUserInputRequired,
            boolean userInputRequired,
            boolean readyToPostPacket,
            String planningPhase,
            boolean revisionNeeded) {

        public boolean packetPostingAllowed() {
            return outcome == LoopOutcome.POST || outcome == LoopOutcome.ASSUME;
        }
    }

    static String revisionSituationFingerprint(
            boolean depthOk,
            boolean structuredParseFailed,
            String depthReason,
            boolean userInputRequired,
            boolean readyToPost,
            UnresolvedItemLedger ledger) {
        return PlanningMaterialFingerprint.revisionSituationFingerprint(
                depthOk, structuredParseFailed, depthReason, userInputRequired, readyToPost, ledger);
    }

    public static String materialStateChangeFingerprint(java.util.Map<String, Object> signalState, FeaturePlanState plan) {
        return PlanningMaterialFingerprint.materialStateChangeFingerprint(signalState, plan);
    }

    public static String firstOpenPlanningQuestionTextOrEmpty(UnresolvedItemLedger ledger) {
        String q = firstOpenPlanningQuestion(ledger);
        return q != null ? q : "";
    }

    public static String firstUserFacingClarificationTextOrEmpty(
            UnresolvedItemLedger ledger, FeaturePlanState plan) {
        return firstOpenPlanningQuestionTextOrEmpty(ledger);
    }

    public static boolean hasStructuredMaterialPlanningGaps(FeaturePlanState plan) {
        return !firstStructuredMaterialQuestion(plan).isBlank();
    }

    public static String firstStructuredMaterialQuestion(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        for (String uq : plan.getUnresolvedQuestions()) {
            if (uq != null) {
                String t = uq.trim();
                if (!t.isBlank()) {
                    return t;
                }
            }
        }
        for (PlanIssue issue : plan.getIssues()) {
            if (!issue.isBlocking() || !PlanIssueStatus.OPEN.equalsIgnoreCase(issue.getStatus())) {
                continue;
            }
            String title = issue.getTitle() != null ? issue.getTitle().trim() : "";
            if (!title.isBlank()) {
                return title;
            }
            String detail = issue.getDetail() != null ? issue.getDetail().trim() : "";
            if (!detail.isBlank()) {
                return detail;
            }
        }
        for (PlanAssumption a : plan.getAssumptions()) {
            if (!PlanAssumptionStatus.OPEN.equalsIgnoreCase(a.getStatus())) {
                continue;
            }
            if (!PlanGovernanceSeverity.HIGH.equalsIgnoreCase(a.getSeverity())) {
                continue;
            }
            String s = a.getStatement() != null ? a.getStatement().trim() : "";
            if (!s.isBlank()) {
                String head = s.length() <= 220 ? s : s.substring(0, 219) + "…";
                return "Confirm or correct this high-severity assumption: " + head;
            }
        }
        return "";
    }

    private static String firstOpenPlanningQuestion(UnresolvedItemLedger ledger) {
        if (ledger == null) {
            return null;
        }
        for (UnresolvedItem it : ledger.items()) {
            if (it.getStatus() != UnresolvedItemStatus.OPEN) {
                continue;
            }
            if (!"planning_clarification".equals(it.getSource().get("channel"))) {
                continue;
            }
            String q = it.getQuestionText();
            if (q != null && !q.isBlank()) {
                return q.trim();
            }
        }
        return null;
    }
}
