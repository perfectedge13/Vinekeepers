package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named workflow actions for CallActionStep.
 */
public final class WorkflowActionRegistry {

    private final Map<String, WorkflowAction> actions = new ConcurrentHashMap<>();

    public void register(String id, WorkflowAction action) {
        if (id != null && !id.isBlank() && action != null) {
            actions.put(id, action);
        }
    }

    public Optional<WorkflowAction> resolve(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    /**
     * Run an action by id; returns null if not found or on error.
     */
    public Object run(String id, Event event, Map<String, Object> state, Map<String, Object> bind) {
        WorkflowAction action = actions.get(id);
        if (action == null) return null;
        try {
            return action.run(event, state != null ? Map.copyOf(state) : Map.of(),
                    bind != null ? Map.copyOf(bind) : Map.of());
        } catch (Exception e) {
            return null;
        }
    }
}
