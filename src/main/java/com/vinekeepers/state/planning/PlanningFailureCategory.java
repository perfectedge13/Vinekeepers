package com.vinekeepers.state.planning;

/**
 * Machine-stable planner / synthesis failure classification for recovery routing and user-facing copy.
 */
public enum PlanningFailureCategory {
    /** No failure or not classified. */
    NONE,
    SYNTHESIS_TRANSPORT_ERROR,
    SYNTHESIS_JSON_INVALID,
    SYNTHESIS_REPAIR_EXHAUSTED,
    SYNTHESIS_UPSERT_REJECTED,
    SYNTHESIS_EMPTY_NOOP,
    REPO_GROUNDING_UNAVAILABLE;

    public static PlanningFailureCategory parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return PlanningFailureCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    public String wireName() {
        return this == NONE ? "" : name();
    }
}
