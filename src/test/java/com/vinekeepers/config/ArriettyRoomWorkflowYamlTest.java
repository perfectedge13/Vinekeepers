package com.vinekeepers.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import com.vinekeepers.workflow.WorkflowLlmActions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@code arrietty_room} branch {@code next} indices against the compiled step list (repo YAML).
 */
class ArriettyRoomWorkflowYamlTest {

    @Test
    void arriettyRoom_branchNextIndicesAreInRange() throws Exception {
        Path yamlPath = Path.of("config", "bots.yaml");
        assertTrue(Files.exists(yamlPath), "config/bots.yaml missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(yamlPath));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        assertTrue(workflows != null && workflows.containsKey("arrietty_room"), "arrietty_room workflow missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get("arrietty_room");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) room.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "arrietty_room has no steps");
        int n = steps.size();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!"branch".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> branches = (List<Map<String, Object>>) step.get("branches");
            if (branches == null) {
                continue;
            }
            for (Map<String, Object> b : branches) {
                Object nextObj = b.get("next");
                if (nextObj instanceof Number num) {
                    int next = num.intValue();
                    assertTrue(
                            next >= 0 && next < n,
                            "Step " + i + " branch next=" + next + " out of range [0," + (n - 1) + "]");
                }
            }
        }
    }

    @Test
    void arriettyRoom_callActionLlmOnlyOnCapableActions() throws Exception {
        Path yamlPath = Path.of("config", "bots.yaml");
        assertTrue(Files.exists(yamlPath), "config/bots.yaml missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(yamlPath));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get("arrietty_room");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) room.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "arrietty_room has no steps");
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!"call_action".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            if (!step.containsKey("llm")) {
                continue;
            }
            String action = String.valueOf(step.get("action"));
            assertTrue(
                    WorkflowLlmActions.isLlmCapable(action),
                    "Step " + i + " has llm block but action " + action + " is not LLM-capable");
        }
    }
}
