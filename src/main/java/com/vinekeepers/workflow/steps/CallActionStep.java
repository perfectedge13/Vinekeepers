package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStepModel;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step that invokes a registered action by id with bound arguments; can store result.
 */
public final class CallActionStep implements WorkflowStep {

    private final WorkflowActionRegistry registry;
    private final ToolRunner toolRunner;
    private final ToolPolicy toolPolicy;
    private final String actionId;
    private final Map<String, Object> bind;
    private final String storeIn;
    private final WorkflowStepModel stepModel;

    public CallActionStep(WorkflowActionRegistry registry, String actionId, Map<String, Object> bind, String storeIn) {
        this(registry, null, null, actionId, bind, storeIn, null);
    }

    public CallActionStep(WorkflowActionRegistry registry, ToolRunner toolRunner, ToolPolicy toolPolicy,
                          String actionId, Map<String, Object> bind, String storeIn) {
        this(registry, toolRunner, toolPolicy, actionId, bind, storeIn, null);
    }

    public CallActionStep(WorkflowActionRegistry registry, ToolRunner toolRunner, ToolPolicy toolPolicy,
                          String actionId, Map<String, Object> bind, String storeIn, WorkflowStepModel stepModel) {
        this.registry = registry != null ? registry : new WorkflowActionRegistry();
        this.toolRunner = toolRunner;
        this.toolPolicy = toolPolicy;
        this.actionId = actionId != null ? actionId : "";
        this.bind = bind != null ? Map.copyOf(bind) : Map.of();
        this.storeIn = storeIn;
        this.stepModel = stepModel;
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        Map<String, Object> args = buildArgs(event, state);
        Object result;
        if (toolRunner != null && toolRunner.hasTool(actionId)) {
            result = toolRunner.run(actionId, args, toolPolicy);
        } else {
            result = registry.run(actionId, event, state != null ? state.getData() : null, args);
        }
        return StepResult.advance(storeIn, result);
    }

    private Map<String, Object> buildArgs(Event event, ConfigurableWorkflowState state) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (state != null) {
            args.putAll(state.getData());
        }
        args.put("__event", buildEventMetadata(event));
        if (stepModel != null && !stepModel.isEmpty()) {
            args.put("__stepModel", stepModel.toMap());
        }
        for (Map.Entry<String, Object> entry : bind.entrySet()) {
            args.put(entry.getKey(), resolve(entry.getValue(), state));
        }
        return args;
    }

    private Map<String, Object> buildEventMetadata(Event event) {
        if (event == null) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>(event.getPayload());
        metadata.put("sourceId", event.getSourceId());
        metadata.put("kind", event.getKind());
        return metadata;
    }

    private Object resolve(Object value, ConfigurableWorkflowState state) {
        if (!(value instanceof String text)) {
            return value;
        }
        if (text.startsWith("{{") && text.endsWith("}}")) {
            String key = text.substring(2, text.length() - 2).trim();
            return state != null ? state.get(key) : null;
        }
        return text;
    }
}
