package com.vinekeepers.workflow.v2;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative capability referencing a legacy workflow action (migration bridge).
 */
public final class WorkflowV2CapabilityModel {

    private final String id;
    private final String kind;
    private final String action;
    private final boolean storeSpread;
    private final Map<String, Object> bind;

    public WorkflowV2CapabilityModel(
            String id, String kind, String action, boolean storeSpread, Map<String, Object> bind) {
        this.id = id != null ? id : "";
        this.kind = kind != null ? kind : "";
        this.action = action != null ? action : "";
        this.storeSpread = storeSpread;
        this.bind = bind != null && !bind.isEmpty() ? Map.copyOf(bind) : Map.of();
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

    public boolean isStoreSpread() {
        return storeSpread;
    }

    public Map<String, Object> getBind() {
        return bind;
    }

    @SuppressWarnings("unchecked")
    public static WorkflowV2CapabilityModel fromMap(String id, Map<String, Object> m) {
        if (m == null) {
            return new WorkflowV2CapabilityModel(id, "", "", false, Map.of());
        }
        String kind = m.get("kind") != null ? m.get("kind").toString() : "";
        String action = m.get("action") != null ? m.get("action").toString() : "";
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
        return new WorkflowV2CapabilityModel(id, kind, action, spread, bind);
    }
}
