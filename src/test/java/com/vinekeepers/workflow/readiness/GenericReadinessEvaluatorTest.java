package com.vinekeepers.workflow.readiness;

import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericReadinessEvaluatorTest {

    private static final String RICH_FEATURE =
            "Ship incremental login improvements with session hardening distinct from the legacy cookie path.";

    @Test
    void v2Profile_failsWhenExplorationThin() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition v2 = reg.get("software_feature_planning_v2").orElseThrow();
        FeaturePlanState plan = minimalV2Plan("word ".repeat(20), "state ".repeat(20), RICH_FEATURE);
        PlanningPacketDepthEvaluator.DepthResult r = GenericReadinessEvaluator.evaluate(v2, plan);
        assertFalse(r.ok(), r.reason());
    }

    @Test
    void v2Profile_okWhenThresholdsMet() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition v2 = reg.get("software_feature_planning_v2").orElseThrow();
        String exploration = "word ".repeat(30);
        String narrative = "state ".repeat(20);
        FeaturePlanState plan = minimalV2Plan(exploration, narrative, RICH_FEATURE);
        PlanningPacketDepthEvaluator.DepthResult r = GenericReadinessEvaluator.evaluate(v2, plan);
        assertTrue(r.ok(), r.reason());
    }

    private static FeaturePlanState minimalV2Plan(String explorationBody, String currentState, String featureSummary) {
        Map<String, Object> reqValues = new LinkedHashMap<>();
        reqValues.put("feature_summary", featureSummary);
        reqValues.put("current_state_summary", currentState);
        SectionState reqSec = new SectionState("narrative", SectionState.STATUS_DRAFT, reqValues, java.util.List.of());
        ArtifactState reqArt = new ArtifactState("requirements_spec", Map.of("narrative", reqSec));

        Map<String, Object> exValues = new LinkedHashMap<>();
        exValues.put("exploration_body", explorationBody);
        SectionState exSec = new SectionState("analysis", SectionState.STATUS_DRAFT, exValues, java.util.List.of());
        ArtifactState exArt = new ArtifactState("request_exploration", Map.of("analysis", exSec));

        Map<String, ArtifactState> arts = new LinkedHashMap<>();
        arts.put("requirements_spec", reqArt);
        arts.put("request_exploration", exArt);
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
