package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;

import java.util.List;

/**
 * Canonical JSON snapshot of post-draft clarification assessment for {@link FeaturePlanState} (packet, readiness, audit).
 */
public final class ClarificationCoordinatorLedger {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ClarificationCoordinatorLedger() {}

    public static FeaturePlanState persist(
            FeaturePlanStateStore store,
            String contextId,
            FeaturePlanState plan,
            List<CanonicalPlanningGapEngine.AssessedGap> assessed,
            CanonicalPlanningGap topAsk) {
        if (store == null || contextId == null || contextId.isBlank() || plan == null) {
            return plan;
        }
        String json = toJson(assessed, topAsk);
        FeaturePlanState next = plan.withClarificationCoordinatorLedgerJson(json);
        store.update(next);
        return store.getByContextId(contextId).orElse(next);
    }

    public static String toJson(List<CanonicalPlanningGapEngine.AssessedGap> assessed, CanonicalPlanningGap topAsk) {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", 1);
        ArrayNode needs = root.putArray("needs");
        if (assessed != null) {
            for (CanonicalPlanningGapEngine.AssessedGap ag : assessed) {
                ObjectNode n = needs.addObject();
                CanonicalPlanningGap g = ag.gap();
                n.put("gapId", g.gapId());
                n.put("blocking", g.blocking());
                n.put("decision", ag.decision().name());
                n.put("kind", g.kind().name());
                n.put(
                        "questionPreview",
                        g.questionSeed() != null ? truncate(g.questionSeed(), 400) : "");
            }
        }
        if (topAsk != null) {
            ObjectNode top = root.putObject("nextAsk");
            top.put("gapId", topAsk.gapId());
            top.put("blocking", topAsk.blocking());
            top.put(
                    "questionPreview",
                    topAsk.questionSeed() != null ? truncate(topAsk.questionSeed(), 400) : "");
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
