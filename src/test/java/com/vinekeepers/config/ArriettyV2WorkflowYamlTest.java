package com.vinekeepers.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArriettyV2WorkflowYamlTest {

    private static final String ARRIETTY_V2_ID = "arrietty_room_v2";

    @Test
    void arriettyRoom_isV2OnlyProductionWorkflow() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        assertTrue(workflows.containsKey(ARRIETTY_V2_ID), "arrietty_room_v2 must exist");
        assertFalse(workflows.containsKey("arrietty_room_legacy"), "arrietty_room_legacy must be deleted");

        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        assertEquals("v2", String.valueOf(room.get("workflowSchema")));
        String entryPhase = String.valueOf(room.get("entryPhase"));
        assertTrue(
                "planning_ingress".equals(entryPhase) || "planning".equals(entryPhase),
                "entryPhase must be planning_ingress or planning");

        @SuppressWarnings("unchecked")
        Map<String, Object> phases = (Map<String, Object>) room.get("phases");
        assertTrue(phases.containsKey("done"), "missing terminal done phase");
        if ("planning_ingress".equals(entryPhase)) {
            for (String phaseId : List.of(
                    "planning_ingress",
                    "planning_prepare",
                    "planning_autonomous_draft",
                    "planning_assess_clarification",
                    "planning_clarification",
                    "planning_packet",
                    "planning_critique",
                    "planning_preapproval_review",
                    "planning_approval",
                    "planning_launch",
                    "done")) {
                assertTrue(phases.containsKey(phaseId), "missing phase " + phaseId);
            }
        } else {
            assertTrue(phases.containsKey("planning"), "minimal v2 must include planning phase");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");
        boolean hasInlineSteps = false;
        for (Map.Entry<String, Object> entry : capabilities.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> capability = (Map<String, Object>) entry.getValue();
            String kind = String.valueOf(capability.get("kind"));
            assertFalse(
                    "linear_workflow_ref".equals(kind),
                    "arrietty_room_v2 must not delegate to a sibling linear workflow: capability "
                            + entry.getKey());
            if ("configurable_steps".equals(kind)) {
                hasInlineSteps = true;
            }
        }
        assertTrue(hasInlineSteps, "arrietty_room_v2 must embed at least one configurable_steps capability");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bots = (List<Map<String, Object>>) root.get("bots");
        Map<String, Object> arrietty = bots.stream()
                .filter(bot -> "arrietty".equals(String.valueOf(bot.get("id"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("arrietty bot must exist"));
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) arrietty.get("workflow");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) wf.get("params");
        assertEquals(
                ARRIETTY_V2_ID,
                String.valueOf(params.get("workflowRef")),
                "arrietty bot must use arrietty_room_v2 in production config");
    }

    @Test
    void arriettyRoom_doesNotReintroduceLegacyKickoffSolicitation() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");
        for (Object rawCapability : capabilities.values()) {
            if (!(rawCapability instanceof Map<?, ?> capability)) {
                continue;
            }
            if (!"configurable_steps".equals(String.valueOf(capability.get("kind")))) {
                continue;
            }
            Object rawSteps = capability.get("steps");
            if (!(rawSteps instanceof List<?> steps)) {
                continue;
            }
            for (Object rawStep : steps) {
                if (!(rawStep instanceof Map<?, ?> step)) {
                    continue;
                }
                if (!"prompt_for_field".equals(String.valueOf(step.get("type")))) {
                    continue;
                }
                String prompt = String.valueOf(step.get("prompt"));
                assertFalse(
                        prompt.contains("Add scope") && prompt.contains("must-haves"),
                        "legacy kickoff solicitation prompt must stay deleted");
            }
        }
    }

    @Test
    void planningRoom_isArriettyOnly() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bots = (List<Map<String, Object>>) root.get("bots");
        List<String> botIds = bots.stream().map(bot -> String.valueOf(bot.get("id"))).collect(Collectors.toList());
        assertTrue(botIds.contains("arrietty"));
        assertFalse(botIds.contains("architect"));
        assertFalse(botIds.contains("auditor"));
        assertFalse(botIds.contains("scribe"));

        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        assertFalse(workflows.containsKey("architect_intro"));
        assertFalse(workflows.containsKey("auditor_intro"));
        assertFalse(workflows.containsKey("scribe_intro"));

        @SuppressWarnings("unchecked")
        Map<String, Object> lunaCursor = (Map<String, Object>) workflows.get("luna_cursor");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) lunaCursor.get("steps");
        Map<String, Object> createChannel = steps.stream()
                .filter(step -> "create_channel".equals(String.valueOf(step.get("action"))))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> bind = (Map<String, Object>) createChannel.get("bind");
        @SuppressWarnings("unchecked")
        List<String> participantBotIds = (List<String>) bind.get("participantBotIds");
        assertEquals(List.of("arrietty"), participantBotIds);
    }
}
