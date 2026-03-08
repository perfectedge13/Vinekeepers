package com.vinekeepers.workflow;

/**
 * High-level outcome of a workflow step.
 */
public enum StepOutcome {
    CONTINUE,
    WAITING,
    COMPLETE,
    ERROR
}
