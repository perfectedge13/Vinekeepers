package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowLlmActions;
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
    private final boolean storeSpread;
    private final Map<String, Object> stepLlm;

    public CallActionStep(WorkflowActionRegistry registry, String actionId, Map<String, Object> bind, String storeIn) {
        this(registry, null, null, actionId, bind, storeIn, false, null);
    }

    public CallActionStep(WorkflowActionRegistry registry, ToolRunner toolRunner, ToolPolicy toolPolicy,
                          String actionId, Map<String, Object> bind, String storeIn) {
        this(registry, toolRunner, toolPolicy, actionId, bind, storeIn, false, null);
    }

    public CallActionStep(WorkflowActionRegistry registry, ToolRunner toolRunner, ToolPolicy toolPolicy,
                          String actionId, Map<String, Object> bind, String storeIn, boolean storeSpread) {
        this(registry, toolRunner, toolPolicy, actionId, bind, storeIn, storeSpread, null);
    }

    public CallActionStep(WorkflowActionRegistry registry, ToolRunner toolRunner, ToolPolicy toolPolicy,
                          String actionId, Map<String, Object> bind, String storeIn, boolean storeSpread,
                          Map<String, Object> stepLlm) {
        this.registry = registry != null ? registry : new WorkflowActionRegistry();
        this.toolRunner = toolRunner;
        this.toolPolicy = toolPolicy;
        this.actionId = actionId != null ? actionId : "";
        this.bind = bind != null ? Map.copyOf(bind) : Map.of();
        this.storeIn = storeIn;
        this.storeSpread = storeSpread;
        this.stepLlm = stepLlm != null && !stepLlm.isEmpty() ? Map.copyOf(stepLlm) : Map.of();
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
        if (storeSpread && result instanceof Map<?, ?> raw) {
            Map<String, Object> spread = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    spread.put(e.getKey().toString(), e.getValue());
                }
            }
            return StepResult.advanceSpread(spread);
        }
        return StepResult.advance(storeIn, result);
    }

    public String actionId() {
        return actionId;
    }

    public boolean storeSpread() {
        return storeSpread;
    }

    public String describeForLogs() {
        return "call_action:" + actionId + (storeSpread ? " (storeSpread)" : "");
    }

    private Map<String, Object> buildArgs(Event event, ConfigurableWorkflowState state) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (state != null) {
            args.putAll(state.getData());
        }
        args.put("__event", buildEventMetadata(event));
        for (Map.Entry<String, Object> entry : bind.entrySet()) {
            args.put(entry.getKey(), resolve(entry.getValue(), state));
        }
        if (WorkflowLlmActions.isLlmCapable(actionId)) {
            mergeLlmIntoArgs(args, state);
        }
        return args;
    }

    private void mergeLlmIntoArgs(Map<String, Object> args, ConfigurableWorkflowState state) {
        String model = firstNonBlank(stringFromMap(stepLlm, "model"), stringFromState(state, "workflowLlmModel"));
        if (model != null) {
            args.put("llmModel", model);
        }
        String provider = firstNonBlank(stringFromMap(stepLlm, "provider"), stringFromState(state, "workflowLlmProvider"));
        if (provider != null) {
            args.put("llmProvider", provider);
        }
        String timeout = firstNonBlank(stringFromMap(stepLlm, "timeoutMs"), stringFromState(state, "workflowLlmTimeoutMs"));
        if (timeout != null) {
            args.put("llmTimeoutMs", timeout);
        }
    }

    private static String stringFromMap(Map<String, Object> m, String key) {
        if (m == null || key == null) {
            return null;
        }
        Object v = m.get(key);
        return v != null && !v.toString().isBlank() ? v.toString() : null;
    }

    private static String stringFromState(ConfigurableWorkflowState state, String key) {
        if (state == null || key == null) {
            return null;
        }
        Object v = state.get(key);
        return v != null && !v.toString().isBlank() ? v.toString() : null;
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
