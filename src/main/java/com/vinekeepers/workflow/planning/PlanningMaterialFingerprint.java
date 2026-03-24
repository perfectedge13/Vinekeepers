package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanCritiqueSnapshot;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;

import java.util.Map;
import java.util.Objects;

/**
 * Deterministic material / revision fingerprints for planning observability. No post-draft policy or ask routing.
 */
public final class PlanningMaterialFingerprint {

    private PlanningMaterialFingerprint() {}

    public static String materialStateChangeFingerprint(Map<String, Object> signalState, FeaturePlanState plan) {
        String repo = shortHash(getString(signalState, "planningRepoEvidenceJson"));
        int asm = plan != null ? plan.getAssumptions().size() : 0;
        int crit = critiqueBlockingCount(plan);
        String draft = observabilityDraftFingerprint(plan);
        return "repo=" + repo + "|asm=" + asm + "|crit=" + crit + "|draft=" + draft;
    }

    public static String revisionSituationFingerprint(
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

    private static String primaryOpenPlanningGapId(UnresolvedItemLedger ledger) {
        if (ledger == null) {
            return "";
        }
        for (var it : ledger.items()) {
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

    public static String observabilityDraftFingerprint(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        String ex = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        String syn = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        String blob = (ex != null ? ex : "") + "\n" + (syn != null ? syn : "");
        return String.valueOf(blob.trim().hashCode());
    }

    public static int critiqueBlockingCount(FeaturePlanState plan) {
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

    private static String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    /** Material change vs persisted baselines (used by post-draft loop pacing). */
    public static boolean detectMaterialChange(
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
        String baseRepo = getString(baselineState, PlanningMaterialSpreadKeys.BASELINE_REPO_HASH_KEY);
        if (baseRepo == null || baseRepo.isBlank()) {
            return true;
        }
        if (!Objects.equals(baseRepo, curRepo != null ? curRepo : "")) {
            return true;
        }
        int curAsm = plan != null ? plan.getAssumptions().size() : 0;
        int baseAsm = parseInt(getString(baselineState, PlanningMaterialSpreadKeys.BASELINE_ASSUMPTION_COUNT_KEY), -1);
        if (baseAsm < 0 || curAsm > baseAsm) {
            return true;
        }
        int curCrit = critiqueBlockingCount(plan);
        int baseCrit = parseInt(getString(baselineState, PlanningMaterialSpreadKeys.BASELINE_CRITIQUE_BLOCKING_KEY), -1);
        if (baseCrit < 0 || curCrit != baseCrit) {
            return true;
        }
        String curDraft = observabilityDraftFingerprint(plan);
        String baseDraft = getString(baselineState, PlanningMaterialSpreadKeys.BASELINE_DRAFT_FP_KEY);
        if (baseDraft != null
                && !baseDraft.isBlank()
                && curDraft != null
                && !curDraft.isBlank()
                && !baseDraft.equals(curDraft)) {
            return true;
        }
        return false;
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
