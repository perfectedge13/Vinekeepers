package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.workflow.actions.UpsertArtifactSectionDataAction;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanGovernanceDeriverTest {

    @Test
    void requiredFieldGapCreatesBlockingIssue() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning").orElseThrow();
        FeaturePlanState plan =
                new FeaturePlanState(
                        "ctx",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "1234567890123456789012345678901234567890ABCD",
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
                        java.util.Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        plan = plan.withAutonomousPlanningPassCompleted(true);
        FeaturePlanState next = PlanGovernanceDeriver.derive(plan, profile);
        assertTrue(
                next.getIssues().stream().anyMatch(i -> i.getId().startsWith("gap:")),
                "expected gap-derived issue");
        assertTrue(
                next.getIssues().stream().anyMatch(i -> PlanIssueStatus.BLOCKING.equals(i.getStatus())),
                "expected a blocking issue for missing required profile fields");
    }

    @Test
    void mergeIsIdempotentForSameGap() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning").orElseThrow();
        FeaturePlanState plan =
                new FeaturePlanState(
                        "ctx2",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "1234567890123456789012345678901234567890ABCD",
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
                        java.util.Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        FeaturePlanState a = PlanGovernanceDeriver.derive(plan, profile);
        FeaturePlanState b = PlanGovernanceDeriver.derive(a, profile);
        assertEquals(a.getIssues().size(), b.getIssues().size());
    }

    @Test
    void workspaceBlockerGapSurfacesAsIssue() {
        FeaturePlanState plan =
                new FeaturePlanState(
                        "w",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "1234567890123456789012345678901234567890ABCD",
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
                        java.util.Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)
                        .withWorkspaceLinkage("w1", "FAILED", "", "no access");
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning").orElseThrow();
        FeaturePlanState next = PlanGovernanceDeriver.derive(plan, profile);
        assertTrue(next.getIssues().stream().anyMatch(i -> i.getId().startsWith("gap:")));
    }

    @Test
    void repeatableDecisionLogRowsBecomeSeparateArtifactBackedDecisions() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning").orElseThrow();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "gov-dec", "channelId", "ch"),
                        Map.of("profileId", "software_feature_planning")));
        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        assertEquals(
                "OK",
                upsert.run(
                        null,
                        Map.of("contextId", "gov-dec"),
                        Map.of(
                                "artifactId",
                                "decision_log",
                                "sectionId",
                                "decisions",
                                "mode",
                                "replace",
                                "data",
                                Map.of("decision_text", "First settled choice about persistence layer"))));
        assertEquals(
                "OK",
                upsert.run(
                        null,
                        Map.of("contextId", "gov-dec"),
                        Map.of(
                                "artifactId",
                                "decision_log",
                                "sectionId",
                                "decisions",
                                "mode",
                                "append",
                                "data",
                                Map.of("decision_text", "Second settled choice about API versioning"))));

        FeaturePlanState plan = store.getByContextId("gov-dec").orElseThrow();
        FeaturePlanState next = PlanGovernanceDeriver.derive(plan, profile);
        long artBacked =
                next.getDecisions().stream().filter(d -> d.getId() != null && d.getId().startsWith("art-dec-")).count();
        assertEquals(2L, artBacked);
    }
}
