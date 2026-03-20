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
        assertEquals("OK", init.run(null, Map.of("contextId", "cx", "channelId", "ch"), Map.of()));
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
        init.run(null, Map.of("contextId", "cy", "channelId", "ch"), Map.of());
        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        upsert.run(null, Map.of("contextId", "cy"),
                Map.of("artifactId", "requirements_spec", "sectionId", "narrative", "data", Map.of("feature_summary", "x")));
        upsert.run(null, Map.of("contextId", "cy"),
                Map.of("artifactId", "overall_plan", "sectionId", "outline", "data", Map.of("plan_body", "p")));
        upsert.run(null, Map.of("contextId", "cy"),
                Map.of("artifactId", "validation_plan", "sectionId", "checks", "data", Map.of("validation_notes", "v")));

        var plan = store.getByContextId("cy").orElseThrow();
        var profile = reg.get(plan.getProfileId()).orElseThrow();
        List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile);
        assertTrue(gaps.isEmpty());
    }
}
