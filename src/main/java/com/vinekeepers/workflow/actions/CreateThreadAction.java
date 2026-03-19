package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.CreateThreadRequest;
import com.vinekeepers.connectors.CreateThreadResult;
import com.vinekeepers.connectors.SpaceOperations;
import com.vinekeepers.connectors.SpaceOperationsRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.WorkflowCapabilitySupport;

import java.util.Map;

/**
 * Workflow action: create a thread via the SpaceOperations capability for the event's source.
 * Resolves capability by source prefix (e.g. "discord"). Returns thread id or THREAD_CREATE_FAILED
 * on failure or unknown source. Fail-closed: null/blank prefix or unregistered prefix returns
 * THREAD_CREATE_FAILED (no implicit default).
 */
public final class CreateThreadAction implements com.vinekeepers.workflow.WorkflowAction {

    /** Sentinel returned when thread creation fails (e.g. for workflow branch or fallback to channel). */
    public static final String THREAD_CREATE_FAILED = "THREAD_CREATE_FAILED";

    private final SpaceOperationsRegistry registry;
    private final SpaceOperations testOverride;

    /** Production: resolve capability by source prefix from registry. */
    public CreateThreadAction(SpaceOperationsRegistry registry) {
        this.registry = registry;
        this.testOverride = null;
    }

    /** Test: use the given ops instead of registry lookup when non-null. */
    public CreateThreadAction(SpaceOperationsRegistry registry, SpaceOperations testOverride) {
        this.registry = registry;
        this.testOverride = testOverride;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        CreateThreadRequest request = CreateThreadRequest.from(event, state, bind);
        if (testOverride != null) {
            CreateThreadResult result = testOverride.createThread(request);
            return result.isSuccess() ? result.getThreadId() : THREAD_CREATE_FAILED;
        }
        String prefix = WorkflowCapabilitySupport.sourcePrefix(event);
        if (prefix == null || prefix.isBlank()) {
            return THREAD_CREATE_FAILED;
        }
        SpaceOperations ops = registry != null ? registry.get(prefix) : null;
        if (ops == null) {
            return THREAD_CREATE_FAILED;
        }
        CreateThreadResult result = ops.createThread(request);
        return result.isSuccess() ? result.getThreadId() : THREAD_CREATE_FAILED;
    }
}
