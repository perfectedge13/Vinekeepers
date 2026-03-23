package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.rag.PlanningRepoGroundingService;

import java.util.Map;

/**
 * After {@link EnsureRepoWorkspaceAction}, loads {@code .vinekeepers/repo-grounding.parquet} when present, indexes
 * missing chunks into Qdrant (OpenAI embeddings), exports merged Parquet, and spreads retrieval text for planning LLM
 * steps. Fails gracefully when optional services are unavailable.
 */
public final class PrepPlanningRepoGroundingAction implements com.vinekeepers.workflow.WorkflowAction {

    private final PlanningRepoGroundingService service;
    private final FeaturePlanStateStore planStateStore;

    public PrepPlanningRepoGroundingAction(OpenAiChatClient openAi, FeaturePlanStateStore planStateStore) {
        this.service = new PlanningRepoGroundingService(openAi);
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            return Map.of("planningRagError", "NO_PLAN_STORE");
        }
        String contextId = firstNonBlank(get(bind, "contextId"), get(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return Map.of("planningRagError", "NO_CONTEXT");
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        return service.run(state, bind, plan);
    }

    private static String get(Map<String, Object> m, String k) {
        if (m == null) {
            return null;
        }
        Object v = m.get(k);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
