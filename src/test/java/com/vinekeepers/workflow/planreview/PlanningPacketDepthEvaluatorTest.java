package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPacketDepthEvaluatorTest {

    private static final String RICH_FEATURE =
            "Ship incremental login improvements with session hardening distinct from the legacy cookie path.";

    @Test
    void wordCount_emptyIsZero() {
        assertEquals(0, PlanningPacketDepthEvaluator.wordCount(null));
        assertEquals(0, PlanningPacketDepthEvaluator.wordCount(""));
        assertEquals(0, PlanningPacketDepthEvaluator.wordCount("   "));
    }

    @Test
    void wordCount_countsTokens() {
        assertEquals(3, PlanningPacketDepthEvaluator.wordCount("a b c"));
    }

    @Test
    void evaluate_failsWhenExplorationThin() {
        FeaturePlanState plan = minimalPlanWithNarrative("word ".repeat(20), "y".repeat(200), RICH_FEATURE);
        PlanningPacketDepthEvaluator.DepthResult r = PlanningPacketDepthEvaluator.evaluate(plan);
        assertFalse(r.ok());
        assertTrue(r.reason().contains("exploration"));
    }

    @Test
    void evaluate_failsWhenNarrativesThin() {
        String exploration = "word ".repeat(30);
        FeaturePlanState plan = minimalPlanWithNarrative(exploration, "short", "also short");
        PlanningPacketDepthEvaluator.DepthResult r = PlanningPacketDepthEvaluator.evaluate(plan);
        assertFalse(r.ok());
        assertTrue(r.reason().contains("thin"));
    }

    @Test
    void evaluate_okWhenThresholdsMet() {
        String exploration = "word ".repeat(30);
        String narrative = "state ".repeat(20);
        FeaturePlanState plan = minimalPlanWithNarrative(exploration, narrative, RICH_FEATURE);
        PlanningPacketDepthEvaluator.DepthResult r = PlanningPacketDepthEvaluator.evaluate(plan);
        assertTrue(r.ok(), r.reason());
    }

    @Test
    void evaluate_v2Profile_declarativeThenSupplemental_okWhenThresholdsMet() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        String exploration = "word ".repeat(30);
        String narrative = "state ".repeat(20);
        FeaturePlanState plan = minimalV2PlanFullPacket(exploration, narrative, RICH_FEATURE);
        PlanningPacketDepthEvaluator.DepthResult r =
                PlanningPacketDepthEvaluator.evaluate(plan, reg.get("software_feature_planning_v2").orElseThrow());
        assertTrue(r.ok(), r.reason());
    }

    private static FeaturePlanState minimalPlanWithNarrative(String explorationBody, String currentState, String featureSummary) {
        Map<String, Object> reqValues = new LinkedHashMap<>();
        reqValues.put("feature_summary", featureSummary);
        reqValues.put("current_state_summary", currentState);
        SectionState reqSec = new SectionState("narrative", SectionState.STATUS_DRAFT, reqValues, List.of());
        ArtifactState reqArt = new ArtifactState("requirements_spec", Map.of("narrative", reqSec));

        Map<String, Object> exValues = new LinkedHashMap<>();
        exValues.put("exploration_body", explorationBody);
        SectionState exSec = new SectionState("analysis", SectionState.STATUS_DRAFT, exValues, List.of());
        ArtifactState exArt = new ArtifactState("request_exploration", Map.of("analysis", exSec));

        Map<String, Object> archValues = new LinkedHashMap<>();
        archValues.put("components_impacted", "src/main/java/com/example/AuthService.java");
        archValues.put(
                "architecture_summary",
                "Service layer coordinates token issuance; persistence boundary stays behind repository interfaces.");
        SectionState archSec = new SectionState("impact", SectionState.STATUS_DRAFT, archValues, List.of());
        ArtifactState archArt = new ArtifactState("architecture_notes", Map.of("impact", archSec));

        Map<String, Object> valValues = new LinkedHashMap<>();
        valValues.put(
                "validation_notes",
                "Run mvn test and integration checks that exercise the authentication flows described in this plan, "
                        + "including negative cases and refresh handling. "
                        + "Add regression coverage for session expiry edge cases observed in staging.");
        SectionState valSec = new SectionState("checks", SectionState.STATUS_DRAFT, valValues, List.of());
        ArtifactState valArt = new ArtifactState("validation_plan", Map.of("checks", valSec));

        Map<String, ArtifactState> arts = new LinkedHashMap<>();
        arts.put("requirements_spec", reqArt);
        arts.put("request_exploration", exArt);
        arts.put("architecture_notes", archArt);
        arts.put("validation_plan", valArt);
        FeaturePlanState empty = new FeaturePlanState(
                "ctx",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "",
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "software_feature_planning",
                Map.of(),
                null,
                null);
        return empty.withArtifacts(arts);
    }

    private static FeaturePlanState minimalV2PlanFullPacket(String explorationBody, String currentState, String featureSummary) {
        Map<String, Object> reqValues = new LinkedHashMap<>();
        reqValues.put("feature_summary", featureSummary);
        reqValues.put("current_state_summary", currentState);
        SectionState reqSec = new SectionState("narrative", SectionState.STATUS_DRAFT, reqValues, List.of());
        ArtifactState reqArt = new ArtifactState("requirements_spec", Map.of("narrative", reqSec));

        Map<String, Object> exValues = new LinkedHashMap<>();
        exValues.put("exploration_body", explorationBody);
        SectionState exSec = new SectionState("analysis", SectionState.STATUS_DRAFT, exValues, List.of());
        ArtifactState exArt = new ArtifactState("request_exploration", Map.of("analysis", exSec));

        Map<String, Object> archValues = new LinkedHashMap<>();
        archValues.put("components_impacted", "src/main/java/com/example/AuthService.java");
        archValues.put(
                "architecture_summary",
                "Service layer coordinates token issuance; persistence boundary stays behind repository interfaces.");
        SectionState archSec = new SectionState("impact", SectionState.STATUS_DRAFT, archValues, List.of());
        ArtifactState archArt = new ArtifactState("architecture_notes", Map.of("impact", archSec));

        Map<String, Object> valValues = new LinkedHashMap<>();
        valValues.put(
                "validation_notes",
                "Run mvn test and integration checks that exercise the authentication flows described in this plan, "
                        + "including negative cases and refresh handling. "
                        + "Add regression coverage for session expiry edge cases observed in staging.");
        SectionState valSec = new SectionState("checks", SectionState.STATUS_DRAFT, valValues, List.of());
        ArtifactState valArt = new ArtifactState("validation_plan", Map.of("checks", valSec));

        Map<String, Object> oqValues = new LinkedHashMap<>();
        oqValues.put("open_questions", "None — ready to implement after confirming OAuth scope with security.");
        SectionState oqSec = new SectionState("backlog", SectionState.STATUS_DRAFT, oqValues, List.of());
        ArtifactState oqArt = new ArtifactState("open_questions_block", Map.of("backlog", oqSec));

        Map<String, ArtifactState> arts = new LinkedHashMap<>();
        arts.put("requirements_spec", reqArt);
        arts.put("request_exploration", exArt);
        arts.put("architecture_notes", archArt);
        arts.put("validation_plan", valArt);
        arts.put("open_questions_block", oqArt);
        FeaturePlanState empty = new FeaturePlanState(
                "ctx",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "",
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "software_feature_planning_v2",
                Map.of(),
                null,
                null);
        return empty.withArtifacts(arts);
    }
}
