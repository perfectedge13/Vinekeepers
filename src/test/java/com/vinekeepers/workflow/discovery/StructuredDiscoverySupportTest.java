package com.vinekeepers.workflow.discovery;

import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.workflow.actions.UpsertArtifactSectionDataAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredDiscoverySupportTest {

    @Test
    void collectGaps_ordersBlockerBeforeHighSeverity() throws Exception {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "cx", "channelId", "ch"),
                        Map.of("profileId", "software_feature_planning")));
        var plan = store.getByContextId("cx").orElseThrow();
        var profile = reg.get(plan.getProfileId()).orElseThrow();

        List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile);
        assertFalse(gaps.isEmpty());

        var spread = StructuredDiscoverySupport.spreadFromGaps(gaps);
        assertEquals("true", spread.get("discoveryHasOpenGaps"));
        String json = (String) spread.get("discoveryGapsJson");
        assertTrue(json.contains("REQUIRED_FIELD"));

        var agenda = StructuredDiscoverySupport.buildAgendaSpread(json);
        String prompt = (String) agenda.get("discoveryCurrentQuestionPrompt");
        assertTrue(prompt.length() > 5);
        assertFalse(prompt.contains("overall_plan.outline.plan_body"));
        assertFalse(prompt.contains("validation_plan.checks.validation_notes"));
        assertTrue(gaps.stream().anyMatch(g -> g.getUserFacingDetail() != null && !g.getUserFacingDetail().isBlank()));
    }

    @Test
    void allRequiredFilled_yieldsNoGaps() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(
                null,
                Map.of("contextId", "cy", "channelId", "ch"),
                Map.of("profileId", "software_feature_planning"));
        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        upsert.run(null, Map.of("contextId", "cy"),
                Map.of("artifactId", "requirements_spec", "sectionId", "narrative", "data",
                        Map.of("feature_summary", "x", "acceptance_criteria", "- Done when it works.")));
        upsert.run(null, Map.of("contextId", "cy"),
                Map.of("artifactId", "overall_plan", "sectionId", "outline", "data", Map.of("plan_body", "p")));
        upsert.run(null, Map.of("contextId", "cy"),
                Map.of("artifactId", "validation_plan", "sectionId", "checks", "data", Map.of("validation_notes", "v")));

        var plan = store.getByContextId("cy").orElseThrow();
        var profile = reg.get(plan.getProfileId()).orElseThrow();
        List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile);
        assertTrue(gaps.isEmpty());
    }

    @Test
    void workspaceBlockersOnly_skipsRequiredFieldGapsWhenWorkspaceUnset() throws Exception {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(
                null,
                Map.of("contextId", "preAuto", "channelId", "ch"),
                Map.of("profileId", "software_feature_planning"));
        var plan = store.getByContextId("preAuto").orElseThrow();
        var profile = reg.get(plan.getProfileId()).orElseThrow();

        List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile, true);
        assertTrue(gaps.isEmpty());

        var spread = StructuredDiscoverySupport.spreadFromGaps(gaps);
        assertEquals("false", spread.get("discoveryHasOpenGaps"));
        assertEquals("false", spread.get("discoveryBlockingIssueMode"));
    }

    @Test
    void workspaceBlockersOnly_stillCollectsWorkspaceBlocker() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(
                null,
                Map.of("contextId", "wsBlock", "channelId", "ch"),
                Map.of("profileId", "software_feature_planning"));
        var plan = store.getByContextId("wsBlock").orElseThrow();
        store.update(plan.withWorkspaceLinkage("w1", "FAILED", "", "clone failed"));
        plan = store.getByContextId("wsBlock").orElseThrow();
        var profile = reg.get(plan.getProfileId()).orElseThrow();

        List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile, true);
        assertEquals(1, gaps.size());
        assertEquals("WORKSPACE", gaps.get(0).getKind());
        assertEquals("BLOCKER", gaps.get(0).getSeverity());
    }

    @Test
    void repeatableSection_rowsMissingSameRequiredField_consolidatesToOneGap() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(
                null,
                Map.of("contextId", "rep", "channelId", "ch"),
                Map.of("profileId", "software_feature_planning"));
        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        upsert.run(
                null,
                Map.of("contextId", "rep"),
                Map.of("artifactId", "decision_log", "sectionId", "decisions", "data", Map.of(), "mode", "replace"));
        upsert.run(
                null,
                Map.of("contextId", "rep"),
                Map.of("artifactId", "decision_log", "sectionId", "decisions", "data", Map.of(), "mode", "append"));
        var plan = store.getByContextId("rep").orElseThrow();
        var profile = reg.get(plan.getProfileId()).orElseThrow();

        List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile);
        long decisionTextGaps =
                gaps.stream().filter(g -> "decision_text".equals(g.getFieldId())).count();
        assertEquals(1, decisionTextGaps);
    }
}
