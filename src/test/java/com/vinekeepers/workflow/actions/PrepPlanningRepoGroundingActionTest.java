package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.http.HttpClient;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock(value = "planning-rag-env-props", mode = ResourceAccessMode.READ_WRITE)
class PrepPlanningRepoGroundingActionTest {

    private static final String[] ENV_KEYS = {
        "VINEKEEPERS_PLANNING_RAG",
        "QDRANT_URL",
        "VINEKEEPERS_QDRANT_URL",
        "QDRANT_API_KEY",
        "VINEKEEPERS_QDRANT_API_KEY",
        "QDRANT_COLLECTION",
        "OPENAI_EMBEDDING_MODEL",
        "PLANNING_RAG_EMBED_BATCH_SIZE",
        "PLANNING_RAG_MAX_CHUNKS",
        "PLANNING_RAG_TOP_K",
        "OPENAI_EMBEDDING_DIMENSIONS"
    };

    @AfterEach
    void clearEnvProps() {
        for (String k : ENV_KEYS) {
            System.clearProperty(k);
        }
    }

    @Test
    void nullStore_returnsNoPlanStore() {
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "", "gpt-4o-mini");
        var action = new PrepPlanningRepoGroundingAction(ai, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(new Event("s", "k", Map.of()), Map.of("contextId", "x"), Map.of());
        assertEquals("NO_PLAN_STORE", out.get("planningRagError"));
    }

    @Test
    void missingContextId_returnsNoContext() {
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "", "gpt-4o-mini");
        var action = new PrepPlanningRepoGroundingAction(ai, new FeaturePlanStateStore());
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(new Event("s", "k", Map.of()), Map.of(), Map.of());
        assertEquals("NO_CONTEXT", out.get("planningRagError"));
    }

    @Test
    void missingPlan_returnsNoPlan() {
        System.setProperty("QDRANT_URL", "http://127.0.0.1:6333");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "unit-test-key", "gpt-4o-mini");
        var action = new PrepPlanningRepoGroundingAction(ai, new FeaturePlanStateStore());
        @SuppressWarnings("unchecked")
        Map<String, Object> out =
                (Map<String, Object>) action.run(new Event("s", "k", Map.of()), Map.of("contextId", "missing"), Map.of());
        assertEquals("NO_PLAN", out.get("planningRagError"));
    }

    @Test
    void contextIdFromBind_resolvesPlanFromBindMap() {
        System.setProperty("QDRANT_URL", "http://127.0.0.1:6333");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "unit-test-key", "gpt-4o-mini");
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.put(minimalPlan("from-bind"));
        var action = new PrepPlanningRepoGroundingAction(ai, store);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "k", Map.of()), Map.of(), Map.of("contextId", "from-bind"));
        assertEquals("NO_LOCAL_PATH", out.get("planningRagError"));
    }

    private static FeaturePlanState minimalPlan(String contextId) {
        return new FeaturePlanState(
                contextId,
                "f1",
                "feat-slug",
                "room-ch",
                null,
                "https://github.com/o/r",
                "",
                null,
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
                "ws-9",
                "MATERIALIZED",
                "",
                "",
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
}
