package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationEnginePolicy;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.util.Map;

/**
 * Autonomous drafting / synthesis / depth pacing for a planning cycle. Production uses this class for material routing;
 * {@link PlanningPostDraftGovernor} supplies only legacy {@link PlanningPostDraftGovernor.LoopOutcome} / {@link
 * PlanningPostDraftGovernor.Result} shapes for tests and routing records.
 */
public final class PlanningMaterialCyclePacing {

    private PlanningMaterialCyclePacing() {}

    /**
     * Canonical planning spine: material/synthesis loop pacing with {@code canonicalV1} rules (ledger-backed ask only when
     * {@code canonicalGapAuthorizesAsk}).
     */
    public static PlanningMaterialRoutingOutcome canonicalPlanningCycleOutcome(
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
            CoordinatorClarificationEnginePolicy policy,
            boolean canonicalGapAuthorizesAsk) {
        return deriveCore(
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
                policy,
                true,
                canonicalGapAuthorizesAsk);
    }

    public static PlanningMaterialRoutingOutcome readinessResultAlignedWithCanonical(
            PlanningCanonicalDecision canonical, PlanningMaterialRoutingOutcome governorBase) {
        if (canonical == null || governorBase == null) {
            return governorBase;
        }
        PlanningPostDraftAction action =
                switch (canonical.nextAction()) {
                    case ASK_ONE_QUESTION -> PlanningPostDraftAction.ASK_ONE_QUESTION;
                    case POST_PACKET -> PlanningPostDraftAction.POST_PACKET;
                    case AUTONOMOUS_REDRAFT -> PlanningPostDraftAction.AUTONOMOUS_REDRAFT;
                    case BLOCK -> PlanningPostDraftAction.BLOCK;
                };
        PlanningPostDraftGovernor.LoopOutcome outcome = outcomeFor(action);
        boolean ask = canonical.nextAction() == com.vinekeepers.state.planning.PlanningCanonicalNextAction.ASK_ONE_QUESTION;
        boolean readyPost =
                canonical.nextAction() == com.vinekeepers.state.planning.PlanningCanonicalNextAction.POST_PACKET
                        && canonical.packetPostingAllowed();
        String phase =
                switch (canonical.nextAction()) {
                    case ASK_ONE_QUESTION -> "WAITING_FOR_CLARIFICATION";
                    case POST_PACKET -> "READY_FOR_REVIEW";
                    case AUTONOMOUS_REDRAFT -> "REVISING";
                    case BLOCK -> "FAILED";
                };
        boolean revisionNeeded =
                ask || canonical.nextAction() == com.vinekeepers.state.planning.PlanningCanonicalNextAction.AUTONOMOUS_REDRAFT;
        return new PlanningMaterialRoutingOutcome(
                action,
                outcome,
                governorBase.noticeMarkdown(),
                governorBase.forceUserInputRequired(),
                ask,
                readyPost,
                phase,
                revisionNeeded);
    }

    public static void writePersistenceKeys(
            Map<String, Object> spread,
            Map<String, Object> signalState,
            FeaturePlanState plan,
            PlanningMaterialRoutingOutcome result,
            boolean wantsRevision,
            String revisionSituationFingerprint) {
        if (spread == null) {
            return;
        }
        String repo = shortHash(getString(signalState, "planningRepoEvidenceJson"));
        int asm = plan != null ? plan.getAssumptions().size() : 0;
        int critBlock = PlanningMaterialFingerprint.critiqueBlockingCount(plan);
        String draftFp = PlanningMaterialFingerprint.observabilityDraftFingerprint(plan);
        spread.put(PlanningMaterialSpreadKeys.BASELINE_REPO_HASH_KEY, repo != null ? repo : "");
        spread.put(PlanningMaterialSpreadKeys.BASELINE_ASSUMPTION_COUNT_KEY, String.valueOf(asm));
        spread.put(PlanningMaterialSpreadKeys.BASELINE_CRITIQUE_BLOCKING_KEY, String.valueOf(critBlock));
        spread.put(PlanningMaterialSpreadKeys.BASELINE_DRAFT_FP_KEY, draftFp != null ? draftFp : "");
        PlanningPostDraftAction action = result != null ? result.action() : null;
        if (action == PlanningPostDraftAction.AUTONOMOUS_REDRAFT) {
            int prev = parseInt(getString(signalState, PlanningMaterialSpreadKeys.AUTONOMOUS_REDRAFT_COUNT_KEY), 0);
            spread.put(PlanningMaterialSpreadKeys.AUTONOMOUS_REDRAFT_COUNT_KEY, String.valueOf(prev + 1));
        } else {
            spread.put(PlanningMaterialSpreadKeys.AUTONOMOUS_REDRAFT_COUNT_KEY, "0");
        }
        if ((result != null && result.outcome() == PlanningPostDraftGovernor.LoopOutcome.POST) || !wantsRevision) {
            spread.put(PlanningMaterialSpreadKeys.LAST_REVISION_SITUATION_KEY, "");
        } else if (revisionSituationFingerprint != null && !revisionSituationFingerprint.isBlank()) {
            spread.put(PlanningMaterialSpreadKeys.LAST_REVISION_SITUATION_KEY, revisionSituationFingerprint);
        }
    }

