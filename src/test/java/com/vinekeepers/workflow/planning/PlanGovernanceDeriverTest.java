package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanIssueStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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
}
