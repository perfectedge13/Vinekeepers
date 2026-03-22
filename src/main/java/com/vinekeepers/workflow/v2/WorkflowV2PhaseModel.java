package com.vinekeepers.workflow.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Phase: ordered capability ids and optional ruleset for convergence. */
public final class WorkflowV2PhaseModel {

    private final String id;
    private final List<String> pipeline;
    private final String rulesRef;
    private final String defaultNextPhase;
    private final boolean terminal;

    public WorkflowV2PhaseModel(
            String id, List<String> pipeline, String rulesRef, String defaultNextPhase, boolean terminal) {
        this.id = id != null ? id : "";
        this.pipeline = pipeline != null ? List.copyOf(pipeline) : List.of();
        this.rulesRef = rulesRef != null ? rulesRef : "";
        this.defaultNextPhase = defaultNextPhase != null ? defaultNextPhase : "";
        this.terminal = terminal;
    }

    public String getId() {
        return id;
    }

    public List<String> getPipeline() {
        return pipeline;
    }

    public String getRulesRef() {
        return rulesRef;
    }

    public String getDefaultNextPhase() {
        return defaultNextPhase;
    }

    public boolean isTerminal() {
        return terminal;
    }

    @SuppressWarnings("unchecked")
    public static WorkflowV2PhaseModel fromMap(String phaseId, Map<String, Object> m) {
        if (m == null) {
            return new WorkflowV2PhaseModel(phaseId, List.of(), "", "", false);
        }
        List<String> pipe = new ArrayList<>();
        Object p = m.get("pipeline");
        if (p instanceof List<?> pl) {
            for (Object o : pl) {
                if (o != null) {
                    pipe.add(o.toString());
                }
            }
        }
        String rulesRef = m.get("rulesRef") != null ? m.get("rulesRef").toString() : "";
        String next = m.get("defaultNextPhase") != null ? m.get("defaultNextPhase").toString() : "";
        boolean term = Boolean.TRUE.equals(m.get("terminal"))
                || "true".equalsIgnoreCase(String.valueOf(m.get("terminal")));
        return new WorkflowV2PhaseModel(phaseId, pipe, rulesRef, next, term);
    }
}