    static PlanningMaterialRoutingOutcome deriveLegacy(
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
            CoordinatorClarificationEnginePolicy policy,
            boolean canonicalV1,
            boolean canonicalGapAuthorizesAsk) {
        return deriveCore(
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
                policy,
                canonicalV1,
                canonicalGapAuthorizesAsk);
    }

    private static PlanningMaterialRoutingOutcome deriveCore(
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
            CoordinatorClarificationEnginePolicy policy,
            boolean canonicalV1,
            boolean canonicalGapAuthorizesAsk) {
        String depth = depthReason != null ? depthReason : "";
        String cyc = cycleError != null ? cycleError : "";
        boolean wantsRevision = !readyToPost && !userInputRequired;
        String synthCat = synthesisFailureCategory != null ? synthesisFailureCategory.trim() : "";
        PlanningFailureCategory synthFailure = PlanningFailureCategory.parse(synthCat);
        CoordinatorClarificationEnginePolicy loopPolicy =
                policy != null ? policy : CoordinatorClarificationEnginePolicy.defaultPolicy();
        int autonomousRedraftCount = parseInt(getString(persistedSessionState, PlanningMaterialSpreadKeys.AUTONOMOUS_REDRAFT_COUNT_KEY), 0);
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
                PlanningMaterialFingerprint.detectMaterialChange(
                        persistedSessionState,
                        signalState,
                        plan,
                        wantsRevision || userInputRequired || readyToPost);
        String situation =
                PlanningMaterialFingerprint.revisionSituationFingerprint(
                        depthOk, structuredParseFailed, depth, userInputRequired, readyToPost, ledger);
        String lastSituation = getString(persistedSessionState, PlanningMaterialSpreadKeys.LAST_REVISION_SITUATION_KEY);
        boolean sameNonPostingSituation =
                wantsRevision
                        && situation != null
                        && !situation.isBlank()
                        && situation.equals(lastSituation != null ? lastSituation : "");

        if (hardClarificationBlock) {
            String q = PlanningPostDraftGovernor.firstUserFacingClarificationTextOrEmpty(ledger, plan);
            if (!q.isBlank() && !canonicalV1) {
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
                            + "unblock scope or relax constraints before we can continue.",
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
                String q = PlanningPostDraftGovernor.firstUserFacingClarificationTextOrEmpty(ledger, plan);
                if (!q.isBlank() && (!canonicalV1 || canonicalGapAuthorizesAsk)) {
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

        if (userInputRequired) {
            if (!canonicalV1) {
                return resultFor(PlanningPostDraftAction.ASK_ONE_QUESTION, "", false);
            }
            if (canonicalGapAuthorizesAsk) {
                return resultFor(PlanningPostDraftAction.ASSUME_AND_CONTINUE, "", false);
            }
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

    private static PlanningMaterialRoutingOutcome resultFor(
            PlanningPostDraftAction action, String noticeMarkdown, boolean forceUserInputRequired) {
        PlanningPostDraftGovernor.LoopOutcome outcome = outcomeFor(action);
        boolean effectiveUser = outcome == PlanningPostDraftGovernor.LoopOutcome.ASK || forceUserInputRequired;
        boolean readyToPostPacket = outcome == PlanningPostDraftGovernor.LoopOutcome.POST;
        String phase =
                switch (outcome) {
                    case ASK -> "WAITING_FOR_CLARIFICATION";
                    case REDRAFT -> "REVISING";
                    case POST, ASSUME -> "READY_FOR_REVIEW";
                    case BLOCK -> "FAILED";
                };
        boolean revisionNeeded =
                outcome == PlanningPostDraftGovernor.LoopOutcome.ASK
                        || outcome == PlanningPostDraftGovernor.LoopOutcome.REDRAFT;
        return new PlanningMaterialRoutingOutcome(
                action,
                outcome,
                noticeMarkdown != null ? noticeMarkdown : "",
                forceUserInputRequired,
                effectiveUser,
                readyToPostPacket,
                phase,
                revisionNeeded);
    }

    private static PlanningPostDraftGovernor.LoopOutcome outcomeFor(PlanningPostDraftAction action) {
        if (action == null) {
            return PlanningPostDraftGovernor.LoopOutcome.BLOCK;
        }
        return switch (action) {
            case ASK_ONE_QUESTION -> PlanningPostDraftGovernor.LoopOutcome.ASK;
            case AUTONOMOUS_REDRAFT -> PlanningPostDraftGovernor.LoopOutcome.REDRAFT;
            case POST_PACKET -> PlanningPostDraftGovernor.LoopOutcome.POST;
            case ASSUME_AND_CONTINUE -> PlanningPostDraftGovernor.LoopOutcome.ASSUME;
            case BLOCK -> PlanningPostDraftGovernor.LoopOutcome.BLOCK;
        };
    }

    private static String shortHash(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return String.valueOf(s.trim().hashCode());
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
