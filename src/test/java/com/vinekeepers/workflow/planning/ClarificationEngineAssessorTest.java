package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationEnginePolicy;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.state.planning.ClarificationResolutionDecision;
import com.vinekeepers.state.planning.FeaturePlanState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClarificationEngineAssessorTest {

    @Test
    void assessReturnsEmptyWhenSemanticGapsDisallowed() {
        CoordinatorClarificationSettings settings = canonicalSettings(simpleGap(false));
        FeaturePlanState plan = basePlan("ctx", "config versus runtime tradeoff");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), false, null, false);
        assertTrue(out.isEmpty());
    }

    @Test
    void nonBlockingLowRepoEvidenceAsksUser() {
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(5, 0.45, 0.0, true, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(simpleGap(false)), pol);
        FeaturePlanState plan = basePlan("ctx", "config versus runtime tradeoff");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, null, false);
        assertEquals(1, out.size());
        assertEquals(ClarificationResolutionDecision.ASK_USER, out.get(0).decision());
    }

    @Test
    void nonBlockingStrongRepoEvidenceAssumesWhenAllowed() {
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(5, 0.45, 0.0, true, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(simpleGap(false)), pol);
        FeaturePlanState plan =
                basePlan("ctx", "config versus runtime tradeoff")
                        .withWorkspaceLinkage("ws1", "MATERIALIZED", "/repo/worktree", "");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, null, false);
        assertEquals(1, out.size());
        assertEquals(ClarificationResolutionDecision.ASSUME_AND_CONTINUE, out.get(0).decision());
        assertTrue(out.get(0).assumptionToRecord().contains("g_nb"));
    }

    @Test
    void budgetExhaustedNonBlockingAssumesWhenPolicyAllows() {
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(2, 0.45, 0.0, true, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(simpleGap(false)), pol);
        FeaturePlanState plan =
                basePlan("ctx", "config versus runtime tradeoff")
                        .withClarificationQuestionSurfaced("q1")
                        .withClarificationQuestionSurfaced("q2");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, null, false);
        assertEquals(1, out.size());
        assertEquals(ClarificationResolutionDecision.ASSUME_AND_CONTINUE, out.get(0).decision());
    }

    @Test
    void budgetExhaustedNonBlockingDefersWhenAssumeDisabled() {
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(1, 0.45, 0.0, false, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(simpleGap(false)), pol);
        FeaturePlanState plan =
                basePlan("ctx", "config versus runtime tradeoff").withClarificationQuestionSurfaced("q1");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, null, false);
        assertEquals(1, out.size());
        assertEquals(ClarificationResolutionDecision.LOW_PRIORITY_DEFER, out.get(0).decision());
    }

    @Test
    void budgetExhaustedBlockingYieldsBlockAsUnimplementable() {
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(1, 0.45, 0.0, true, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1, List.of(simpleGap(true)), pol);
        FeaturePlanState plan =
                basePlan("ctx", "config versus runtime tradeoff").withClarificationQuestionSurfaced("q1");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, null, false);
        assertEquals(1, out.size());
        assertEquals(ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE, out.get(0).decision());
        assertTrue(out.get(0).assumptionToRecord().contains("budget exhausted"));
    }

    @Test
    void perGapBudgetExhaustedUsesExplicitGapLimit() {
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(10, 1, 2, 0.72, 0.45, 0.0, true, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(simpleGap(false)), pol);
        FeaturePlanState plan =
                basePlan("ctx", "config versus runtime tradeoff").withClarificationQuestionSurfaced("ask:g_nb:1");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, null, false);
        assertEquals(1, out.size());
        assertEquals(ClarificationResolutionDecision.ASSUME_AND_CONTINUE, out.get(0).decision());
    }

    @Test
    void critiqueFollowUpLowersAskThresholdSoMarginalEvidenceCanAssume() {
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(5, 0.55, 0.0, true, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(simpleGap(false)), pol);
        String json =
                "{\"localPathPresent\":true,\"workspaceStatus\":\"MATERIALIZED\",\"blockingIssues\":0}";
        FeaturePlanState plan =
                basePlan("ctx", "config versus runtime tradeoff")
                        .withWorkspaceLinkage("ws1", null, "/tmp/plan-repo", "");
        List<ClarificationEngineAssessor.AssessedGap> withoutCritique =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, json, false);
        List<ClarificationEngineAssessor.AssessedGap> withCritique =
                ClarificationEngineAssessor.assessCanonicalGaps(
                        plan, settings, List.of("Should we use config or runtime?"), true, json, true);
        assertEquals(ClarificationResolutionDecision.ASK_USER, withoutCritique.get(0).decision());
        assertEquals(ClarificationResolutionDecision.ASSUME_AND_CONTINUE, withCritique.get(0).decision());
    }

    @Test
    void repoEvidenceGroundingScoreIncludesJsonBonus() {
        FeaturePlanState plan = basePlan("ctx", "x");
        double bare = ClarificationEngineAssessor.repoEvidenceGroundingScore(plan, null);
        String json =
                "{\"localPathPresent\":true,\"workspaceStatus\":\"MATERIALIZED\",\"blockingIssues\":0}";
        double withJson = ClarificationEngineAssessor.repoEvidenceGroundingScore(plan, json);
        assertTrue(withJson > bare);
    }

    @Test
    void blockingModelRoutingGapStillAsksUserWithStrongRepoEvidence() {
        CoordinatorClarificationGapRule gap =
                new CoordinatorClarificationGapRule(
                        "model_override_granularity",
                        true,
                        "Should overrides be per named workflow step, step type, or both?",
                        List.of("override", "step"),
                        List.of("model", "step"),
                        List.of("named steps", "step types", "both"));
        CoordinatorClarificationEnginePolicy pol =
                new CoordinatorClarificationEnginePolicy(5, 0.10, 0.0, true, true);
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(gap), pol);
        FeaturePlanState plan =
                basePlan("ctx", "Lets plug different models into different workflow steps.")
                        .withWorkspaceLinkage("ws1", "MATERIALIZED", "/repo/worktree", "");
        List<ClarificationEngineAssessor.AssessedGap> out =
                ClarificationEngineAssessor.assessCanonicalGaps(plan, settings, List.of(), true, null, false);
        assertEquals(1, out.size());
        assertEquals(ClarificationResolutionDecision.ASK_USER, out.get(0).decision());
    }

    private static CoordinatorClarificationGapRule simpleGap(boolean blocking) {
        return new CoordinatorClarificationGapRule(
                blocking ? "g_blk" : "g_nb",
                blocking,
                blocking ? "Blocking: pick config or runtime?" : "Non-blocking: prefer config or runtime?",
                List.of("config", "runtime"),
                List.of(),
                List.of());
    }

    private static CoordinatorClarificationSettings canonicalSettings(CoordinatorClarificationGapRule rule) {
        return new CoordinatorClarificationSettings(
                CoordinatorClarificationMode.CANONICAL_V1,
                List.of(rule),
                CoordinatorClarificationEnginePolicy.defaultPolicy());
    }

    private static FeaturePlanState basePlan(String contextId, String request) {
        return new FeaturePlanState(
                contextId,
                "f1",
                "slug",
                "room",
                "thread",
                null,
                "t",
                request,
                "PLANNING",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
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
                Instant.now(),
                Instant.now());
    }
}
