package com.vinekeepers.workflow.planning;

/**
 * Spread/session keys for silent synthesis pacing (not semantic clarification policy).
 */
public final class PlanningMaterialSpreadKeys {

    public static final String NOTICE_MARKDOWN_KEY = "planningSynthesisNoticeMarkdown";
    public static final String BASELINE_REPO_HASH_KEY = "planningMaterialBaselineRepoEvidenceHash";
    public static final String BASELINE_ASSUMPTION_COUNT_KEY = "planningMaterialBaselineAssumptionCount";
    public static final String BASELINE_CRITIQUE_BLOCKING_KEY = "planningMaterialBaselineCritiqueBlockingCount";
    public static final String BASELINE_DRAFT_FP_KEY = "planningMaterialBaselineDraftFingerprint";
    public static final String LAST_REVISION_SITUATION_KEY = "planningLastNonPostingSynthesisSituation";
    public static final String CONTINUE_SYNTHESIS_COUNT_KEY = "planningContinueSynthesisCount";

    /** {@link PlanningSynthesisAction#name()} from material pacing (edge serialization). */
    public static final String MATERIAL_ACTION_KEY = "planningMaterialAction";

    public static final String MATERIAL_NOTICE_MARKDOWN_KEY = "planningMaterialNoticeMarkdown";
    public static final String MATERIAL_FORCE_USER_INPUT_KEY = "planningMaterialForceUserInputRequired";
    public static final String MATERIAL_USER_INPUT_REQUIRED_KEY = "planningMaterialUserInputRequired";
    public static final String MATERIAL_READY_FOR_PACKET_KEY = "planningMaterialReadyForPacket";
    public static final String MATERIAL_REVISION_NEEDED_KEY = "planningMaterialRevisionNeeded";
    public static final String MATERIAL_PACKET_POSTING_ALLOWED_KEY = "planningMaterialPacketPostingAllowed";

    private PlanningMaterialSpreadKeys() {}
}
