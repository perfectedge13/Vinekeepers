package com.vinekeepers.profile;

/**
 * Clarification transport mode: retained legacy transport vs canonical evaluation-backed transport.
 */
public enum CoordinatorClarificationMode {
    /** Prior transport retained only for compatibility. */
    LEGACY,
    /** Canonical plan gaps plus evaluation-backed routing drive the clarification path. */
    CANONICAL_V1
}
