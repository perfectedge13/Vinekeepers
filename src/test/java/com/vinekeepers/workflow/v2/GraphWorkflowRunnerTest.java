package com.vinekeepers.workflow.v2;

import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphWorkflowRunnerTest {

    @Test
    void runsPipelineAndCompletes() {
        WorkflowActionRegistry reg = new WorkflowActionRegistry();
        reg.register("v2_noop", (e, s, b) -> Map.of("v2NoopRan", "true"));

        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("workflowSchema", "v2");
        wf.put("entryPhase", "start");
        Map<String, Object> phases = new LinkedHashMap<>();
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("pipeline", List.of("c1", "c2"));
        start.put("defaultNextPhase", "done");
        phases.put("start", start);
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("pipeline", List.of());
        done.put("terminal", true);
        phases.put("done", done);
        wf.put("phases", phases);
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put(
                "c1",
                Map.of("kind", "legacy_action", "action", "v2_noop", "storeSpread", true));
        caps.put(
                "c2",
                Map.of("kind", "legacy_action", "action", "v2_noop", "storeSpread", true));
        wf.put("capabilities", caps);
        wf.put(
                "deliberation",
                Map.of("profileHint", "runner_hint", "label", "runner_l"));

        WorkflowV2Model model = WorkflowV2Loader.load("t", wf);
        GraphWorkflowRunner runner =
                new GraphWorkflowRunner(
                        model, reg, null, ToolPolicy.allowAll(), ConversationMode.SINGLE_EVENT, "channel");

        Event event =
                new Event(
                        "discord:x",
                        "message",
                        Map.of("channelId", "ch1", "authorId", "u1", "content", "hi"));

        StateStore store = new StateStore();
        WorkflowRunResult res = runner.runResult(event, store, "b");
        assertTrue(res.isCompleted());
        String key = "bot:b:conv:ch1";
        var st = store.get(key, com.vinekeepers.workflow.ConfigurableWorkflowState.class);
        assertTrue(st.isPresent());
        assertEquals("true", st.get().get("v2NoopRan"));
        assertEquals("runner_hint", st.get().get("deliberationProfileHint"));
        assertEquals("runner_l", st.get().get("deliberationLabel"));
    }

    @Test
    void linearWorkflowRefRunsDelegatedLinearWorkflow() {
        WorkflowActionRegistry reg = new WorkflowActionRegistry();
        reg.register("v2_noop", (e, s, b) -> Map.of("linearDelegateRan", "true"));

        Map<String, Object> linear = new LinkedHashMap<>();
        linear.put(
                "steps",
                List.of(Map.of("type", "call_action", "action", "v2_noop", "storeSpread", true)));

        Map<String, Object> workflows = new LinkedHashMap<>();
        workflows.put("inner_linear", linear);

        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("workflowSchema", "v2");
        wf.put("entryPhase", "p");
        Map<String, Object> phases = new LinkedHashMap<>();
        phases.put("p", Map.of("pipeline", List.of("cap1"), "defaultNextPhase", "done"));
        phases.put("done", Map.of("pipeline", List.of(), "terminal", true));
        wf.put("phases", phases);
        wf.put(
                "capabilities",
                Map.of("cap1", Map.of("kind", "linear_workflow_ref", "workflowRef", "inner_linear")));

        WorkflowV2Model model = WorkflowV2Loader.load("t", wf);
        GraphWorkflowRunner runner =
                new GraphWorkflowRunner(
                        model,
                        reg,
                        null,
                        ToolPolicy.allowAll(),
                        ConversationMode.SINGLE_EVENT,
                        "channel",
                        workflows,
                        null);

        Event event =
                new Event(
                        "discord:x",
                        "message",
                        Map.of("channelId", "ch1", "authorId", "u1", "content", "hi"));

        StateStore store = new StateStore();
        WorkflowRunResult res = runner.runResult(event, store, "b");
        assertTrue(res.isCompleted());
        String key = "bot:b:conv:ch1";
        var st = store.get(key, com.vinekeepers.workflow.ConfigurableWorkflowState.class);
        assertTrue(st.isPresent());
        assertEquals("true", st.get().get("linearDelegateRan"));
    }

    @Test
    void linearWorkflowRefForwardsNonEmptyDoneMessageInsteadOfDroppingToEmptyOuterCompletion() {
        WorkflowActionRegistry reg = new WorkflowActionRegistry();

        Map<String, Object> linear = new LinkedHashMap<>();
        linear.put(
                "steps",
                List.of(Map.of("type", "done", "message", "Planning rejected; not launching.")));

        Map<String, Object> workflows = new LinkedHashMap<>();
        workflows.put("inner_linear", linear);

        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("workflowSchema", "v2");
        wf.put("entryPhase", "p");
        Map<String, Object> phases = new LinkedHashMap<>();
        phases.put("p", Map.of("pipeline", List.of("cap1"), "defaultNextPhase", "done"));
        phases.put("done", Map.of("pipeline", List.of(), "terminal", true));
        wf.put("phases", phases);
        wf.put(
                "capabilities",
                Map.of("cap1", Map.of("kind", "linear_workflow_ref", "workflowRef", "inner_linear")));

        WorkflowV2Model model = WorkflowV2Loader.load("t", wf);
        GraphWorkflowRunner runner =
                new GraphWorkflowRunner(
                        model,
                        reg,
                        null,
                        ToolPolicy.allowAll(),
                        ConversationMode.SINGLE_EVENT,
                        "channel",
                        workflows,
                        null);

        Event event =
                new Event(
                        "discord:x",
                        "message",
                        Map.of("channelId", "ch1", "authorId", "u1", "content", "hi"));

        StateStore store = new StateStore();
        WorkflowRunResult res = runner.runResult(event, store, "b");
        assertTrue(res.isCompleted());
        assertEquals("Planning rejected; not launching.", res.getReplyMessage());
        String key = "bot:b:conv:ch1";
        var st = store.get(key, com.vinekeepers.workflow.ConfigurableWorkflowState.class);
        assertTrue(st.isPresent());
        assertEquals("__v2_done", st.get().get(GraphWorkflowRunner.PHASE_KEY));
    }
}
