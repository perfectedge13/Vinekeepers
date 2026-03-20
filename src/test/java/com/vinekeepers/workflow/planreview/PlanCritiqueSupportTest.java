package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanCritiqueSupportTest {

    private static final Instant T = Instant.parse("2026-03-20T12:00:00Z");

    @Test
    void insufficientRequestAddsMustFixAndNeedsRevision() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning").orElseThrow();
        FeaturePlanState plan = basePlan("short");
        List<PlanCritiqueFinding> f = PlanCritiqueSupport.buildFindings(plan, profile, List.of());
        assertTrue(f.stream().anyMatch(x -> "INSUFFICIENT_REQUEST_SUMMARY".equals(x.getCode())));
        PlanConfidence c = PlanReadinessEvaluator.evaluate(plan, List.of(), f, T);
        assertEquals(PlanReadinessStatus.NEEDS_REVISION, c.getReadinessStatus());
    }

    @Test
    void sufficientArtifactsOmitInsufficientCodesAndAllowReady() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning").orElseThrow();
        SectionState outline = new SectionState("outline", SectionState.STATUS_DRAFT,
                Map.of("plan_body", "1234567890123456789012345678901234567890ABCD"), List.of());
        SectionState checks = new SectionState("checks", SectionState.STATUS_DRAFT,
                Map.of("validation_notes", "123456789012345678901234567890"), List.of());
        ArtifactState overall = new ArtifactState("overall_plan", Map.of("outline", outline));
        ArtifactState val = new ArtifactState("validation_plan", Map.of("checks", checks));
        FeaturePlanState plan = basePlan("123456789012345678901234567890").withArtifacts(Map.of(
                "overall_plan", overall,
                "validation_plan", val));
        List<PlanCritiqueFinding> f = PlanCritiqueSupport.buildFindings(plan, profile, List.of());
        assertFalse(f.stream().anyMatch(x -> x.getCode() != null && x.getCode().startsWith("INSUFFICIENT_")));
        PlanConfidence c = PlanReadinessEvaluator.evaluate(plan, List.of(), f, T);
        assertEquals(PlanReadinessStatus.READY, c.getReadinessStatus());
    }

    private static FeaturePlanState basePlan(String initialRequest) {
        return new FeaturePlanState(
                "ctx",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                initialRequest,
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
    }
}
