package com.vinekeepers.workflow.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative capability referencing a legacy workflow action (migration bridge), a sibling linear workflow, or inline
 * configurable steps ({@code configurable_steps}).
 */
public final class WorkflowV2CapabilityModel {

    private final String id;
    private final String kind;
    private final String action;
    /** Referenced linear workflow id from the bot YAML {@code workflows} map ({@code linear_workflow_ref}). */
    private final String workflowRef;
    private final boolean storeSpread;
    private final Map<String, Object> bind;
    /** Inline linear-style steps for {@code configurable_steps} (same shape as v1 {@code steps}). */
    private final List<Map<String, Object>> steps;

    public WorkflowV2CapabilityModel(
            String id, String kind, String action, String workflowRef, boolean storeSpread, Map<String, Object> bind) {
        this(id, kind, action, workflowRef, storeSpread, bind, List.of());
    }

    public WorkflowV2CapabilityModel(
            String id,
            String kind,
            String action,
            String workflowRef,
            boolean storeSpread,
            Map<String, Object> bind,
            List<Map<String, Object>> steps) {
        this.id = id != null ? id : "";
        this.kind = kind != null ? kind : "";
        this.action = action != null ? action : "";
        this.workflowRef = workflowRef != null ? workflowRef : "";
        this.storeSpread = storeSpread;
        this.bind = bind != null && !bind.isEmpty() ? Map.copyOf(bind) : Map.of();
        this.steps = steps != null && !steps.isEmpty() ? List.copyOf(steps) : List.of();
    }

    public String getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getAction() {
        return action;
    }

    public String getWorkflowRef() {
        return workflowRef;
    }

    public boolean isStoreSpread() {
        return storeSpread;
    }

    public Map<String, Object> getBind() {
        return bind;
    }

    public List<Map<String, Object>> getSteps() {
        return steps;
    }

    public static WorkflowV2CapabilityModel fromMap(String id, Map<String, Object> m) {
        if (m == null) {
            return new WorkflowV2CapabilityModel(id, "", "", "", false, Map.of(), List.of());
        }
        String kind = m.get("kind") != null ? m.get("kind").toString() : "";
        String action = m.get("action") != null ? m.get("action").toString() : "";
        String workflowRef = m.get("workflowRef") != null ? m.get("workflowRef").toString().trim() : "";
        boolean spread =
                Boolean.TRUE.equals(m.get("storeSpread"))
                        || "true".equalsIgnoreCase(String.valueOf(m.get("storeSpread")));
        Object b = m.get("bind");
        Map<String, Object> bind = new LinkedHashMap<>();
        if (b instanceof Map<?, ?> bm) {
            for (Map.Entry<?, ?> e : bm.entrySet()) {
                if (e.getKey() != null) {
                    bind.put(e.getKey().toString(), e.getValue());
                }
            }
        }
        List<Map<String, Object>> steps = parseStepsList(m.get("steps"));
        return new WorkflowV2CapabilityModel(id, kind, action, workflowRef, spread, bind, steps);
    }

    private static List<Map<String, Object>> parseStepsList(Object stepsObj) {
        if (!(stepsObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> mm = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : row.entrySet()) {
                if (e.getKey() != null) {
                    mm.put(e.getKey().toString(), e.getValue());
                }
            }
            out.add(mm);
        }
        return List.copyOf(out);
    }
}
