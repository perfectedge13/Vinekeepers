package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationEnginePolicy;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanCritiqueSnapshot;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;
import java.util.Map;
import java.util.Objects;

/**
 * Derives {@link PlanningPostDraftAction} and durable material-change baselines so autonomous drafting cannot spin on an
 * unchanged revision situation.
 */
public final class PlanningPostDraftGovernor {

    public static final String SPREAD_KEY = "planningPostDraftAction";
    public static final String NOTICE_MARKDOWN_KEY = "planningPostDraftNoticeMarkdown";
    public static final String BASELINE_REPO_HASH_KEY = "planningMaterialBaselineRepoEvidenceHash";
    public static final String BASELINE_ASSUMPTION_COUNT_KEY = "planningMaterialBaselineAssumptionCount";
    public static final String BASELINE_CRITIQUE_BLOCKING_KEY = "planningMaterialBaselineCritiqueBlockingCount";
    public static final String BASELINE_DRAFT_FP_KEY = "planningMaterialBaselineDraftFingerprint";
    public static final String LAST_REVISION_SITUATION_KEY = "planningLastNonPostingRevisionSituation";
    public static final String AUTONOMOUS_REDRAFT_COUNT_KEY = "planningAutonomousRedraftCount";

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

    /**
     * @param persistedSessionState keys persisted from prior workflow turns (baselines, last revision situation)
     * @param signalState current signals (e.g. spread merged this turn for repo evidence and merge flags)
     */
    public static Result derive(
            Map<String, Object> persistedSessionState,
            Map<String, Object> signalState,
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            boolean userInputRequired,
            boolean readyToPost,
            boolean depthOk,
            boolean structuredParseFailed,
            String depthReason,
            String cycleError,
            String synthesisFailureCategory,
            boolean recoverableAfterSynthesis,
            boolean suppressAutonomousRedraftNotice,
            boolean hardClarificationBlock) {
        return derive(
                persistedSessionState,
                signalState,
                plan,
                ledger,
                userInputRequired,
                readyToPost,
                depthOk,
                structuredParseFailed,
                depthReason,
                cycleError,
                synthesisFailureCategory,
                recoverableAfterSynthesis,
                suppressAutonomousRedraftNotice,
                hardClarificationBlock,
                CoordinatorClarificationEnginePolicy.defaultPolicy());
    }

