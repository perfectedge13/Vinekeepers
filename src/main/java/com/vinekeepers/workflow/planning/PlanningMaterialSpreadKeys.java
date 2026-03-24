package com.vinekeepers.workflow.planning;

/**
 * Spread/session keys for autonomous drafting and material pacing (not clarification policy).
 */
public final class PlanningMaterialSpreadKeys {

    public static final String NOTICE_MARKDOWN_KEY = "planningPostDraftNoticeMarkdown";
    public static final String BASELINE_REPO_HASH_KEY = "planningMaterialBaselineRepoEvidenceHash";
    public static final String BASELINE_ASSUMPTION_COUNT_KEY = "planningMaterialBaselineAssumptionCount";
    public static final String BASELINE_CRITIQUE_BLOCKING_KEY = "planningMaterialBaselineCritiqueBlockingCount";
    public static final String BASELINE_DRAFT_FP_KEY = "planningMaterialBaselineDraftFingerprint";
    public static final String LAST_REVISION_SITUATION_KEY = "planningLastNonPostingRevisionSituation";
    public static final String AUTONOMOUS_REDRAFT_COUNT_KEY = "planningAutonomousRedraftCount";

    private PlanningMaterialSpreadKeys() {}
}
