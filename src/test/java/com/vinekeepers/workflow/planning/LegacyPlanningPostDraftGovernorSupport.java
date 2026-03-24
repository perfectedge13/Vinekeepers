package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationEnginePolicy;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;

import java.util.Map;

/** Test-only bridge to {@link PlanningMaterialCyclePacing#deriveLegacy} for {@link PlanningPostDraftGovernor.Result} shape. */
public final class LegacyPlanningPostDraftGovernorSupport {

    private LegacyPlanningPostDraftGovernorSupport() {}

    public static PlanningPostDraftGovernor.Result derive(
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
        return PlanningMaterialCyclePacing.deriveLegacy(
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
                        CoordinatorClarificationEnginePolicy.defaultPolicy(),
                        false,
                        true)
                .toLegacyResult();
    }

    public static PlanningPostDraftGovernor.Result derive(
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
        return PlanningMaterialCyclePacing.deriveLegacy(
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
                        false,
                        true)
                .toLegacyResult();
    }

    public static PlanningPostDraftGovernor.Result derive(
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
        return PlanningMaterialCyclePacing.deriveLegacy(
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
                        canonicalGapAuthorizesAsk)
                .toLegacyResult();
    }
}
