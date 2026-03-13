package com.vinekeepers.workflow.actions;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.workflow.actions.CreateThreadAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateLifecycleContextActionTest {

    @Test
    void runReturnsErrorWhenStoreNull() {
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(null);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of("channelId", "chan-1"));
        assertEquals("Lifecycle context store not available.", result);
    }

    @Test
    void runReturnsErrorWhenChannelIdMissing() {
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(new LifecycleContextStore());
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals("Missing channelId for create_lifecycle_context.", result);
    }

    @Test
    void runCreatesContextAndStoresInStore() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        Object result = action.run(null,
                Map.of("channelId", "chan-1"),
                Map.of("channelId", "chan-1"));
        assertTrue(result instanceof String);
        String contextId = (String) result;
        assertTrue(contextId.startsWith("ctx-"));
        assertTrue(store.getByContextId(contextId).isPresent());
        LifecycleContext ctx = store.getByContextId(contextId).orElseThrow();
        assertEquals("chan-1", ctx.getChannelId());
        assertTrue(store.getByChannelId("chan-1").isPresent());
    }

    @Test
    void runSetsConfiguredBotIdRuntimeBotInstanceIdRepoRequestTextFromBindOrState() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null,
                Map.of("channelId", "chan-2", "configuredBotId", "arrietty", "instanceId", "arrietty-abc12345",
                        "project", "https://github.com/owner/repo", "codeChange", "Phase 1 tests"),
                Map.of("channelId", "chan-2"));
        LifecycleContext ctx = store.getByChannelId("chan-2").orElseThrow();
        assertEquals("arrietty", ctx.getConfiguredBotId());
        assertEquals("arrietty-abc12345", ctx.getRuntimeBotInstanceId());
        assertEquals("https://github.com/owner/repo", ctx.getRepo());
        assertEquals("Phase 1 tests", ctx.getRequestText());
    }

    @Test
    void runSetsStatusToProvisioningByDefault() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null, Map.of("channelId", "chan-status"), Map.of("channelId", "chan-status"));
        LifecycleContext ctx = store.getByChannelId("chan-status").orElseThrow();
        assertEquals("provisioning", ctx.getStatus());
    }

    @Test
    void runPrefersBindOverStateForNewFields() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null,
                Map.of("channelId", "chan-3", "configuredBotId", "from-state", "project", "from-state-repo", "codeChange", "from-state-req"),
                Map.of("channelId", "chan-3", "configuredBotId", "from-bind", "repo", "from-bind-repo", "requestText", "from-bind-req", "runtimeBotInstanceId", "from-bind-inst"));
        LifecycleContext ctx = store.getByChannelId("chan-3").orElseThrow();
        assertEquals("from-bind", ctx.getConfiguredBotId());
        assertEquals("from-bind-inst", ctx.getRuntimeBotInstanceId());
        assertEquals("from-bind-repo", ctx.getRepo());
        assertEquals("from-bind-req", ctx.getRequestText());
    }

    @Test
    void runConfiguredBotIdBindPrecedenceBindWinsOverState() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null,
                Map.of("channelId", "chan-4", "configuredBotId", "state-bot-id"),
                Map.of("channelId", "chan-4", "configuredBotId", "arrietty"));
        LifecycleContext ctx = store.getByChannelId("chan-4").orElseThrow();
        assertEquals("arrietty", ctx.getConfiguredBotId(), "configuredBotId from bind must override state");
    }

    @Test
    void runUsesChannelIdFromStateWhenNotInBind() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null, Map.of("channelId", "chan-from-state"), Map.of());
        assertTrue(store.getByChannelId("chan-from-state").isPresent());
    }

    @Test
    void runStoresDeliveryChannelIdFromBindOrState() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null,
                Map.of("channelId", "chan-1", "deliveryChannelId", "thread-abc"),
                Map.of());
        LifecycleContext ctx = store.getByChannelId("chan-1").orElseThrow();
        assertEquals("thread-abc", ctx.getDeliveryChannelId());
    }

    @Test
    void runPrefersBindDeliveryChannelIdOverState() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null,
                Map.of("channelId", "chan-1", "deliveryChannelId", "thread-state"),
                Map.of("deliveryChannelId", "thread-bind"));
        LifecycleContext ctx = store.getByChannelId("chan-1").orElseThrow();
        assertEquals("thread-bind", ctx.getDeliveryChannelId());
    }

    @Test
    void runSetsDeliveryChannelIdNullWhenSentinelThreadCreateFailed() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null,
                Map.of("channelId", "chan-1", "deliveryChannelId", CreateThreadAction.THREAD_CREATE_FAILED),
                Map.of());
        LifecycleContext ctx = store.getByChannelId("chan-1").orElseThrow();
        assertEquals(null, ctx.getDeliveryChannelId());
    }

    @Test
    void runSetsDeliveryChannelIdNullWhenBindHasSentinelStateHasThreadId() {
        LifecycleContextStore store = new LifecycleContextStore();
        CreateLifecycleContextAction action = new CreateLifecycleContextAction(store);
        action.run(null,
                Map.of("channelId", "chan-1", "deliveryChannelId", "thread-ok"),
                Map.of("deliveryChannelId", CreateThreadAction.THREAD_CREATE_FAILED));
        LifecycleContext ctx = store.getByChannelId("chan-1").orElseThrow();
        assertEquals(null, ctx.getDeliveryChannelId());
    }
}
