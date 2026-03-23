package com.vinekeepers.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@code workflows.devops_deploy} in {@code config/bots.yaml}: branch targets must be in range,
 * main menu must expose compose paths including restart.
 */
class DevopsDeployWorkflowYamlTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> devopsSteps() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        Map<String, Object> devops = (Map<String, Object>) workflows.get("devops_deploy");
        return (List<Map<String, Object>>) devops.get("steps");
    }

    @Test
    void devopsDeploy_branchNextIndicesAreInRange() throws Exception {
        List<Map<String, Object>> steps = devopsSteps();
        int n = steps.size();
        List<Integer> bad = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> step = steps.get(i);
            if (!"branch".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            Object rawBranches = step.get("branches");
            if (!(rawBranches instanceof List<?> branches)) {
                continue;
            }
            for (Object raw : branches) {
                if (!(raw instanceof Map<?, ?> b)) {
                    continue;
                }
                Object nextObj = b.get("next");
                if (!(nextObj instanceof Number num)) {
                    continue;
                }
                int next = num.intValue();
                if (next < 0 || next >= n) {
                    bad.add(next);
                }
            }
        }
        assertTrue(bad.isEmpty(), "branch next out of range: " + bad + " (step count " + n + ")");
    }

    @Test
    void devopsDeploy_mainMenuIncludesComposeRestart() throws Exception {
        List<Map<String, Object>> steps = devopsSteps();
        Map<String, Object> mainPrompt = steps.stream()
                .filter(s -> "prompt_for_field".equals(String.valueOf(s.get("type"))))
                .filter(s -> "mainMenu".equals(String.valueOf(s.get("storeIn"))))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) mainPrompt.get("choices");
        boolean hasRestart = choices.stream()
                .anyMatch(c -> "compose_restart".equals(String.valueOf(c.get("id"))));
        assertTrue(hasRestart, "main menu must offer compose_restart");
    }

    @Test
    void devopsDeploy_composePsUsesDevOpsProgressThread() throws Exception {
        List<Map<String, Object>> steps = devopsSteps();

        Map<String, Object> createThread = (Map<String, Object>) steps.get(48);
        assertEquals("call_action", String.valueOf(createThread.get("type")));
        assertEquals("create_thread", String.valueOf(createThread.get("action")));

        @SuppressWarnings("unchecked")
        Map<String, Object> bind = (Map<String, Object>) createThread.get("bind");
        assertEquals("DevOps Progress", String.valueOf(bind.get("threadName")));

        Map<String, Object> composeCall = (Map<String, Object>) steps.get(49);
        assertEquals("run_deploy_compose", String.valueOf(composeCall.get("action")));
        @SuppressWarnings("unchecked")
        Map<String, Object> composeBind = (Map<String, Object>) composeCall.get("bind");
        assertEquals("ps", String.valueOf(composeBind.get("composeOperation")));
    }

    @Test
    void devopsDeploy_composeUpRoutesToTargetOrServiceThenRunsUp() throws Exception {
        List<Map<String, Object>> steps = devopsSteps();

        Map<String, Object> branch = (Map<String, Object>) steps.get(18);
        assertEquals("branch", String.valueOf(branch.get("type")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) branch.get("branches");
        assertEquals(19, ((Number) branches.get(0).get("next")).intValue());
        assertEquals(21, ((Number) branches.get(1).get("next")).intValue());

        Map<String, Object> createThread = (Map<String, Object>) steps.get(23);
        assertEquals("create_thread", String.valueOf(createThread.get("action")));

        @SuppressWarnings("unchecked")
        Map<String, Object> threadBind = (Map<String, Object>) createThread.get("bind");
        assertEquals("DevOps Progress", String.valueOf(threadBind.get("threadName")));

        Map<String, Object> composeCall = (Map<String, Object>) steps.get(24);
        assertEquals("run_deploy_compose", String.valueOf(composeCall.get("action")));

        @SuppressWarnings("unchecked")
        Map<String, Object> composeBind = (Map<String, Object>) composeCall.get("bind");
        assertEquals("up", String.valueOf(composeBind.get("composeOperation")));
    }

    @Test
    void devopsDeploy_composeStopRoutesToTargetOrServiceThenRunsStop() throws Exception {
        List<Map<String, Object>> steps = devopsSteps();

        Map<String, Object> branch = (Map<String, Object>) steps.get(27);
        assertEquals("branch", String.valueOf(branch.get("type")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) branch.get("branches");
        assertEquals(28, ((Number) branches.get(0).get("next")).intValue());
        assertEquals(30, ((Number) branches.get(1).get("next")).intValue());

        Map<String, Object> createThread = (Map<String, Object>) steps.get(32);
        assertEquals("create_thread", String.valueOf(createThread.get("action")));

        @SuppressWarnings("unchecked")
        Map<String, Object> threadBind = (Map<String, Object>) createThread.get("bind");
        assertEquals("deploy-progress", String.valueOf(threadBind.get("threadName")));

        Map<String, Object> composeCall = (Map<String, Object>) steps.get(33);
        assertEquals("run_deploy_compose", String.valueOf(composeCall.get("action")));

        @SuppressWarnings("unchecked")
        Map<String, Object> composeBind = (Map<String, Object>) composeCall.get("bind");
        assertEquals("stop", String.valueOf(composeBind.get("composeOperation")));
    }
}
