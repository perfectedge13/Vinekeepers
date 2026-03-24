package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatePlanningPacketDepthActionTest {

    private static final String RICH_FEATURE =
            "Ship incremental login improvements with session hardening distinct from the legacy cookie path.";

    @Test
    void usesRelaxWhenCanonicalClearAndLedgerNonBlocking() {
        FeaturePlanStateStore plans = new FeaturePlanStateStore();
        plans.put(planWithRelaxedOpenQuestionScenario("ctx-relax"));
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        var action = new EvaluatePlanningPacketDepthAction(plans, reg);

        UnresolvedItem normal =
                new UnresolvedItem(
                        "uq_n",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Assumption-grade gap",
                        "normal",
                        Map.of("channel", "planning_clarification", "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(normal);
        Map<String, Object> state = new LinkedHashMap<>();
        ledger.putInto(state);
        state.put("planningCanonicalUserInputRequired", "false");
        state.put("planningUserInputRequired", "false");

        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>)
                        action.run(
                                new Event("discord:x", "message", Map.of()),
                                state,
                                Map.of("contextId", "ctx-relax"));
        assertEquals("true", spread.get("planningPacketDepthOk"));
        assertFalse(spread.containsKey("planningReviewReady"));
        assertTrue(String.valueOf(spread.get("planningReviewReadyReason")).contains("Final review readiness"));
    }

    @Test
    void canonicalUserInputRequiredDoesNotReintroduceOpenQuestionDepthFailure() {
        FeaturePlanStateStore plans = new FeaturePlanStateStore();
        plans.put(planWithRelaxedOpenQuestionScenario("ctx-block"));
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        var action = new EvaluatePlanningPacketDepthAction(plans, reg);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("planningCanonicalUserInputRequired", "true");
        state.put("planningUserInputRequired", "true");

        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>)
                        action.run(
                                new Event("discord:x", "message", Map.of()),
                                state,
                                Map.of("contextId", "ctx-block"));
        assertEquals("true", spread.get("planningPacketDepthOk"));
        assertFalse(spread.containsKey("planningReviewReady"));
        assertFalse(String.valueOf(spread.get("planningPacketDepthReason")).contains("Open questions"));
    }

    /**
     * Same shape as {@link com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluatorTest}: canonical unresolved
     * questions stay on governance state, not in a packet artifact.
     */
    private static FeaturePlanState planWithRelaxedOpenQuestionScenario(String contextId) {
        String exploration = "word ".repeat(30);
        String narrative = "state ".repeat(20);
        Map<String, Object> reqValues = new LinkedHashMap<>();
        reqValues.put("feature_summary", RICH_FEATURE);
        reqValues.put("current_state_summary", narrative);
        SectionState reqSec = new SectionState("narrative", SectionState.STATUS_DRAFT, reqValues, List.of());
        ArtifactState reqArt = new ArtifactState("requirements_spec", Map.of("narrative", reqSec));

        Map<String, Object> exValues = new LinkedHashMap<>();
        exValues.put("exploration_body", exploration);
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

        FeaturePlanState base =
                new FeaturePlanState(
                        contextId,
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        return base.withArtifacts(arts)
                .withGovernanceRecords(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("Which OAuth flow applies for this API?"));
    }
}
