package com.vinekeepers.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                    "planning_preflight",
                    "planning_silent_synthesis",
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
    void arriettyPostAssessRoutesOnPlanningNextPhaseOnly() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> rulesets =
                (Map<String, List<Map<String, Object>>>) room.get("rulesets");
        List<Map<String, Object>> rs = rulesets.get("rs_evaluation_branch");
        assertTrue(rs != null && !rs.isEmpty(), "rs_evaluation_branch must exist");
        Set<String> phases = new HashSet<>();
        for (Map<String, Object> rule : rs) {
            @SuppressWarnings("unchecked")
            Map<String, Object> when = (Map<String, Object>) rule.get("when");
            assertTrue(when != null && when.containsKey("equals"), "post-assess rules must use equals on live routing state");
            @SuppressWarnings("unchecked")
            Map<String, Object> eq = (Map<String, Object>) when.get("equals");
            assertEquals("planningNextPhase", String.valueOf(eq.get("key")));
            phases.add(String.valueOf(eq.get("value")));
        }
        assertEquals(
                Set.of(
                        "planning_clarification",
                        "planning_packet",
                        "planning_blocked"),
                phases,
                "post-assess must map each planningNextPhase explicitly");
    }

    @Test
    void arriettyClarificationCapabilityBranchesOnPlanningNextPhase() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");
        @SuppressWarnings("unchecked")
        Map<String, Object> capability = (Map<String, Object>) capabilities.get("cap_planning_clarification");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) capability.get("steps");
        @SuppressWarnings("unchecked")
        Map<String, Object> branch = steps.get(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) branch.get("branches");
        @SuppressWarnings("unchecked")
        Map<String, Object> when = (Map<String, Object>) branches.get(0).get("when");
        assertEquals("planningNextPhase", String.valueOf(when.get("key")));
        assertEquals("planning_clarification", String.valueOf(when.get("value")));
    }

    @Test
    void arriettyAllowedKeys_excludesLegacyPostDraftSpreadKey() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> templates = (Map<String, Object>) room.get("templates");
        @SuppressWarnings("unchecked")
        List<String> allowedKeys = (List<String>) templates.get("allowedKeys");
        assertFalse(
                allowedKeys.contains("planningPostDraftAction"),
                "live room must not expose legacy planningPostDraftAction in template allow-list");
        assertFalse(
                allowedKeys.contains("discoveryCurrentQuestionPrompt"),
                "live room must not expose discovery field prompts as coordinator template keys");
    }

    @Test
    void arriettyPlanningClarificationDefaultIsRoutingInvariant() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> phases = (Map<String, Object>) room.get("phases");
        @SuppressWarnings("unchecked")
        Map<String, Object> assess = (Map<String, Object>) phases.get("planning_clarification");
        assertEquals("planning_routing_invariant", String.valueOf(assess.get("defaultNextPhase")));
    }

    @Test
    void arriettyPlanningPreflight_removesProposalAndMissingFieldNoiseFromLivePath() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");
        @SuppressWarnings("unchecked")
        Map<String, Object> cap = (Map<String, Object>) capabilities.get("cap_planning_preflight");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) cap.get("steps");
        List<String> actions = steps.stream()
                .filter(step -> "call_action".equals(String.valueOf(step.get("type"))))
                .map(step -> String.valueOf(step.get("action")))
                .toList();
        assertFalse(actions.contains("get_profile_missing_fields"));
        assertFalse(actions.contains("generate_planning_proposals"));
        assertFalse(actions.contains("apply_auto_planning_proposals"));
        assertFalse(actions.contains("accept_pending_planning_confirmations"));
        long promptCount = steps.stream()
                .filter(step -> "prompt_for_field".equals(String.valueOf(step.get("type"))))
                .count();
        assertEquals(1L, promptCount, "planning_preflight should prompt only for workspace blockers");
        Map<String, Object> promptStep = steps.stream()
                .filter(step -> "prompt_for_field".equals(String.valueOf(step.get("type"))))
                .findFirst()
                .orElseThrow();
        assertEquals("❓ {{planningWorkspaceBlockerPrompt}}", String.valueOf(promptStep.get("prompt")));
    }

    @Test
    void arriettyClarification_promptsCanonicalQuestionTextDirectly() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");
        @SuppressWarnings("unchecked")
        Map<String, Object> cap = (Map<String, Object>) capabilities.get("cap_planning_clarification");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) cap.get("steps");
        Map<String, Object> promptStep = steps.stream()
                .filter(step -> "prompt_for_field".equals(String.valueOf(step.get("type"))))
                .findFirst()
                .orElseThrow();
        assertEquals("❓ {{planningClarificationQuestionText}}", String.valueOf(promptStep.get("prompt")));
    }

    @Test
    void arriettyDoesNotKeepSeparateContinuationNoticePhase() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");
        assertFalse(capabilities.containsKey("cap_post_draft_blocked"));
    }

    @Test
    void arriettyBlockedThreadUsesUserVisiblePlanningFailure() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");
        @SuppressWarnings("unchecked")
        Map<String, Object> cap = (Map<String, Object>) capabilities.get("cap_post_blocked_thread");
        @SuppressWarnings("unchecked")
        Map<String, Object> bind = (Map<String, Object>) cap.get("bind");
        String content = String.valueOf(bind.get("content"));
        assertTrue(content.contains("{{planningCycleUserVisibleFailure}}"));
        assertFalse(content.contains("{{planCritiqueError}}"));
    }

    @Test
    void arriettyTemplatePolicyAllowlistsPlanningCycleUserVisibleFailure() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> templates = (Map<String, Object>) room.get("templates");
        @SuppressWarnings("unchecked")
        List<String> allowedKeys = (List<String>) templates.get("allowedKeys");
        assertTrue(allowedKeys.contains("planningCycleUserVisibleFailure"));
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

    @Test
    void arriettyPromptQuestionsStartWithAlertEmoji() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.newBufferedReader(Path.of("config", "bots.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> workflows = (Map<String, Object>) root.get("workflows");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) workflows.get(ARRIETTY_V2_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) room.get("capabilities");

        int promptCount = 0;
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
                promptCount++;
                String prompt = String.valueOf(step.get("prompt"));
                assertTrue(
                        prompt.startsWith("❓ "),
                        "Arrietty prompt_for_field steps must start with the red question mark alert");
            }
        }

        assertTrue(promptCount > 0, "arrietty_room_v2 should define at least one prompt_for_field step");
    }
}
