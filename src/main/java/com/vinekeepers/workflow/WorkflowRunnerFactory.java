package com.vinekeepers.workflow;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.env.Env;
import com.vinekeepers.tools.ToolRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates WorkflowRunner instances from workflow type and optional params.
 */
public final class WorkflowRunnerFactory {
    private static final Set<String> LLM_STEP_ACTIONS = Set.of("launch_cursor_run", "cursor.fullRun");
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "gpt-5",
            "gpt-5.2",
            "gpt-5-mini",
            "gpt-5-nano",
            "gpt-4.1",
            "gpt-4.1-mini",
            "gpt-4o",
            "gpt-4o-mini",
            "o3",
            "o4-mini",
            "claude-3-5-sonnet",
            "claude-3-7-sonnet",
            "claude-sonnet-4"
    );

    /**
     * Create a runner for the given workflow type and params (no workflows section or action registry).
     */
    public static WorkflowRunner create(String workflowType, Map<String, Object> workflowParams) {
        return create(workflowType, workflowParams, null, null, null, ToolPolicy.allowAll(),
                ConversationMode.SINGLE_EVENT, null);
    }

    /**
     * Create a runner for the given workflow type and params, with optional workflows map and action registry for type "configured".
     *
     * @param workflowType   type from config (e.g. "stub", "configured")
     * @param workflowParams optional params from config (may be null); for "configured" may contain workflowRef or steps
     * @param workflows      optional workflows section from YAML (id -> { steps: [...] })
     * @param actionRegistry optional registry for CallActionStep (used when type is "configured")
     * @return runner for that type
     */
    public static WorkflowRunner create(String workflowType, Map<String, Object> workflowParams,
                                       Map<String, Object> workflows, WorkflowActionRegistry actionRegistry) {
        return create(workflowType, workflowParams, workflows, actionRegistry, null, ToolPolicy.allowAll(),
                ConversationMode.SINGLE_EVENT, null, null);
    }

    public static WorkflowRunner create(BotDefinition bot, Map<String, Object> workflows,
                                        WorkflowActionRegistry actionRegistry, ToolRunner toolRunner) {
        return create(bot, workflows, actionRegistry, toolRunner, null);
    }

    public static WorkflowRunner create(BotDefinition bot, Map<String, Object> workflows,
                                        WorkflowActionRegistry actionRegistry, ToolRunner toolRunner,
                                        DynamicChoiceProviderRegistry choiceProviderRegistry) {
        BotDefinition resolvedBot = bot;
        if (resolvedBot == null) {
            return create("stub", null, workflows, actionRegistry, toolRunner, ToolPolicy.allowAll(),
                    ConversationMode.SINGLE_EVENT, null, null, null);
        }
        String botDefaultModel = normalizeModelId(resolvedBot.getModelProfile().getModelId());
        return create(resolvedBot.getWorkflowType(), resolvedBot.getWorkflowParams(), workflows, actionRegistry,
                toolRunner, resolvedBot.getToolPolicy(), resolvedBot.getConversationMode(),
                resolvedBot.getSessionKeyStrategy(), choiceProviderRegistry, botDefaultModel);
    }

    public static WorkflowRunner create(String workflowType, Map<String, Object> workflowParams,
                                        Map<String, Object> workflows, WorkflowActionRegistry actionRegistry,
                                        ToolRunner toolRunner, ToolPolicy toolPolicy,
                                        ConversationMode conversationMode, String sessionKeyStrategy) {
        return create(workflowType, workflowParams, workflows, actionRegistry, toolRunner, toolPolicy,
                conversationMode, sessionKeyStrategy, null);
    }

    public static WorkflowRunner create(String workflowType, Map<String, Object> workflowParams,
                                        Map<String, Object> workflows, WorkflowActionRegistry actionRegistry,
                                        ToolRunner toolRunner, ToolPolicy toolPolicy,
                                        ConversationMode conversationMode, String sessionKeyStrategy,
                                        DynamicChoiceProviderRegistry choiceProviderRegistry) {
        return create(workflowType, workflowParams, workflows, actionRegistry, toolRunner, toolPolicy,
                conversationMode, sessionKeyStrategy, choiceProviderRegistry, null);
    }

    public static WorkflowRunner create(String workflowType, Map<String, Object> workflowParams,
                                        Map<String, Object> workflows, WorkflowActionRegistry actionRegistry,
                                        ToolRunner toolRunner, ToolPolicy toolPolicy,
                                        ConversationMode conversationMode, String sessionKeyStrategy,
                                        DynamicChoiceProviderRegistry choiceProviderRegistry,
                                        String botDefaultModel) {
        String type = workflowType != null && !workflowType.isBlank() ? workflowType : "stub";
        return switch (type) {
            case "configured" -> {
                WorkflowDefinition def = resolveWorkflowDefinition(workflowParams, workflows, botDefaultModel);
                yield new ConfigurableWorkflowRunner(def, actionRegistry != null ? actionRegistry : new WorkflowActionRegistry(),
                        toolRunner, toolPolicy, conversationMode, sessionKeyStrategy, choiceProviderRegistry);
            }
            default -> new StubWorkflowRunner();
        };
    }

    @SuppressWarnings("unchecked")
    private static WorkflowDefinition resolveWorkflowDefinition(Map<String, Object> params, Map<String, Object> workflows,
                                                                String botDefaultModel) {
        if (params != null && params.get("workflowRef") instanceof String ref && workflows != null) {
            Object w = workflows.get(ref);
            if (w instanceof Map<?, ?> wMap) {
                Object steps = wMap.get("steps");
                if (steps instanceof List<?> list) {
                    String resolvedDefaultModel = selectWorkflowDefaultModel(wMap, botDefaultModel);
                    validateStepModels(ref, resolvedDefaultModel, (List<Map<String, Object>>) list);
                    return new WorkflowDefinition(ref, resolvedDefaultModel, (List<Map<String, Object>>) list);
                }
            }
        }
        if (params != null && params.get("steps") instanceof List<?> list) {
            String resolvedDefaultModel = normalizeModelId(botDefaultModel);
            validateStepModels("inline", resolvedDefaultModel, (List<Map<String, Object>>) list);
            return new WorkflowDefinition("inline", resolvedDefaultModel, (List<Map<String, Object>>) list);
        }
        return new WorkflowDefinition("", normalizeModelId(botDefaultModel), List.of());
    }

    private static String selectWorkflowDefaultModel(Map<?, ?> workflowMap, String botDefaultModel) {
        String workflowDefaultModel = normalizeModelId(asString(workflowMap.get("defaultModel")));
        if (workflowDefaultModel == null) {
            workflowDefaultModel = normalizeModelId(asString(workflowMap.get("default_model")));
        }
        if (workflowDefaultModel != null) {
            validateModelIdentifier(workflowDefaultModel, "workflow default model");
            return workflowDefaultModel;
        }
        return normalizeModelId(botDefaultModel);
    }

    private static void validateStepModels(String workflowId, String defaultModel, List<Map<String, Object>> steps) {
        String envDefaultModel = normalizeModelId(Env.get("CURSOR_MODEL", ""));
        if (envDefaultModel != null) {
            validateModelIdentifier(envDefaultModel, "CURSOR_MODEL");
        }
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String action = asString(step.get("action"));
            String stepModel = normalizeModelId(asString(step.get("model")));
            if (stepModel == null) {
                stepModel = normalizeModelId(asString(step.get("modelOverride")));
            }
            boolean isLlmStep = "call_action".equals(asString(step.get("type"))) && LLM_STEP_ACTIONS.contains(action);
            if (!isLlmStep && stepModel != null) {
                throw new IllegalArgumentException("Workflow '" + workflowId + "' step " + i
                        + " config error: model override is only allowed on LLM call_action steps.");
            }
            if (stepModel != null) {
                validateModelIdentifier(stepModel, "workflow step model");
            }
            if (isLlmStep && stepModel == null && defaultModel == null && envDefaultModel == null) {
                throw new IllegalArgumentException("Workflow '" + workflowId + "' step " + i
                        + " config error: no model configured for LLM step (requires step model, bot/workflow default, or CURSOR_MODEL).");
            }
        }
    }

    private static void validateModelIdentifier(String model, String source) {
        if (!SUPPORTED_MODELS.contains(model)) {
            throw new IllegalArgumentException("Unsupported " + source + " '" + model + "'.");
        }
    }

    private static String normalizeModelId(String model) {
        if (model == null) {
            return null;
        }
        String normalized = model.trim();
        if (normalized.isEmpty() || "stub".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
