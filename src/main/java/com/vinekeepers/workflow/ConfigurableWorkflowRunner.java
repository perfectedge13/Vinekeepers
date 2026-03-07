package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interprets a workflow definition from config: executes steps in order, updates state, persists via StateStore.
 */
public final class ConfigurableWorkflowRunner implements WorkflowRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableWorkflowRunner.class);

    private final WorkflowDefinition definition;
    private final WorkflowActionRegistry actionRegistry;
    private final List<WorkflowStep> steps;

    public ConfigurableWorkflowRunner(WorkflowDefinition definition, WorkflowActionRegistry actionRegistry) {
        this.definition = definition != null ? definition : new WorkflowDefinition("", List.of());
        this.actionRegistry = actionRegistry != null ? actionRegistry : new WorkflowActionRegistry();
        this.steps = buildSteps(this.definition, this.actionRegistry);
    }

    @Override
    public String run(Event event, StateStore stateStore, String botId) {
        if (steps.isEmpty()) {
            return "";
        }
        String stateKey = stateKey(botId, event);
        ConfigurableWorkflowState state = stateStore.get(stateKey, ConfigurableWorkflowState.class)
                .orElseGet(ConfigurableWorkflowState::new);
        int maxSteps = 100;
        for (int i = 0; i < maxSteps; i++) {
            if (state.getStepIndex() >= steps.size()) {
                state.setStepIndex(0);
            }
            WorkflowStep step = steps.get(state.getStepIndex());
            StepResult result = step.execute(event, state, state.getStepIndex());
            if (result.getStoreIn() != null && result.getStoreValue() != null) {
                state.put(result.getStoreIn(), result.getStoreValue());
            }
            if (result.getNextStepIndex() != null) {
                state.setStepIndex(result.getNextStepIndex());
            } else {
                state.setStepIndex(state.getStepIndex() + 1);
            }
            if (result.isDone()) {
                stateStore.put(stateKey, state);
                return result.getMessage();
            }
        }
        stateStore.put(stateKey, state);
        return "Workflow step limit reached.";
    }

    private static String stateKey(String botId, Event event) {
        if (event != null && event.getSourceId() != null && event.getSourceId().startsWith("discord:")) {
            String channelId = event.getPayload("channelId", String.class);
            if (channelId == null) channelId = event.getPayload("channel", String.class);
            if (channelId != null && !channelId.isEmpty()) {
                return "bot:" + botId + ":conv:" + channelId;
            }
        }
        return "bot:" + botId + ":state";
    }

    @SuppressWarnings("unchecked")
    private static List<WorkflowStep> buildSteps(WorkflowDefinition definition, WorkflowActionRegistry registry) {
        List<WorkflowStep> out = new ArrayList<>();
        for (Map<String, Object> stepMap : definition.getSteps()) {
            String type = (String) stepMap.get("type");
            if (type == null) type = "done";
            switch (type) {
                case "ask_input" -> out.add(new com.vinekeepers.workflow.steps.AskForInputStep(
                        (String) stepMap.get("prompt"),
                        (String) stepMap.get("storeIn")));
                case "call_action" -> out.add(new com.vinekeepers.workflow.steps.CallActionStep(
                        registry,
                        (String) stepMap.get("action"),
                        (Map<String, Object>) stepMap.get("bind"),
                        (String) stepMap.get("storeIn")));
                case "branch" -> out.add(new com.vinekeepers.workflow.steps.BranchStep(
                        (List<Map<String, Object>>) stepMap.get("branches")));
                case "done" -> out.add(new com.vinekeepers.workflow.steps.DoneStep(
                        (String) stepMap.get("message")));
                default -> out.add(new com.vinekeepers.workflow.steps.DoneStep("Unknown step type: " + type));
            }
        }
        return out;
    }
}
