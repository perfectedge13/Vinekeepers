package com.vinekeepers.workflow.planning.rag;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.state.planning.FeaturePlanState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock(value = "planning-rag-env-props", mode = ResourceAccessMode.READ_WRITE)
class PlanningRepoGroundingServiceTest {

    private static final String[] KEYS = {
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
    void clearProps() {
        for (String k : KEYS) {
            System.clearProperty(k);
        }
    }

    @Test
    void featureDisabled_returnsDisabledToggle() {
        System.setProperty("VINEKEEPERS_PLANNING_RAG", "false");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "x", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        Map<String, Object> out = svc.run(Map.of(), Map.of(), plan("MATERIALIZED", "/tmp"));
        assertEquals("disabled_toggle", out.get("planningRagSource"));
    }

    @Test
    void missingQdrant_returnsDisabledConfig() {
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "key", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        Map<String, Object> out = svc.run(Map.of(), Map.of(), plan("MATERIALIZED", "/tmp"));
        assertEquals("disabled_config", out.get("planningRagSource"));
        assertEquals("QDRANT_URL not set", out.get("planningRagError"));
        assertEquals("false", out.get("planningRagEnabled"));
    }

    @Test
    void missingOpenAiKey_returnsDisabledConfig() {
        System.setProperty("QDRANT_URL", "http://localhost:6333");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        Map<String, Object> out = svc.run(Map.of(), Map.of(), plan("MATERIALIZED", "/tmp"));
        assertEquals("disabled_config", out.get("planningRagSource"));
        assertEquals("OPENAI_API_KEY not set", out.get("planningRagError"));
    }

    @Test
    void nullPlan_returnsNoPlan() {
        System.setProperty("QDRANT_URL", "http://localhost:6333");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "k", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        Map<String, Object> out = svc.run(Map.of(), Map.of(), null);
        assertEquals("NO_PLAN", out.get("planningRagError"));
    }

    @Test
    void workspaceNotReady_returnsWorkSpaceNotReady() {
        System.setProperty("QDRANT_URL", "http://localhost:6333");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "k", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        Map<String, Object> out = svc.run(Map.of(), Map.of(), plan("GATHERING_CONTEXT", "/tmp"));
        assertEquals("WORKSPACE_NOT_READY", out.get("planningRagError"));
        assertEquals("no_workspace", out.get("planningRagSource"));
    }

    @Test
    void blankLocalPath_returnsNoLocalPath() {
        System.setProperty("QDRANT_URL", "http://localhost:6333");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "k", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        Map<String, Object> out = svc.run(Map.of(), Map.of(), plan("MATERIALIZED", "  "));
        assertEquals("NO_LOCAL_PATH", out.get("planningRagError"));
    }

    @Test
    void localPathNotDirectory_returnsError(@TempDir Path tmp) throws Exception {
        System.setProperty("QDRANT_URL", "http://localhost:6333");
        Path file = tmp.resolve("not-dir");
        Files.writeString(file, "x");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "k", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        Map<String, Object> out = svc.run(Map.of(), Map.of(), plan("MATERIALIZED", file.toString()));
        assertEquals("LOCAL_PATH_NOT_DIR", out.get("planningRagError"));
    }

    @Test
    void emptyMaterializedRepo_noNetworkCallsWhenDepsOk(@TempDir Path repo) throws Exception {
        System.setProperty("QDRANT_URL", "http://127.0.0.1:6333");
        OpenAiChatClient ai = new OpenAiChatClient(HttpClient.newHttpClient(), "https://api.openai.com/v1", "fake-key-for-test", "gpt-4o-mini");
        var svc = new PlanningRepoGroundingService(ai);
        FeaturePlanState p = plan("MATERIALIZED", repo.toString());
        Map<String, Object> out = svc.run(Map.of("branch", "unit-test-branch"), Map.of(), p);
        assertEquals("indexed", out.get("planningRagSource"));
        assertEquals("0", out.get("planningRagIndexedNewCount"));
        assertEquals("", out.get("planningRagError"));
        assertEquals("true", out.get("planningRagAvailable"));
        assertEquals("0", out.get("planningRagParquetImportRows"));
    }

    private static FeaturePlanState plan(String workspaceStatus, String localPath) {
        return new FeaturePlanState(
                "ctx-1",
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
                workspaceStatus,
                localPath,
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
