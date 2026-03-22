package com.vinekeepers.workflow.v2;

import com.vinekeepers.workflow.template.WorkflowTemplatePolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link WorkflowV2Model} from a workflow YAML map ({@code workflowSchema: v2}).
 */
public final class WorkflowV2Loader {

    private WorkflowV2Loader() {}

    @SuppressWarnings("unchecked")
    public static WorkflowV2Model load(String workflowId, Map<String, Object> root) {
        if (root == null) {
            return new WorkflowV2Model(workflowId, "", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    WorkflowTemplatePolicy.LEGACY_FULL_STATE);
        }
        String entry = root.get("entryPhase") != null ? root.get("entryPhase").toString().trim() : "";
        Map<String, WorkflowV2PhaseModel> phases = new LinkedHashMap<>();
        Object ph = root.get("phases");
        if (ph instanceof Map<?, ?> pm) {
            for (Map.Entry<?, ?> e : pm.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String pid = e.getKey().toString();
                if (e.getValue() instanceof Map<?, ?> vm) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> ie : vm.entrySet()) {
                        if (ie.getKey() != null) {
                            mm.put(ie.getKey().toString(), ie.getValue());
                        }
                    }
                    phases.put(pid, WorkflowV2PhaseModel.fromMap(pid, mm));
                }
            }
        }
        if (entry.isEmpty() && !phases.isEmpty()) {
            entry = phases.keySet().iterator().next();
        }
        Map<String, WorkflowV2CapabilityModel> caps = new LinkedHashMap<>();
        Object c = root.get("capabilities");
        if (c instanceof Map<?, ?> cm) {
            for (Map.Entry<?, ?> e : cm.entrySet()) {
                if (e.getKey() == null || !(e.getValue() instanceof Map)) {
                    continue;
                }
                String cid = e.getKey().toString();
                Map<String, Object> mm = new LinkedHashMap<>();
                for (Map.Entry<?, ?> ie : ((Map<?, ?>) e.getValue()).entrySet()) {
                    if (ie.getKey() != null) {
                        mm.put(ie.getKey().toString(), ie.getValue());
                    }
                }
                caps.put(cid, WorkflowV2CapabilityModel.fromMap(cid, mm));
            }
        }
        Map<String, List<Map<String, Object>>> rulesets = new LinkedHashMap<>();
        Object rs = root.get("rulesets");
        if (rs instanceof Map<?, ?> rsm) {
            for (Map.Entry<?, ?> e : rsm.entrySet()) {
                if (e.getKey() == null || !(e.getValue() instanceof List)) {
                    continue;
                }
                String name = e.getKey().toString();
                List<Map<String, Object>> rules = new ArrayList<>();
                for (Object row : (List<?>) e.getValue()) {
                    if (row instanceof Map<?, ?> rm) {
                        Map<String, Object> mm = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> ie : rm.entrySet()) {
                            if (ie.getKey() != null) {
                                mm.put(ie.getKey().toString(), ie.getValue());
                            }
                        }
                        rules.add(mm);
                    }
                }
                rulesets.put(name, List.copyOf(rules));
            }
        }
        Map<String, Object> llm = new LinkedHashMap<>();
        Object llmObj = root.get("llm");
        if (llmObj instanceof Map<?, ?> lm) {
            for (Map.Entry<?, ?> e : lm.entrySet()) {
                if (e.getKey() != null) {
                    llm.put(e.getKey().toString(), e.getValue());
                }
            }
        }
        Map<String, Object> deliberation = new LinkedHashMap<>();
        Object delObj = root.get("deliberation");
        if (delObj instanceof Map<?, ?> dm) {
            for (Map.Entry<?, ?> e : dm.entrySet()) {
                if (e.getKey() != null) {
                    deliberation.put(e.getKey().toString(), e.getValue());
                }
            }
        }
        return new WorkflowV2Model(
                workflowId,
                entry,
                phases,
                caps,
                rulesets,
                llm,
                deliberation,
                WorkflowTemplatePolicy.fromYaml(root.get("templates")));
    }
}
