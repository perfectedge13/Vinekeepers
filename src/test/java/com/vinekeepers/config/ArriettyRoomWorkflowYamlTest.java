package com.vinekeepers.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import com.vinekeepers.workflow.WorkflowLlmActions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@code arrietty_room_legacy} branch {@code next} indices against the compiled step list (repo YAML).
 */
class ArriettyRoomWorkflowYamlTest {

    private static final String ARRIETTY_LINEAR_ID = "arrietty_room_legacy";

    @Test
    void arriettyRoom_branchNextIndicesAreInRange() throws Exception {
        Path yamlPath = Path.of("config", "bots.yaml");
        assertTrue(Files.exists(yamlPath), "config/bots.yaml missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(yamlPath));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        assertTrue(
                workflows != null && workflows.containsKey(ARRIETTY_LINEAR_ID),
                "arrietty_room_legacy workflow missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_LINEAR_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) room.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "arrietty_room_legacy has no steps");
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
    void arriettyRoom_branchNextMustNotTargetDoneSteps() throws Exception {
        Path yamlPath = Path.of("config", "bots.yaml");
        assertTrue(Files.exists(yamlPath), "config/bots.yaml missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(yamlPath));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_LINEAR_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) room.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "arrietty_room_legacy has no steps");
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
                if (!(nextObj instanceof Number)) {
                    continue;
                }
                int next = ((Number) nextObj).intValue();
                if (next < 0 || next >= n) {
                    continue;
                }
                String targetType = String.valueOf(steps.get(next).get("type"));
                assertTrue(
                        !"done".equals(targetType),
                        "Step "
                                + i
                                + " branch must not jump to terminal done step at index "
                                + next
                                + " (inserts before coordinator menu break branch math)");
            }
        }
    }

    @Test
    void arriettyRoom_coordinatorMenuEntryStepIndicesAreStable() throws Exception {
        Path yamlPath = Path.of("config", "bots.yaml");
        assertTrue(Files.exists(yamlPath), "config/bots.yaml missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(yamlPath));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_LINEAR_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) room.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "arrietty_room_legacy has no steps");
        assertEquals("branch", String.valueOf(steps.get(0).get("type")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wsBranches = (List<Map<String, Object>>) steps.get(0).get("branches");
        assertNotNull(wsBranches);
        assertTrue(
                wsBranches.stream().allMatch(br -> Integer.valueOf(1).equals(((Number) br.get("next")).intValue())),
                "workspace branch must enter early workspace post (step 1), not skip coordinator spine");
        assertEquals("call_action", String.valueOf(steps.get(1).get("type")));
        assertEquals("post_channel_message", String.valueOf(steps.get(1).get("action")));
        assertEquals("call_action", String.valueOf(steps.get(73).get("type")));
        assertEquals("coordinator_intake_bootstrap", String.valueOf(steps.get(73).get("action")));
        assertEquals("branch", String.valueOf(steps.get(74).get("type")));
        assertEquals("call_action", String.valueOf(steps.get(26).get("type")));
        assertEquals("execute_planning_room_cycle", String.valueOf(steps.get(26).get("action")));
        assertEquals("build_insight_discovery_agenda", String.valueOf(steps.get(17).get("action")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> postAgendaBranches =
                (List<Map<String, Object>>) steps.get(18).get("branches");
        assertNotNull(postAgendaBranches);
        boolean elseGoesToMarkIntake = postAgendaBranches.stream()
                .anyMatch(br -> "else".equals(br.get("when"))
                        && Integer.valueOf(25).equals(((Number) br.get("next")).intValue()));
        assertTrue(elseGoesToMarkIntake, "empty blocking clarification must continue to mark_intake path, not step 1");
        assertTrue(
                steps.stream().noneMatch(s -> {
                    if (!"prompt_for_field".equals(String.valueOf(s.get("type")))) {
                        return false;
                    }
                    String p = String.valueOf(s.get("prompt"));
                    return p.contains("Add scope") && p.contains("must-haves");
                }),
                "optional solicitation prompt_for_field must not exist in live workflow");
        int humanReview = -1;
        int ack = -1;
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            if ("call_action".equals(String.valueOf(s.get("type")))
                    && "acknowledge_readiness_human_decision".equals(String.valueOf(s.get("action")))) {
                ack = i;
            }
            if ("call_action".equals(String.valueOf(s.get("type")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> bind = (Map<String, Object>) s.get("bind");
                if (bind != null) {
                    String c = String.valueOf(bind.getOrDefault("content", ""));
                    if (c.contains("Human review requested")) {
                        humanReview = i;
                    }
                }
            }
        }
        assertTrue(humanReview >= 0 && ack >= 0, "human review and acknowledge steps must exist");
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            if (!"branch".equals(String.valueOf(s.get("type")))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> branches = (List<Map<String, Object>>) s.get("branches");
            if (branches == null) {
                continue;
            }
            for (Map<String, Object> b : branches) {
                Object whenObj = b.get("when");
                if (!(whenObj instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> when = (Map<String, Object>) whenObj;
                if ("readinessProceedRaw".equals(String.valueOf(when.get("key")))
                        && "proceed".equals(String.valueOf(when.get("value")))) {
                    assertEquals(
                            ack,
                            ((Number) b.get("next")).intValue(),
                            "proceed must jump to acknowledge_readiness_human_decision, not human-review repost");
                }
            }
        }
    }

    @Test
    void arriettyRoom_keyStepsReachableViaBranchAndLinearEdges() throws Exception {
        Path yamlPath = Path.of("config", "bots.yaml");
        assertTrue(Files.exists(yamlPath), "config/bots.yaml missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(yamlPath));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_LINEAR_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) room.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "arrietty_room_legacy has no steps");
        int n = steps.size();
        List<Set<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new HashSet<>());
        }
        for (int i = 0; i < n; i++) {
            Map<String, Object> step = steps.get(i);
            if ("branch".equals(String.valueOf(step.get("type")))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> branches = (List<Map<String, Object>>) step.get("branches");
                if (branches != null) {
                    for (Map<String, Object> b : branches) {
                        Object nextObj = b.get("next");
                        if (nextObj instanceof Number num) {
                            int next = num.intValue();
                            if (next >= 0 && next < n) {
                                adj.get(i).add(next);
                            }
                        }
                    }
                }
            } else if (i + 1 < n) {
                adj.get(i).add(i + 1);
            }
        }
        Set<Integer> seen = new HashSet<>();
        Queue<Integer> q = new ArrayDeque<>();
        q.add(0);
        seen.add(0);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v : adj.get(u)) {
                if (seen.add(v)) {
                    q.add(v);
                }
            }
        }
        int coordinatorBootstrap = indexOfCallAction(steps, "coordinator_intake_bootstrap");
        int planningCycle = indexOfCallAction(steps, "execute_planning_room_cycle");
        int structuredGaps = indexOfCallAction(steps, "get_structured_discovery_gaps");
        int markIntakeDone = indexOfCallAction(steps, "mark_intake_discovery_complete");
        assertTrue(seen.contains(coordinatorBootstrap), "coordinator_intake_bootstrap step must be reachable from start");
        assertTrue(seen.contains(planningCycle), "execute_planning_room_cycle must be reachable from start");
        assertTrue(seen.contains(structuredGaps), "get_structured_discovery_gaps must be reachable from start");
        assertTrue(seen.contains(markIntakeDone), "mark_intake_discovery_complete must be reachable from start");
    }

    private static int indexOfCallAction(List<Map<String, Object>> steps, String actionId) {
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            if ("call_action".equals(String.valueOf(s.get("type")))
                    && actionId.equals(String.valueOf(s.get("action")))) {
                return i;
            }
        }
        throw new AssertionError("call_action " + actionId + " not found in workflow");
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
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_LINEAR_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) room.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "arrietty_room_legacy has no steps");
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