    public static Result derive(
            Map<String, Object> persistedSessionState,
            Map<String, Object> signalState,
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            boolean userInputRequired,
            boolean readyToPost,
            boolean depthOk,
            boolean structuredParseFailed,
            String depthReason,
            String cycleError,
            String synthesisFailureCategory,
            boolean recoverableAfterSynthesis,
            boolean suppressAutonomousRedraftNotice,
            boolean hardClarificationBlock,
            CoordinatorClarificationEnginePolicy policy) {
        String depth = depthReason != null ? depthReason : "";
        String cyc = cycleError != null ? cycleError : "";
        boolean wantsRevision = !readyToPost && !userInputRequired;
        String synthCat = synthesisFailureCategory != null ? synthesisFailureCategory.trim() : "";
        PlanningFailureCategory synthFailure = PlanningFailureCategory.parse(synthCat);
        CoordinatorClarificationEnginePolicy loopPolicy =
                policy != null ? policy : CoordinatorClarificationEnginePolicy.defaultPolicy();
        int autonomousRedraftCount = parseInt(getString(persistedSessionState, AUTONOMOUS_REDRAFT_COUNT_KEY), 0);
        boolean redraftAskThresholdReached =
                wantsRevision
                        && loopPolicy.getMaxAutonomousRedraftsBeforeAsk() > 0
                        && autonomousRedraftCount >= loopPolicy.getMaxAutonomousRedraftsBeforeAsk();
        double clarificationConfidence =
                parseDouble(getString(signalState, PlanningCyclePipeline.PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY), -1.0);
        boolean confidenceGateReached =
                clarificationConfidence >= 0
                        && clarificationConfidence >= loopPolicy.getClarificationConfidenceThreshold();

        boolean material =
                detectMaterialChange(
                        persistedSessionState,
                        signalState,
                        plan,
                        wantsRevision || userInputRequired || readyToPost);
        String situation = revisionSituationFingerprint(depthOk, structuredParseFailed, depth, userInputRequired, readyToPost, ledger);
        String lastSituation = getString(persistedSessionState, LAST_REVISION_SITUATION_KEY);
        boolean sameNonPostingSituation =
                wantsRevision
                        && situation != null
                        && !situation.isBlank()
                        && situation.equals(lastSituation != null ? lastSituation : "");

        if (hardClarificationBlock) {
            String q = firstUserFacingClarificationTextOrEmpty(ledger, plan);
            if (!q.isBlank()) {
                return resultFor(PlanningPostDraftAction.ASK_ONE_QUESTION, "", true);
            }
            if ("true".equalsIgnoreCase(getString(signalState, "planningJustMergedClarification"))) {
                return resultFor(
                        PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                        "**Continuing**\n\nYour clarification was applied successfully, so I'm using that answer and "
                                + "moving the saved draft forward instead of dropping into a blocked state.",
                        false);
            }
            return resultFor(
                    PlanningPostDraftAction.BLOCK,
                    "**Planning paused**\n\nA blocking coordinator gap hit the clarification budget. A human needs to "
                            + "unblock scope or relax constraints before we continue.",
                    false);
        }
        if (!cyc.isBlank() && cyc.startsWith("DEPTH_FAIL_AFTER_RETRIES")) {
            return resultFor(
                    PlanningPostDraftAction.BLOCK,
                    "**Planning paused**\n\nDepth checks failed after several tries. Reply with one concrete constraint, "
                            + "example, or acceptance check you care about, or confirm a smaller scope so we can ship a "
                            + "reviewable packet.",
                    false);
        }

        if (synthFailure == PlanningFailureCategory.SYNTHESIS_EMPTY_NOOP) {
            if (recoverableAfterSynthesis) {
                return resultFor(
                        PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                        "**Continuing**\n\nThe latest drafting pass made no applicable structured changes, so I'm "
                                + "keeping the current draft and moving forward.",
                        false);
            }
            synthFailure = PlanningFailureCategory.NONE;
        }

        if (synthFailure != PlanningFailureCategory.NONE) {
            if (recoverableAfterSynthesis) {
                String q = firstUserFacingClarificationTextOrEmpty(ledger, plan);
                if (!q.isBlank()) {
                    return resultFor(PlanningPostDraftAction.ASK_ONE_QUESTION, "", true);
                }
                if (hasStructuredMaterialPlanningGaps(plan)) {
                    return resultFor(PlanningPostDraftAction.ASK_ONE_QUESTION, "", true);
                }
                String human = PlanningUserFacingCopy.humanizePlanningRoomCycleErrorCode(synthFailure.name());
                return resultFor(
                        PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                        "**Continuing**\n\n"
                                + (human.isBlank() ? "The latest drafting pass had trouble." : human)
                                + " I'm keeping the current draft and moving forward with the saved version.",
                        false);
            }
            String human = PlanningUserFacingCopy.humanizePlanningRoomCycleErrorCode(synthFailure.name());
            return resultFor(
                    PlanningPostDraftAction.BLOCK,
                    "**Planning paused**\n\n" + (human.isBlank() ? synthFailure.name() : human),
                    false);
        }

        if (!cyc.isBlank()) {
            return resultFor(
                    PlanningPostDraftAction.BLOCK,
                    "**Planning paused**\n\n"
                            + (cyc.length() > 220 ? cyc.substring(0, 219) + "…" : cyc),
                    false);
        }

        if (!userInputRequired && !readyToPost && synthesisAskFallbackAvailable(signalState, ledger)) {
            return resultFor(PlanningPostDraftAction.ASK_ONE_QUESTION, "", true);
        }

        if (userInputRequired) {
            return resultFor(
                    PlanningPostDraftAction.ASK_ONE_QUESTION,
                    "",
                    false);
        }
        if (readyToPost) {
            return resultFor(PlanningPostDraftAction.POST_PACKET, "", false);
        }

        if (wantsRevision && confidenceGateReached) {
            return resultFor(
                    PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                    "**Continuing**\n\nThe current draft is above the clarification confidence threshold, so I'm moving "
                            + "forward instead of spending another cycle on a low-value clarification pass.",
                    false);
        }

        boolean ledgerRepeat = ledger != null && ledger.maxOpenItemRepeatCount() >= 1;
        boolean denyRedraft = !material && (sameNonPostingSituation || ledgerRepeat);

        if (wantsRevision && redraftAskThresholdReached) {
            String q = firstUserFacingClarificationTextOrEmpty(ledger, plan);
            if (!q.isBlank()) {
                return resultFor(PlanningPostDraftAction.ASK_ONE_QUESTION, "", true);
            }
            if (!loopPolicy.isAllowAssumeAndContinue()) {
                return resultFor(
                        PlanningPostDraftAction.BLOCK,
                        "**Planning paused**\n\nThe clarification loop hit its max autonomous passes without reaching a "
                                + "confident stopping point. A human needs to unblock the next step.",
                        false);
            }
            return resultFor(
                    PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                    "**Continuing**\n\nI hit the autonomous clarification loop cap without finding another high-value "
                            + "question, so I'm moving the saved draft forward for review.",
                    false);
        }

        if (wantsRevision && denyRedraft) {
            String q = firstOpenPlanningQuestion(ledger);
            if (q != null && !q.isBlank()) {
                return resultFor(
                        PlanningPostDraftAction.ASK_ONE_QUESTION,
                        "",
                        true);
            }
            if (hasStructuredMaterialPlanningGaps(plan)) {
                return resultFor(PlanningPostDraftAction.ASK_ONE_QUESTION, "", true);
            }
            return resultFor(
                    PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                    "**Continuing**\n\nI'm treating remaining gaps as assumptions for this pass and moving the packet "
                            + "forward for review. Reply if you want to correct anything before launch.",
                    false);
        }

        if (wantsRevision) {
            if (suppressAutonomousRedraftNotice) {
                return resultFor(
                        PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                        "**Continuing**\n\nAdvancing from the saved draft without a separate redraft notice — reply "
                                + "only if you want to correct something before the next packet step.",
                        false);
            }
            return resultFor(
                    PlanningPostDraftAction.AUTONOMOUS_REDRAFT,
                    "**Another drafting pass**\n\nTightening the draft from the latest repo signals and coordinator "
                            + "notes — no reply needed unless I ask a specific question next.",
                    false);
        }

        return resultFor(PlanningPostDraftAction.ASSUME_AND_CONTINUE, "", false);
    }

