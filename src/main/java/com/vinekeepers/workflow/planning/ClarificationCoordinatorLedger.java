package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.ClarificationEngineAssessor.AssessedGap;

import java.util.List;

/**
 * Canonical JSON snapshot of post-draft clarification assessment for {@link FeaturePlanState} (packet, readiness, audit).
 */
public final class ClarificationCoordinatorLedger {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ClarificationCoordinatorLedger() {}

    /**
     * Persists a ledger JSON blob on the plan from the latest assessed gaps and optional top ask.
     */
    public static FeaturePlanState persist(
            FeaturePlanStateStore store,
            String contextId,
            FeaturePlanState plan,
            List<AssessedGap> assessed,
            CoordinatorClarificationGapEvaluator.OpenGap topAsk) {
        if (store == null || contextId == null || contextId.isBlank() || plan == null) {
            return plan;
        }
        String json = toJson(assessed, topAsk);
        FeaturePlanState next = plan.withClarificationCoordinatorLedgerJson(json);
        store.update(next);
        return store.getByContextId(contextId).orElse(next);
    }

    public static String toJson(List<AssessedGap> assessed, CoordinatorClarificationGapEvaluator.OpenGap topAsk) {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", 1);
        ArrayNode needs = root.putArray("needs");
        if (assessed != null) {
            for (AssessedGap ag : assessed) {
                ObjectNode n = needs.addObject();
                CoordinatorClarificationGapEvaluator.OpenGap g = ag.gap();
                n.put("gapId", g.gapId());
                n.put("blocking", g.blocking());
                n.put("decision", ag.decision().name());
                n.put("rankScore", ag.rankScore());
                n.put(
                        "questionPreview",
                        g.questionText() != null ? truncate(g.questionText(), 400) : "");
            }
        }
        if (topAsk != null) {
            ObjectNode top = root.putObject("nextAsk");
            top.put("gapId", topAsk.gapId());
            top.put("blocking", topAsk.blocking());
            top.put(
                    "questionPreview",
                    topAsk.questionText() != null ? truncate(topAsk.questionText(), 400) : "");
        } else {
            root.putNull("nextAsk");
        }
        root.put("assessedAt", java.time.Instant.now().toString());
        try {
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"version\":1,\"needs\":[],\"nextAsk\":null}";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
