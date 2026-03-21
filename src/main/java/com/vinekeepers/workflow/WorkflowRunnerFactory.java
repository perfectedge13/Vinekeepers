package com.vinekeepers.workflow;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.core.cursor.CursorLaunchModel;
import com.vinekeepers.tools.ToolRunner;

import java.util.List;
import java.util.Map;

/**
 * Creates WorkflowRunner instances from workflow type and optional params.
 */
public final class WorkflowRunnerFactory {

    /**
     * Create a runner for the given workflow type and params (no workflows section or action registry).
     */
    public static WorkflowRunner create(String workflowType, Map<String, Object> workflowParams) {
        return create(workflowType, workflowParams, null, null, null, ToolPolicy.allowAll(),
                ConversationMode.SINGLE_EVENT, null, null, null);
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
                ConversationMode.SINGLE_EVENT, null, null, null);
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
        String botCursorModel = CursorLaunchModel.fromBotProfile(resolvedBot.getModelProfile());
        return create(resolvedBot.getWorkflowType(), resolvedBot.getWorkflowParams(), workflows, actionRegistry,
                toolRunner, resolvedBot.getToolPolicy(), resolvedBot.getConversationMode(),
                resolvedBot.getSessionKeyStrategy(), choiceProviderRegistry, botCursorModel);
    }

    public static WorkflowRunner create(String workflowType, Map<String, Object> workflowParams,
                                        Map<String, Object> workflows, WorkflowActionRegistry actionRegistry,
                                        ToolRunner toolRunner, ToolPolicy toolPolicy,
                                        ConversationMode conversationMode, String sessionKeyStrategy) {
        return create(workflowType, workflowParams, workflows, actionRegistry, toolRunner, toolPolicy,
                conversationMode, sessionKeyStrategy, null, null);
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
                                        String botDefaultCursorModel) {
        String type = workflowType != null && !workflowType.isBlank() ? workflowType : "stub";
        return switch (type) {
            case "configured" -> {
                WorkflowDefinition def = resolveWorkflowDefinition(workflowParams, workflows);
                yield new ConfigurableWorkflowRunner(def, actionRegistry != null ? actionRegistry : new WorkflowActionRegistry(),
                        toolRunner, toolPolicy, conversationMode, sessionKeyStrategy, choiceProviderRegistry,
                        botDefaultCursorModel);
            }
            default -> new StubWorkflowRunner();
        };
    }

    @SuppressWarnings("unchecked")
    private static WorkflowDefinition resolveWorkflowDefinition(Map<String, Object> params, Map<String, Object> workflows) {
        if (params != null && params.get("workflowRef") instanceof String ref && workflows != null) {
            Object w = workflows.get(ref);
            if (w instanceof Map<?, ?> wMap) {
                Object steps = wMap.get("steps");
                if (steps instanceof List<?> list) {
                    return new WorkflowDefinition(ref, (List<Map<String, Object>>) list);
                }
            }
        }
        if (params != null && params.get("steps") instanceof List<?> list) {
            return new WorkflowDefinition("inline", (List<Map<String, Object>>) list);
        }
        return new WorkflowDefinition("", List.of());
    }
}