    /** Updates session keys merged from spread after assess (material baselines + last non-posting situation). */
    public static void writePersistenceKeys(
            Map<String, Object> spread,
            Map<String, Object> signalState,
            FeaturePlanState plan,
            Result result,
            boolean wantsRevision,
            String revisionSituationFingerprint) {
        if (spread == null) {
            return;
        }
        String repo = shortHash(getString(signalState, "planningRepoEvidenceJson"));
        int asm = plan != null ? plan.getAssumptions().size() : 0;
        int critBlock = critiqueBlockingCount(plan);
        String draftFp = observabilityDraftFingerprint(plan);
        spread.put(BASELINE_REPO_HASH_KEY, repo != null ? repo : "");
        spread.put(BASELINE_ASSUMPTION_COUNT_KEY, String.valueOf(asm));
        spread.put(BASELINE_CRITIQUE_BLOCKING_KEY, String.valueOf(critBlock));
        spread.put(BASELINE_DRAFT_FP_KEY, draftFp != null ? draftFp : "");
        PlanningPostDraftAction action = result != null ? result.action() : null;
        if (action == PlanningPostDraftAction.AUTONOMOUS_REDRAFT) {
            int prev = parseInt(getString(signalState, AUTONOMOUS_REDRAFT_COUNT_KEY), 0);
            spread.put(AUTONOMOUS_REDRAFT_COUNT_KEY, String.valueOf(prev + 1));
        } else {
            spread.put(AUTONOMOUS_REDRAFT_COUNT_KEY, "0");
        }
        if ((result != null && result.outcome() == LoopOutcome.POST) || !wantsRevision) {
            spread.put(LAST_REVISION_SITUATION_KEY, "");
        } else if (revisionSituationFingerprint != null && !revisionSituationFingerprint.isBlank()) {
            spread.put(LAST_REVISION_SITUATION_KEY, revisionSituationFingerprint);
        }
    }

