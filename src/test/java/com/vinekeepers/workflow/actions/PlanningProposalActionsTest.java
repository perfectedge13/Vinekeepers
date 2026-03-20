package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningProposalActionsTest {

    @Test
    void generateProposals_workspaceNotReady_queuesConfirmForRequestBackedDrafts() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "gp1", "channelId", "ch", "codeChange", "Add dark mode toggle"),
                        Map.of()));
        var gen = new GeneratePlanningProposalsAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) gen.run(null, Map.of("contextId", "gp1"), Map.of());
        assertEquals("true", out.get("planningHasConfirmPending"));
        assertEquals("false", out.get("planningHasAutoApply"));
        assertTrue(((String) out.get("planningProposalsJson")).contains("CONFIRM"));
        assertTrue(((String) out.get("planningConfirmQueueJson")).contains("prop-"));
    }

    @Test
    void applyAuto_appliesAutoApplyAndStripsThemFromJson() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "aa1", "channelId", "ch"), Map.of());
        String proposalsJson =
                "[{\"proposalId\":\"p-auto\",\"artifactId\":\"overall_plan\",\"sectionId\":\"outline\",\"fieldId\":\"plan_body\","
                        + "\"proposedValue\":\"AUTO BODY\",\"confidence\":\"HIGH\",\"interaction\":\"AUTO_APPLY\","
                        + "\"sources\":[\"TEST\"],\"reasoningSummary\":\"\",\"suggestedUserQuestion\":\"\"}]";
        var apply = new ApplyAutoPlanningProposalsAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) apply.run(null, Map.of("contextId", "aa1", "planningProposalsJson", proposalsJson), Map.of());
        assertEquals("1", out.get("planningAutoApplyCount"));
        assertEquals("[]", out.get("planningProposalsJson"));
        var plan = store.getByContextId("aa1").orElseThrow();
        var body = plan.getArtifacts().get("overall_plan").getSectionsById().get("outline").getValues().get("plan_body");
        assertEquals("AUTO BODY", body);
    }

    @Test
    void buildAndResolveConfirm_okAcceptsDraft() throws Exception {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "cr1", "channelId", "ch", "codeChange", "Feature X"), Map.of());
        var gen = new GeneratePlanningProposalsAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> genOut = (Map<String, Object>) gen.run(null, Map.of("contextId", "cr1"), Map.of());
        var build = new BuildProposalConfirmPromptAction(store, reg);
        Map<String, Object> state = new LinkedHashMap<>(genOut);
        state.put("contextId", "cr1");
        @SuppressWarnings("unchecked")
        Map<String, Object> promptOut = (Map<String, Object>) build.run(null, state, Map.of());
        assertTrue(((String) promptOut.get("proposalConfirmPrompt")).contains("OK"));
        state.putAll(promptOut);
        state.put("discoveryAnswerRaw", "OK");
        var resolve = new ResolveProposalConfirmationAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> resOut = (Map<String, Object>) resolve.run(null, state, Map.of());
        assertEquals("OK", resOut.get("proposalResolveStatus"));
        assertEquals("true", resOut.get("planningHasConfirmPending"));
        var plan = store.getByContextId("cr1").orElseThrow();
        var body = plan.getArtifacts().get("overall_plan").getSectionsById().get("outline").getValues().get("plan_body");
        assertTrue(body.toString().contains("Feature X"));
    }
}
