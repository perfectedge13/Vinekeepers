package com.vinekeepers.state.planning;

/**
 * Durable coordinator intake phase for a feature plan. Persisted on {@link FeaturePlanState}; authoritative
 * for orchestration (not YAML step index).
 */
public enum PlanningIntakeStage {
    /** Initial exploration and optional scope capture before autonomous drafting. */
    GATHERING_CONTEXT,
    /** Waiting on a blocking clarification (plain-text Q/A). */
    CLARIFYING,
    /** Running planning cycles / drafts; no mandatory user ceremony. */
    DRAFTING,
    /** Planning packet has been posted to the intake thread. */
    PACKET_POSTED,
    /** Critique snapshot being produced or refreshed. */
    CRITIQUING,
    /** Readiness evaluation; may need human proceed/revise (optional buttons). */
    READINESS_GATE,
    /** Waiting on approve / revise / reject. */
    AWAITING_APPROVAL,
    /** Launch in flight. */
    LAUNCHING,
    /** Planning launch completed for this thread. */
    DONE,
    /** Terminal failure; user must follow recovery guidance. */
    FAILED
}