    private static Result resultFor(
            PlanningPostDraftAction action, String noticeMarkdown, boolean forceUserInputRequired) {
        LoopOutcome outcome = outcomeFor(action);
        boolean effectiveUser = outcome == LoopOutcome.ASK || forceUserInputRequired;
        boolean readyToPostPacket = outcome == LoopOutcome.POST;
        String phase = switch (outcome) {
            case ASK -> "WAITING_FOR_CLARIFICATION";
            case REDRAFT -> "REVISING";
            case POST, ASSUME -> "READY_FOR_REVIEW";
            case BLOCK -> "FAILED";
        };
        boolean revisionNeeded = outcome == LoopOutcome.ASK || outcome == LoopOutcome.REDRAFT;
        return new Result(
                action,
                outcome,
                noticeMarkdown != null ? noticeMarkdown : "",
                forceUserInputRequired,
                effectiveUser,
                readyToPostPacket,
                phase,
                revisionNeeded);
    }

    private static LoopOutcome outcomeFor(PlanningPostDraftAction action) {
        if (action == null) {
            return LoopOutcome.BLOCK;
        }
        return switch (action) {
            case ASK_ONE_QUESTION -> LoopOutcome.ASK;
            case AUTONOMOUS_REDRAFT -> LoopOutcome.REDRAFT;
            case POST_PACKET -> LoopOutcome.POST;
            case ASSUME_AND_CONTINUE -> LoopOutcome.ASSUME;
            case BLOCK -> LoopOutcome.BLOCK;
        };
    }

    static String revisionSituationFingerprint(
            boolean depthOk,
            boolean structuredParseFailed,
            String depthReason,
            boolean userInputRequired,
            boolean readyToPost,
            UnresolvedItemLedger ledger) {
        if (readyToPost) {
            return "";
        }
        String dr = depthReason != null ? depthReason.trim() : "";
        if (dr.length() > 120) {
            dr = dr.substring(0, 119) + "…";
        }
        String gap = primaryOpenPlanningGapId(ledger);
        return "d="
                + depthOk
                + "|p="
                + structuredParseFailed
                + "|u="
                + userInputRequired
                + "|r="
                + readyToPost
                + "|dr="
                + dr.hashCode()
                + "|g="
                + (gap != null ? gap.hashCode() : 0);
    }

    public static String materialStateChangeFingerprint(Map<String, Object> signalState, FeaturePlanState plan) {
        String repo = shortHash(getString(signalState, "planningRepoEvidenceJson"));
        int asm = plan != null ? plan.getAssumptions().size() : 0;
        int crit = critiqueBlockingCount(plan);
        String draft = observabilityDraftFingerprint(plan);
        return "repo=" + repo + "|asm=" + asm + "|crit=" + crit + "|draft=" + draft;
    }

