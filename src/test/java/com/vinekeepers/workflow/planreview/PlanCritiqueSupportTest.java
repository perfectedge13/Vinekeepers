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
import java.util.LinkedHashMap;
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
        assertEquals(PlanReadinessStatus.NOT_READY, c.getReadinessStatus());
    }

    @Test
    void sufficientArtifactsOmitInsufficientCodesAndAllowReady() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning").orElseThrow();
        String longOutline = "1234567890123456789012345678901234567890ABCD".repeat(3);
        String longVal = "1234567890123456789012345678901234567890ABCD".repeat(2);
        SectionState outline = new SectionState("outline", SectionState.STATUS_DRAFT,
                Map.of("plan_body", longOutline), List.of());
        SectionState checks = new SectionState("checks", SectionState.STATUS_DRAFT,
                Map.of("validation_notes", longVal), List.of());
        ArtifactState overall = new ArtifactState("overall_plan", Map.of("outline", outline));
        ArtifactState val = new ArtifactState("validation_plan", Map.of("checks", checks));
        FeaturePlanState plan = basePlan("1234567890123456789012345678901234567890ABCD").withArtifacts(Map.of(
                "overall_plan", overall,
                "validation_plan", val));
        List<PlanCritiqueFinding> f = PlanCritiqueSupport.buildFindings(plan, profile, List.of());
        assertFalse(f.stream().anyMatch(x -> x.getCode() != null && x.getCode().startsWith("INSUFFICIENT_")));
        FeaturePlanState posted = plan.withPacketPosted(T, "", "test-fp", 1);
        PlanConfidence c = PlanReadinessEvaluator.evaluate(posted, List.of(), f, T);
        assertEquals(PlanReadinessStatus.READY, c.getReadinessStatus());
    }

    @Test
    void v2ReadyToImplementOpenQuestionsDoNotAddInsufficientFinding() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition profile = reg.get("software_feature_planning_v2").orElseThrow();
        FeaturePlanState plan = v2PlanWithoutOpenQuestions();
        List<PlanCritiqueFinding> f = PlanCritiqueSupport.buildFindings(plan, profile, List.of());
        assertFalse(f.stream().anyMatch(x -> "INSUFFICIENT_OPEN_QUESTIONS".equals(x.getCode())));
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static FeaturePlanState v2PlanWithoutOpenQuestions() {
        Map<String, Object> reqValues = new LinkedHashMap<>();
        reqValues.put("feature_summary", "Ship incremental login improvements with session hardening and clearer operator feedback.");
        reqValues.put("current_state_summary", "The current login flow mixes legacy cookies with newer session rules and leaves admins guessing why retries fail.");
        reqValues.put("scope_summary", "In scope: tighten session handling and improve operator feedback. Out of scope: unrelated auth refactors.");
        reqValues.put("acceptance_criteria", "- Login succeeds with hardened session rules.\n- Operators see actionable retry feedback.\n- Regression coverage is added.");
        SectionState reqSec = new SectionState("narrative", SectionState.STATUS_DRAFT, reqValues, List.of());
        ArtifactState reqArt = new ArtifactState("requirements_spec", Map.of("narrative", reqSec));

        SectionState exSec = new SectionState(
                "analysis",
                SectionState.STATUS_DRAFT,
                Map.of("exploration_body", "word ".repeat(30)),
                List.of());
        ArtifactState exArt = new ArtifactState("request_exploration", Map.of("analysis", exSec));

        Map<String, Object> archValues = new LinkedHashMap<>();
        archValues.put("components_impacted", "src/main/java/com/example/AuthService.java");
        archValues.put(
                "architecture_summary",
                "Service layer coordinates session policy changes while preserving the repository boundary and existing transport contracts.");
        SectionState archSec = new SectionState("impact", SectionState.STATUS_DRAFT, archValues, List.of());
        ArtifactState archArt = new ArtifactState("architecture_notes", Map.of("impact", archSec));

        SectionState riskSec = new SectionState(
                "main",
                SectionState.STATUS_DRAFT,
                Map.of("risk_summary", "- Session expiry regressions; mitigate with targeted tests and staged rollout."),
                List.of());
        ArtifactState riskArt = new ArtifactState("risk_register", Map.of("main", riskSec));

        SectionState valSec = new SectionState(
                "checks",
                SectionState.STATUS_DRAFT,
                Map.of(
                        "validation_notes",
                        "Run mvn test plus focused auth regression coverage that exercises hardened session rules and operator-visible failures."),
                List.of());
        ArtifactState valArt = new ArtifactState("validation_plan", Map.of("checks", valSec));

        SectionState outlineSec = new SectionState(
                "outline",
                SectionState.STATUS_DRAFT,
                Map.of("plan_body", "Review the login/session path, implement the hardening changes, and add focused regression coverage for retry and expiry behavior."),
                List.of());
        ArtifactState planArt = new ArtifactState("overall_plan", Map.of("outline", outlineSec));

        SectionState decisionSec = new SectionState(
                "decisions",
                SectionState.STATUS_DRAFT,
                Map.of(),
                List.of(Map.of("decision_text", "Proceed with a focused session-hardening change before broader auth cleanup.")));
        ArtifactState decisionArt = new ArtifactState("decision_log", Map.of("decisions", decisionSec));

        return new FeaturePlanState(
                        "ctx-v2",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "Ship incremental login improvements with session hardening and clearer operator feedback.",
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)
                .withArtifacts(Map.of(
                        "requirements_spec", reqArt,
                        "request_exploration", exArt,
                        "architecture_notes", archArt,
                        "risk_register", riskArt,
                        "validation_plan", valArt,
                        "overall_plan", planArt,
                        "decision_log", decisionArt));
    }
}
