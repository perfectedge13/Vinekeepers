package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanSectionKey;
import com.vinekeepers.state.planning.PlanSectionStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Phase B discovery loop workflow actions not fully exercised by {@link com.vinekeepers.workflow.discovery.StructuredDiscoverySupportTest}.
 */
class DiscoveryWorkflowActionsTest {

    @Test
    void getStructuredDiscoveryGaps_missingContextId_returnsScanError() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var action = new GetStructuredDiscoveryGapsAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(null, Map.of(), Map.of());
        assertEquals("false", out.get("discoveryHasOpenGaps"));
        assertTrue(((String) out.get("discoveryScanError")).contains("contextId"));
    }

    @Test
    void getStructuredDiscoveryGaps_afterInit_reportsOpenGaps() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        assertEquals("OK", init.run(null, Map.of("contextId", "dg", "channelId", "ch"), Map.of()));
        var action = new GetStructuredDiscoveryGapsAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(null, Map.of("contextId", "dg"), Map.of());
        assertEquals("true", out.get("discoveryHasOpenGaps"));
        assertTrue(((String) out.get("discoveryGapsJson")).contains("REQUIRED_FIELD"));
    }

    @Test
    void buildDiscoveryAgenda_missingGapsJson_setsAgendaError() {
        var action = new BuildDiscoveryAgendaAction();
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(null, Map.of(), Map.of());
        assertTrue(((String) out.get("discoveryAgendaError")).contains("Missing"));
    }

    @Test
    void buildDiscoveryAgenda_readsGapsFromState() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "ag", "channelId", "ch"), Map.of());
        var gapsAction = new GetStructuredDiscoveryGapsAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) gapsAction.run(null, Map.of("contextId", "ag"), Map.of());
        var agendaAction = new BuildDiscoveryAgendaAction();
        @SuppressWarnings("unchecked")
        Map<String, Object> agenda = (Map<String, Object>) agendaAction.run(null, spread, Map.of());
        assertTrue(((String) agenda.get("discoveryCurrentQuestionPrompt")).length() > 5);
        assertEquals("REQUIRED_FIELD", agenda.get("discoveryApplyKind"));
    }

    @Test
    void captureAndApply_skipsWhenKindNotRequiredField() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "sk", "channelId", "ch"), Map.of());
        var capture = new CaptureAndApplyDiscoveryAnswerAction(store, reg);
        assertEquals(
                "OK_SKIP_APPLY",
                capture.run(
                        null,
                        Map.of(
                                "contextId",
                                "sk",
                                "discoveryApplyKind",
                                "NONE",
                                "discoveryAnswerRaw",
                                "notes"),
                        Map.of()));
    }

    @Test
    void captureAndApply_requiredField_delegatesToUpsert() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "up", "channelId", "ch"), Map.of());
        var capture = new CaptureAndApplyDiscoveryAnswerAction(store, reg);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contextId", "up");
        state.put("discoveryApplyKind", "REQUIRED_FIELD");
        state.put("discoveryApplyArtifactId", "requirements_spec");
        state.put("discoveryApplySectionId", "narrative");
        state.put("discoveryApplyFieldId", "feature_summary");
        state.put("discoveryApplyMode", "replace");
        assertEquals("OK", capture.run(null, state, Map.of("discoveryAnswerRaw", "User story filled in.")));
        var plan = store.getByContextId("up").orElseThrow();
        var narrative = plan.getArtifacts().get("requirements_spec").getSectionsById().get("narrative");
        assertEquals("User story filled in.", narrative.getValues().get("feature_summary"));
    }

    @Test
    void recomputePlanProgress_missingContextId_returnsErrorSpread() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var action = new RecomputePlanProgressAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(null, Map.of(), Map.of());
        assertEquals("false", out.get("discoveryHasOpenGaps"));
        assertTrue(((String) out.get("discoveryRecomputeError")).contains("contextId"));
    }

    @Test
    void recomputePlanProgress_persistsProjectedSectionStatus() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "rc", "channelId", "ch"), Map.of());
        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        upsert.run(
                null,
                Map.of("contextId", "rc"),
                Map.of(
                        "artifactId",
                        "requirements_spec",
                        "sectionId",
                        "narrative",
                        "data",
                        Map.of("feature_summary", "summary text")));
        var recompute = new RecomputePlanProgressAction(store, reg);
        Object spread = recompute.run(null, Map.of("contextId", "rc"), Map.of());
        assertInstanceOf(Map.class, spread);
        var plan = store.getByContextId("rc").orElseThrow();
        assertEquals(PlanSectionStatus.DRAFT, plan.getSectionStatuses().get(PlanSectionKey.REQUIREMENTS));
    }
}
