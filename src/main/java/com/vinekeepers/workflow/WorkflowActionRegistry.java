package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named workflow actions for CallActionStep.
 */
public final class WorkflowActionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkflowActionRegistry.class);

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
            return action.run(event, snapshot(state), snapshot(bind));
        } catch (Exception e) {
            String actionId = id != null && !id.isBlank() ? id : "<unknown>";
            String detail = summarizeException(e);
            log.warn("Workflow action failed: actionId={} detail={}", actionId, detail, e);
            throw new IllegalStateException("Workflow action '" + actionId + "' failed: " + detail, e);
        }
    }

    private static Map<String, Object> snapshot(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    private static String summarizeException(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        String simple = error.getClass().getSimpleName();
        return simple != null && !simple.isBlank() ? simple : error.getClass().getName();
    }
}
