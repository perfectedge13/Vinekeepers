package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;

import java.util.Map;

/**
 * Writes deterministic pacing and observability keys derived from the canonical evaluation result.
 */
public final class PlanningMaterialCyclePacing {

    private PlanningMaterialCyclePacing() {}

    /**
     * Aligns spread pacing keys with an already-persisted canonical decision (notice markdown preserved from spread).
     */
    public static void readinessResultAlignedWithCanonical(
            PlanningCanonicalDecision canonical, Map<String, Object> spread) {
        if (canonical == null || spread == null) {
            return;
        }
        PlanningSynthesisAction action =
                switch (canonical.nextAction()) {
                    case ASK_USER -> PlanningSynthesisAction.ASK_USER;
                    case READY_FOR_PACKET -> PlanningSynthesisAction.READY_FOR_PACKET;
                    case CONTINUE_SYNTHESIS -> PlanningSynthesisAction.BLOCK;
                    case BLOCK -> PlanningSynthesisAction.BLOCK;
                };
        boolean ask = canonical.nextAction() == PlanningCanonicalNextAction.ASK_USER;
        boolean readyPost =
                canonical.nextAction() == PlanningCanonicalNextAction.READY_FOR_PACKET
                        && canonical.packetPostingAllowed();
        boolean revisionNeeded = ask;
        boolean packetPostingAllowed =
                action == PlanningSynthesisAction.READY_FOR_PACKET || action == PlanningSynthesisAction.ASSUME_AND_CONTINUE;
        writeMaterialPacingKeys(
                spread,
                action,
                stringOrEmpty(spread.get(PlanningMaterialSpreadKeys.MATERIAL_NOTICE_MARKDOWN_KEY)),
                "true".equalsIgnoreCase(stringOrEmpty(spread.get(PlanningMaterialSpreadKeys.MATERIAL_FORCE_USER_INPUT_KEY))),
                ask,
                readyPost,
                revisionNeeded,
                packetPostingAllowed);
    }

    public static void writePersistenceKeys(
            Map<String, Object> spread,
            Map<String, Object> signalState,
            FeaturePlanState plan,
            boolean wantsRevision,
            String revisionSituationFingerprint) {
        if (spread == null) {
            return;
        }
        String repo = shortHash(getString(signalState, "planningRepoEvidenceJson"));
        int assumptionCount = plan != null ? plan.getAssumptions().size() : 0;
        int critiqueBlockingCount = PlanningMaterialFingerprint.critiqueBlockingCount(plan);
        String draftFingerprint = PlanningMaterialFingerprint.observabilityDraftFingerprint(plan);
        spread.put(PlanningMaterialSpreadKeys.BASELINE_REPO_HASH_KEY, repo != null ? repo : "");
        spread.put(PlanningMaterialSpreadKeys.BASELINE_ASSUMPTION_COUNT_KEY, String.valueOf(assumptionCount));
        spread.put(PlanningMaterialSpreadKeys.BASELINE_CRITIQUE_BLOCKING_KEY, String.valueOf(critiqueBlockingCount));
        spread.put(PlanningMaterialSpreadKeys.BASELINE_DRAFT_FP_KEY, draftFingerprint != null ? draftFingerprint : "");
        PlanningSynthesisAction action = parseMaterialAction(getString(spread, PlanningMaterialSpreadKeys.MATERIAL_ACTION_KEY));
        if (action == PlanningSynthesisAction.CONTINUE_SYNTHESIS) {
            int previous = parseInt(getString(signalState, PlanningMaterialSpreadKeys.CONTINUE_SYNTHESIS_COUNT_KEY), 0);
            spread.put(PlanningMaterialSpreadKeys.CONTINUE_SYNTHESIS_COUNT_KEY, String.valueOf(previous + 1));
        } else {
            spread.put(PlanningMaterialSpreadKeys.CONTINUE_SYNTHESIS_COUNT_KEY, "0");
        }
        if (action == PlanningSynthesisAction.READY_FOR_PACKET || !wantsRevision) {
            spread.put(PlanningMaterialSpreadKeys.LAST_REVISION_SITUATION_KEY, "");
        } else if (revisionSituationFingerprint != null && !revisionSituationFingerprint.isBlank()) {
            spread.put(PlanningMaterialSpreadKeys.LAST_REVISION_SITUATION_KEY, revisionSituationFingerprint);
        }
    }

    static void writeMaterialPacingKeys(
            Map<String, Object> spread,
            PlanningSynthesisAction action,
            String noticeMarkdown,
            boolean forceUserInputRequired,
            boolean userInputRequired,
            boolean readyToPostPacket,
            boolean revisionNeeded,
            boolean packetPostingAllowed) {
        if (spread == null) {
            return;
        }
        spread.put(
                PlanningMaterialSpreadKeys.MATERIAL_ACTION_KEY,
                action != null ? action.name() : PlanningSynthesisAction.BLOCK.name());
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_NOTICE_MARKDOWN_KEY, noticeMarkdown != null ? noticeMarkdown : "");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_FORCE_USER_INPUT_KEY, forceUserInputRequired ? "true" : "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_USER_INPUT_REQUIRED_KEY, userInputRequired ? "true" : "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_READY_FOR_PACKET_KEY, readyToPostPacket ? "true" : "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_REVISION_NEEDED_KEY, revisionNeeded ? "true" : "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_PACKET_POSTING_ALLOWED_KEY, packetPostingAllowed ? "true" : "false");
    }

    static PlanningSynthesisAction parseMaterialAction(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlanningSynthesisAction.BLOCK;
        }
        try {
            return PlanningSynthesisAction.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return PlanningSynthesisAction.BLOCK;
        }
    }

    private static String stringOrEmpty(Object o) {
        return o != null ? o.toString() : "";
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

}
