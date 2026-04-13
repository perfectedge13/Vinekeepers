package com.vinekeepers.workflow;

import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.tools.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interprets a workflow definition from config and persists conversational runtime state.
 */
public final class ConfigurableWorkflowRunner implements WorkflowRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableWorkflowRunner.class);
    private static final Set<String> SUPPORTED_STEP_MODEL_PROVIDERS = Set.of("openai");

    private final String sessionKeyStrategyName;
    private final List<WorkflowStep> steps;

    public ConfigurableWorkflowRunner(WorkflowDefinition definition, WorkflowActionRegistry actionRegistry) {
        this(definition, actionRegistry, null, ToolPolicy.allowAll(), ConversationMode.SINGLE_EVENT, null, null);
    }

    public ConfigurableWorkflowRunner(WorkflowDefinition definition, WorkflowActionRegistry actionRegistry,
                                      ToolRunner toolRunner, ToolPolicy toolPolicy,
                                      ConversationMode conversationMode, String sessionKeyStrategyName) {
        this(definition, actionRegistry, toolRunner, toolPolicy, conversationMode, sessionKeyStrategyName, null);
    }

    public ConfigurableWorkflowRunner(WorkflowDefinition definition, WorkflowActionRegistry actionRegistry,
                                      ToolRunner toolRunner, ToolPolicy toolPolicy,
                                      ConversationMode conversationMode, String sessionKeyStrategyName,
                                      DynamicChoiceProviderRegistry choiceProviderRegistry) {
        WorkflowDefinition resolvedDefinition = definition != null ? definition : new WorkflowDefinition("", List.of());
        WorkflowActionRegistry resolvedRegistry = actionRegistry != null ? actionRegistry : new WorkflowActionRegistry();
        this.sessionKeyStrategyName = sessionKeyStrategyName;
        this.steps = buildSteps(resolvedDefinition, resolvedRegistry, toolRunner,
                toolPolicy != null ? toolPolicy : ToolPolicy.allowAll(), choiceProviderRegistry);
    }

    @Override
    public WorkflowRunResult runResult(Event event, StateStore stateStore, String botId) {
        if (steps.isEmpty()) {
            return WorkflowRunResult.completed("");
        }

        String stateKey = SessionKeyStrategies.resolve(sessionKeyStrategyName, event).resolveSessionKey(botId, event);
        ConfigurableWorkflowState state = stateStore.get(stateKey, ConfigurableWorkflowState.class)
                .orElseGet(ConfigurableWorkflowState::new);
        state.put("__sessionKey", stateKey);
        if (state.getStatus() == ConfigurableWorkflowState.Status.COMPLETED || state.getStepIndex() >= steps.size()) {
            state.resetForNewRun();
            state.put("__sessionKey", stateKey);
        }

        int maxSteps = 100;
        for (int i = 0; i < maxSteps; i++) {
            if (state.getStepIndex() >= steps.size()) {
                state.markCompleted();
                stateStore.put(stateKey, state);
                return WorkflowRunResult.completed("");
            }

            WorkflowStep step = steps.get(state.getStepIndex());
            StepResult result;
            try {
                result = step.execute(event, state, state.getStepIndex());
            } catch (SecurityException e) {
                log.warn("Workflow tool denied for {}: {}", botId, e.getMessage());
                state.markError();
                stateStore.put(stateKey, state);
                return WorkflowRunResult.error(e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Workflow step failed for {}: {}", botId, e.getMessage());
                state.markError();
                stateStore.put(stateKey, state);
                return WorkflowRunResult.error(e.getMessage());
            }

            if (result.getStoreIn() != null && result.getStoreValue() != null) {
                state.put(result.getStoreIn(), result.getStoreValue());
            }

            int nextStepIndex = result.getNextStepIndex() != null
                    ? result.getNextStepIndex()
                    : state.getStepIndex() + 1;

            if (!result.getClearKeys().isEmpty()) {
                state.clearKeys(result.getClearKeys());
            }

            if (result.getOutcome() == StepOutcome.WAITING) {
                state.markWaiting(result.getWaitingForField(), result.getPromptMessage());
                state.setStepIndex(nextStepIndex);
                stateStore.put(stateKey, state);
                return result.getRichReply().isPresent()
                        ? WorkflowRunResult.waiting(result.getRichReply().get(), result.getWaitingForField())
                        : WorkflowRunResult.waiting(result.getPromptMessage(), result.getWaitingForField());
            }

            if (result.getOutcome() == StepOutcome.COMPLETE) {
                state.markCompleted();
                state.setStepIndex(nextStepIndex);
                stateStore.put(stateKey, state);
                return result.getRichReply().isPresent()
                        ? WorkflowRunResult.completed(result.getRichReply().get())
                        : WorkflowRunResult.completed(result.getMessage());
            }

            if (result.getOutcome() == StepOutcome.ERROR) {
                state.markError();
                stateStore.put(stateKey, state);
                return WorkflowRunResult.error(result.getMessage());
            }

            state.markActive();
            state.setStepIndex(nextStepIndex);
        }

        state.markError();
        stateStore.put(stateKey, state);
        return WorkflowRunResult.error("Workflow step limit reached.");
    }

    @SuppressWarnings("unchecked")
    private static List<WorkflowStep> buildSteps(WorkflowDefinition definition, WorkflowActionRegistry registry,
                                                 ToolRunner toolRunner, ToolPolicy toolPolicy,
                                                 DynamicChoiceProviderRegistry choiceProviderRegistry) {
        List<WorkflowStep> out = new ArrayList<>();
        for (Map<String, Object> stepMap : definition.getSteps()) {
            String type = (String) stepMap.get("type");
            if (type == null) {
                type = "done";
            }
            WorkflowStepModel stepModel = parseAndValidateStepModel(stepMap, type);
            switch (type) {
                case "ask_input" -> out.add(new com.vinekeepers.workflow.steps.AskForInputStep(
                        (String) stepMap.get("prompt"),
                        (String) stepMap.get("storeIn")));
                case "prompt_for_field" -> {
                    String choiceProviderId = (String) stepMap.get("choiceProvider");
                    DynamicChoiceProvider provider = choiceProviderRegistry != null && choiceProviderId != null
                            ? choiceProviderRegistry.get(choiceProviderId) : null;
                    out.add(new com.vinekeepers.workflow.steps.PromptForFieldStep(
                            (String) stepMap.get("prompt"),
                            (String) stepMap.get("storeIn"),
                            (String) stepMap.get("intent"),
                            (List<Map<String, Object>>) stepMap.get("choices"),
                            (String) stepMap.get("confirmLabel"),
                            (String) stepMap.get("cancelLabel"),
                            (List<Map<String, Object>>) stepMap.get("fields"),
                            provider));
                }
                case "capture_field" -> {
                    Boolean trimAndLower = stepMap.get("trimAndLower") instanceof Boolean b ? b
                            : (stepMap.get("trimAndLower") != null ? Boolean.parseBoolean(String.valueOf(stepMap.get("trimAndLower"))) : null);
                    out.add(new com.vinekeepers.workflow.steps.CaptureFieldFromEventStep(
                            (String) stepMap.get("storeIn"),
                            (String) stepMap.get("contentKey"),
                            Boolean.TRUE.equals(trimAndLower)));
                }
                case "call_action" -> out.add(new com.vinekeepers.workflow.steps.CallActionStep(
                        registry,
                        toolRunner,
                        toolPolicy,
                        (String) stepMap.get("action"),
                        (Map<String, Object>) stepMap.get("bind"),
                        (String) stepMap.get("storeIn"),
                        stepModel));
                case "branch" -> out.add(new com.vinekeepers.workflow.steps.BranchStep(
                        (List<Map<String, Object>>) stepMap.get("branches")));
                case "done" -> out.add(new com.vinekeepers.workflow.steps.DoneStep(
                        (String) stepMap.get("message")));
                default -> out.add(new com.vinekeepers.workflow.steps.DoneStep("Unknown step type: " + type));
            }
        }
        return out;
    }

    private static WorkflowStepModel parseAndValidateStepModel(Map<String, Object> stepMap, String stepType) {
        WorkflowStepModel stepModel = WorkflowStepModel.fromConfig(stepMap.get("model"));
        if (stepModel == null) {
            return null;
        }
        String provider = stepModel.getProvider().toLowerCase();
        if (!SUPPORTED_STEP_MODEL_PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException(
                    "Unsupported workflow step model provider '" + stepModel.getProvider()
                            + "' for step type '" + stepType + "'. Supported providers: " + SUPPORTED_STEP_MODEL_PROVIDERS);
        }
        if (stepModel.getModelId().isBlank()) {
            throw new IllegalArgumentException(
                    "Workflow step model.modelId is required when model is set for step type '" + stepType + "'.");
        }
        return new WorkflowStepModel(provider, stepModel.getModelId());
    }
}
