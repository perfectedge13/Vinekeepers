package com.vinekeepers.workflow.v2;

import com.vinekeepers.workflow.template.WorkflowTemplatePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed workflowSchema v2 document. */
public final class WorkflowV2Model {

    private final String id;
    private final String entryPhase;
    private final Map<String, WorkflowV2PhaseModel> phases;
    private final Map<String, WorkflowV2CapabilityModel> capabilities;
    private final Map<String, List<Map<String, Object>>> rulesets;
    private final Map<String, Object> llm;
    /** Optional generic deliberation metadata (profile hints, phase names); does not hardcode planning semantics. */
    private final Map<String, Object> deliberation;
    private final WorkflowTemplatePolicy templatePolicy;

    public WorkflowV2Model(
            String id,
            String entryPhase,
            Map<String, WorkflowV2PhaseModel> phases,
            Map<String, WorkflowV2CapabilityModel> capabilities,
            Map<String, List<Map<String, Object>>> rulesets,
            Map<String, Object> llm,
            Map<String, Object> deliberation,
            WorkflowTemplatePolicy templatePolicy) {
        this.id = id != null ? id : "";
        this.entryPhase = entryPhase != null ? entryPhase : "";
        this.phases = phases != null && !phases.isEmpty() ? Map.copyOf(phases) : Map.of();
        this.capabilities = capabilities != null && !capabilities.isEmpty() ? Map.copyOf(capabilities) : Map.of();
        this.rulesets = rulesets != null && !rulesets.isEmpty() ? Map.copyOf(rulesets) : Map.of();
        this.llm = llm != null && !llm.isEmpty() ? Map.copyOf(llm) : Map.of();
        this.deliberation = deliberation != null && !deliberation.isEmpty() ? Map.copyOf(deliberation) : Map.of();
        this.templatePolicy = templatePolicy != null ? templatePolicy : WorkflowTemplatePolicy.LEGACY_FULL_STATE;
    }

    public String getId() {
        return id;
    }

    public String getEntryPhase() {
        return entryPhase;
    }

    public Map<String, WorkflowV2PhaseModel> getPhases() {
        return phases;
    }

    public Map<String, WorkflowV2CapabilityModel> getCapabilities() {
        return capabilities;
    }

    public Map<String, List<Map<String, Object>>> getRulesets() {
        return rulesets;
    }

    public Map<String, Object> getLlm() {
        return llm;
    }

    public Map<String, Object> getDeliberation() {
        return deliberation;
    }

    public WorkflowTemplatePolicy getTemplatePolicy() {
        return templatePolicy;
    }
}