    private static boolean detectMaterialChange(
            Map<String, Object> baselineState,
            Map<String, Object> signalState,
            FeaturePlanState plan,
            boolean activeCycle) {
        if (!activeCycle) {
            return false;
        }
        if ("true".equalsIgnoreCase(getString(signalState, "planningJustMergedClarification"))) {
            return true;
        }
        String curRepo = shortHash(getString(signalState, "planningRepoEvidenceJson"));
        String baseRepo = getString(baselineState, BASELINE_REPO_HASH_KEY);
        if (baseRepo == null || baseRepo.isBlank()) {
            return true;
        }
        if (!Objects.equals(baseRepo, curRepo != null ? curRepo : "")) {
            return true;
        }
        int curAsm = plan != null ? plan.getAssumptions().size() : 0;
        int baseAsm = parseInt(getString(baselineState, BASELINE_ASSUMPTION_COUNT_KEY), -1);
        if (baseAsm < 0 || curAsm > baseAsm) {
            return true;
        }
        int curCrit = critiqueBlockingCount(plan);
        int baseCrit = parseInt(getString(baselineState, BASELINE_CRITIQUE_BLOCKING_KEY), -1);
        if (baseCrit < 0 || curCrit != baseCrit) {
            return true;
        }
        String curDraft = observabilityDraftFingerprint(plan);
        String baseDraft = getString(baselineState, BASELINE_DRAFT_FP_KEY);
        if (baseDraft != null
                && !baseDraft.isBlank()
                && curDraft != null
                && !curDraft.isBlank()
                && !baseDraft.equals(curDraft)) {
            return true;
        }
        return false;
    }

    private static String observabilityDraftFingerprint(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        String ex =
                com.vinekeepers.workflow.planreview.PlanningArtifactTexts.artifactField(
                        plan, "request_exploration", "analysis", "exploration_body");
        String syn =
                com.vinekeepers.workflow.planreview.PlanningArtifactTexts.artifactField(
                        plan, "requirements_spec", "narrative", "feature_summary");
        String blob = (ex != null ? ex : "") + "\n" + (syn != null ? syn : "");
        return String.valueOf(blob.trim().hashCode());
    }

    private static int critiqueBlockingCount(FeaturePlanState plan) {
        if (plan == null) {
            return 0;
        }
        PlanCritiqueSnapshot s = plan.getPlanCritiqueSnapshot();
        if (s == null) {
            return 0;
        }
        return s.getBlockingFindingCount();
    }

    private static String shortHash(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return String.valueOf(s.trim().hashCode());
    }

    private static String primaryOpenPlanningGapId(UnresolvedItemLedger ledger) {
        if (ledger == null) {
            return "";
        }
        for (UnresolvedItem it : ledger.items()) {
            if (it.getStatus() != UnresolvedItemStatus.OPEN) {
                continue;
            }
            if (!"planning_clarification".equals(it.getSource().get("channel"))) {
                continue;
            }
            String gid = it.getSource().get("gapId");
            if (gid != null && !gid.isBlank()) {
                return gid.trim();
            }
        }
        return "";
    }

    /** Exposed for the pipeline when forcing a single ask from ledger state. */
    public static String firstOpenPlanningQuestionTextOrEmpty(UnresolvedItemLedger ledger) {
        String q = firstOpenPlanningQuestion(ledger);
        return q != null ? q : "";
    }

    /**
     * Ledger-first (canonical clarification), then structured plan fields — not packet display text.
     */
    public static String firstUserFacingClarificationTextOrEmpty(
            UnresolvedItemLedger ledger, FeaturePlanState plan) {
        String fromLedger = firstOpenPlanningQuestionTextOrEmpty(ledger);
        if (!fromLedger.isBlank()) {
            return fromLedger;
        }
        return firstStructuredMaterialQuestion(plan);
    }

    /** True when unresolved questions, open blocking issues, or open high-severity assumptions need human input. */
    public static boolean hasStructuredMaterialPlanningGaps(FeaturePlanState plan) {
        return !firstStructuredMaterialQuestion(plan).isBlank();
    }

    /**
     * One concrete line derived from {@link FeaturePlanState} governance fields (not rendered packet bodies).
     */
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

    private static boolean synthesisAskFallbackAvailable(
            Map<String, Object> signalState, UnresolvedItemLedger ledger) {
        if (!"true".equalsIgnoreCase(getString(signalState, "planningLlmUserInputSuggested"))) {
            return false;
        }
        String q = firstOpenPlanningQuestionTextOrEmpty(ledger);
        if (q != null && !q.isBlank()) {
            return true;
        }
        String spreadQuestion = getString(signalState, "planningClarificationQuestionText");
        return spreadQuestion != null && !spreadQuestion.isBlank();
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

    private static double parseDouble(String s, double dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
