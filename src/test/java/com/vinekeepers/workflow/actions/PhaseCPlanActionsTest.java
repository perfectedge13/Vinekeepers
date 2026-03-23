package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanApprovalStatus;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueLifecycleStatus;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;
import com.vinekeepers.state.planning.PlanCritiqueSnapshot;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseCPlanActionsTest {

    @Test
    void runPlanCritiqueSpreadsReadiness() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        assertEquals("OK", init.run(null, Map.of(
                "contextId", "c1",
                "channelId", "room1",
                "codeChange", "Ship dark-mode toggle with accessibility review and QA sign-off."),
                Map.of("profileId", "software_feature_planning")));
        var upsertCrit = new UpsertArtifactSectionDataAction(store, reg);
        assertEquals("OK", upsertCrit.run(null,
                Map.of("contextId", "c1"),
                Map.of(
                        "artifactId", "requirements_spec",
                        "sectionId", "narrative",
                        "mode", "merge",
                        "data", Map.of("acceptance_criteria", "- Toggle persists; - WCAG AA contrast; - QA sign-off in staging."))));
        assertEquals("OK", upsertCrit.run(null,
                Map.of("contextId", "c1"),
                Map.of(
                        "artifactId", "overall_plan",
                        "sectionId", "outline",
                        "mode", "replace",
                        "data", Map.of("plan_body", "Step A: theme tokens. Step B: UI wiring. Step C: QA and a11y audit. "
                                + "Step D: docs and rollout checklist with rollback notes."))));
        assertEquals("OK", upsertCrit.run(null,
                Map.of("contextId", "c1"),
                Map.of(
                        "artifactId", "validation_plan",
                        "sectionId", "checks",
                        "mode", "replace",
                        "data", Map.of("validation_notes", "Automated UI tests; manual screen reader pass; contrast checker; "
                                + "staging soak before production flag."))));
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) new RunPlanCritiqueAndReadinessAction(store, reg)
                .run(null, Map.of("contextId", "c1", "humanDiscoveryCompleted", "true"), Map.of());
        assertTrue(spread.containsKey("planReadinessStatus"));
        assertTrue(spread.containsKey("planReadinessStatusLabel"));
        assertTrue(spread.containsKey("planReadinessCheckpointGuide"));
        assertEquals(
                PlanningUserFacingCopy.humanizeReadinessStatus(String.valueOf(spread.get("planReadinessStatus"))),
                spread.get("planReadinessStatusLabel"));
        assertEquals("", spread.get("planReadinessCheckpointGuide"));
        assertEquals("", spread.get("planCritiqueError"));
        FeaturePlanState p = store.getByContextId("c1").orElseThrow();
        assertTrue(p.getPlanCritiqueSnapshot() != null);
        assertTrue(p.getPlanConfidence() != null);
        assertTrue(spread.containsKey("planningThreadReviewBody"));
        assertEquals("", spread.get("planningThreadReviewBuildError"));
        assertTrue(spread.containsKey("planAssumptionsSummary"));
        assertTrue(spread.containsKey("planIssuesSummary"));
    }

    @Test
    void runPlanCritique_mergesPlanningThreadReviewBodyFromArtifacts() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        assertEquals("OK", init.run(null, Map.of(
                "contextId", "c-review",
                "channelId", "roomR",
                "codeChange", "Add export-to-PDF with pagination and embedded metadata for audit trails."),
                Map.of("profileId", "software_feature_planning")));
        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        assertEquals("OK", upsert.run(null,
                Map.of("contextId", "c-review"),
                Map.of(
                        "artifactId", "overall_plan",
                        "sectionId", "outline",
                        "mode", "replace",
                        "data", Map.of("plan_body", "Phase 1: data model and API. Phase 2: UI export flow. Phase 3: QA and docs. "
                                + "Phase 4: performance tuning. Phase 5: rollout and monitoring. "
                                + "Each phase includes code review and incremental integration tests."))));
        assertEquals("OK", upsert.run(null,
                Map.of("contextId", "c-review"),
                Map.of(
                        "artifactId", "validation_plan",
                        "sectionId", "checks",
                        "mode", "replace",
                        "data", Map.of("validation_notes", "mvn verify; contract tests; manual PDF spot-checks in staging."))));
        assertEquals("OK", upsert.run(null,
                Map.of("contextId", "c-review"),
                Map.of(
                        "artifactId", "requirements_spec",
                        "sectionId", "narrative",
                        "mode", "merge",
                        "data", Map.of("acceptance_criteria", "- PDF renders; - metadata present; - audit log export works."))));

        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) new RunPlanCritiqueAndReadinessAction(store, reg)
                .run(null, Map.of("contextId", "c-review", "humanDiscoveryCompleted", "true"), Map.of());
        assertEquals("", spread.get("planCritiqueError"));
        String body = (String) spread.get("planningThreadReviewBody");
        assertTrue(body.contains("Phase 1: data model"));
        assertTrue(body.contains("mvn verify"));
        assertEquals("", spread.get("planningThreadReviewBuildError"));
    }

    @Test
    void persistPlanApprovalWritesState() {
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        init.run(
                null,
                Map.of("contextId", "c2", "channelId", "r2"),
                Map.of("profileId", "software_feature_planning"));
        Instant t = Instant.now();
        PlanCritiqueRubricScores rubric =
                new PlanCritiqueRubricScores(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9);
        PlanCritiqueSnapshot snap =
                new PlanCritiqueSnapshot(
                        t,
                        "RULES_V1",
                        List.of(),
                        PlanCritiqueLifecycleStatus.COMPLETE,
                        rubric,
                        0,
                        List.of());
        PlanConfidence conf =
                new PlanConfidence("HIGH", "", PlanReadinessStatus.READY, t, 0.9, List.of());
        FeaturePlanState gated =
                store.getByContextId("c2")
                        .orElseThrow()
                        .withPacketPosted(t, "", "fp", 1)
                        .withPlanCritiqueSnapshot(snap)
                        .withPlanConfidence(conf);
        store.update(gated);
        var action = new PersistPlanApprovalAction(store);
        Object r = action.run(
                new Event("discord", "message", Map.of()),
                Map.of(
                        "contextId",
                        "c2",
                        "planApprovalDecision",
                        "approve",
                        "planningPacketPostedVersion",
                        "1"),
                Map.of("__event", Map.of("authorId", "u9")));
        assertEquals("OK", r);
        assertEquals(PlanApprovalStatus.APPROVE, store.getByContextId("c2").orElseThrow().getPlanApproval().getStatus());
        assertEquals("u9", store.getByContextId("c2").orElseThrow().getPlanApproval().getActorId());
    }

    @Test
    void buildPlanningThreadReviewBody_spreadsArtifactSummaries() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "c3", "channelId", "room3"),
                        Map.of("profileId", "software_feature_planning")));

        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        assertEquals("OK", upsert.run(null,
                Map.of("contextId", "c3"),
                Map.of(
                        "artifactId", "overall_plan",
                        "sectionId", "outline",
                        "mode", "replace",
                        "data", Map.of("plan_body", "Step one; step two."))));
        assertEquals("OK", upsert.run(null,
                Map.of("contextId", "c3"),
                Map.of(
                        "artifactId", "validation_plan",
                        "sectionId", "checks",
                        "mode", "replace",
                        "data", Map.of("validation_notes", "Run mvn test and mvn compile; run npm validate scripts when specs change."))));

        var build = new BuildPlanningThreadReviewBodyAction(store);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) build.run(null, Map.of("contextId", "c3"), Map.of());
        assertEquals("", spread.get("planningThreadReviewBuildError"));
        String body = (String) spread.get("planningThreadReviewBody");
        assertTrue(body.contains("Proposed behavior / outline"));
        assertTrue(body.contains("Step one; step two."));
        assertTrue(body.contains("Validation strategy"));
        assertTrue(body.contains("Run mvn test"));
    }
}
