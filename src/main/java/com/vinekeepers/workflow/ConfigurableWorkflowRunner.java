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

/**
 * Interprets a workflow definition from config and persists conversational runtime state.
 */
public final class ConfigurableWorkflowRunner implements WorkflowRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableWorkflowRunner.class);

    /** When planning finished in an intake thread, avoid restarting the full workflow on every later message. */
    private static final String THREAD_PLANNING_IDLE_MESSAGE =
            "This intake/spec thread already completed the planning launch. "
                    + "For a new change, start again from the main channel with @Luna.";

    private final String sessionKeyStrategyName;
    private final ConversationMode conversationMode;
    private final List<WorkflowStep> steps;
    private final Map<String, Object> workflowLlmDefaults;

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
        this.conversationMode = conversationMode != null ? conversationMode : ConversationMode.SINGLE_EVENT;
        this.workflowLlmDefaults = resolvedDefinition.getLlm();
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
        state.put("__botId", botId);
        boolean terminal = state.getStatus() == ConfigurableWorkflowState.Status.COMPLETED
                || state.getStepIndex() >= steps.size();
        if (terminal
                && conversationMode == ConversationMode.CONVERSATIONAL
                && isThreadScopedSessionKey(stateKey, botId)) {
            stateStore.put(stateKey, state);
            return WorkflowRunResult.completed(THREAD_PLANNING_IDLE_MESSAGE);
        }
        if (terminal) {
            state.resetForNewRun();
            state.put("__sessionKey", stateKey);
            state.put("__botId", botId);
        }
        applyWorkflowLlmDefaults(state);

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

            if (!result.getSpreadWrites().isEmpty()) {
                for (Map.Entry<String, Object> e : result.getSpreadWrites().entrySet()) {
                    state.put(e.getKey(), e.getValue());
                }
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
            switch (type) {
                case "ask_input" -> out.add(new com.vinekeepers.workflow.steps.AskForInputStep(
                        (String) stepMap.get("prompt"),
                        (String) stepMap.get("storeIn")));
                case "prompt_for_field" -> {
                    String choiceProviderId = (String) stepMap.get("choiceProvider");
                    DynamicChoiceProvider provider = choiceProviderRegistry != null && choiceProviderId != null
                            ? choiceProviderRegistry.get(choiceProviderId) : null;
                    String controlMode = null;
                    Object ctrl = stepMap.get("control");
                    if (ctrl instanceof Map<?, ?> cMap && cMap.get("mode") != null) {
                        controlMode = cMap.get("mode").toString();
                    }
                    out.add(new com.vinekeepers.workflow.steps.PromptForFieldStep(
                            (String) stepMap.get("prompt"),
                            (String) stepMap.get("storeIn"),
                            (String) stepMap.get("intent"),
                            (List<Map<String, Object>>) stepMap.get("choices"),
                            (String) stepMap.get("confirmLabel"),
                            (String) stepMap.get("cancelLabel"),
                            (List<Map<String, Object>>) stepMap.get("fields"),
                            provider,
                            controlMode));
                }
                case "capture_field" -> {
                    Boolean trimAndLower = stepMap.get("trimAndLower") instanceof Boolean b ? b
                            : (stepMap.get("trimAndLower") != null ? Boolean.parseBoolean(String.valueOf(stepMap.get("trimAndLower"))) : null);
                    List<String> captureTransforms = toTransformList(stepMap.get("transforms"));
                    String captureDefault = stepMap.get("default") != null ? String.valueOf(stepMap.get("default")) : null;
                    out.add(new com.vinekeepers.workflow.steps.CaptureFieldFromEventStep(
                            (String) stepMap.get("storeIn"),
                            (String) stepMap.get("contentKey"),
                            Boolean.TRUE.equals(trimAndLower),
                            captureTransforms,
                            captureDefault));
                }
                case "extract_event" -> out.add(new com.vinekeepers.workflow.steps.ExtractEventFieldsStep(
                        (List<Map<String, Object>>) stepMap.get("fromEvent")));
                case "call_action" -> {
                    boolean storeSpread = Boolean.TRUE.equals(stepMap.get("storeSpread"))
                            || "true".equalsIgnoreCase(String.valueOf(stepMap.get("storeSpread")));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stepLlm = (Map<String, Object>) stepMap.get("llm");
                    out.add(new com.vinekeepers.workflow.steps.CallActionStep(
                            registry,
                            toolRunner,
                            toolPolicy,
                            (String) stepMap.get("action"),
                            (Map<String, Object>) stepMap.get("bind"),
                            (String) stepMap.get("storeIn"),
                            storeSpread,
                            stepLlm));
                }
                case "branch" -> out.add(new com.vinekeepers.workflow.steps.BranchStep(
                        (List<Map<String, Object>>) stepMap.get("branches")));
                case "done" -> out.add(new com.vinekeepers.workflow.steps.DoneStep(
                        (String) stepMap.get("message")));
                default -> out.add(new com.vinekeepers.workflow.steps.DoneStep("Unknown step type: " + type));
            }
        }
        return out;
    }

    private void applyWorkflowLlmDefaults(ConfigurableWorkflowState state) {
        if (state == null || workflowLlmDefaults == null || workflowLlmDefaults.isEmpty()) {
            return;
        }
        Object provider = workflowLlmDefaults.get("provider");
        if (provider != null && !provider.toString().isBlank()) {
            state.put("workflowLlmProvider", provider.toString());
        }
        Object model = workflowLlmDefaults.get("model");
        if (model != null && !model.toString().isBlank()) {
            state.put("workflowLlmModel", model.toString());
        }
        Object timeoutMs = workflowLlmDefaults.get("timeoutMs");
        if (timeoutMs != null) {
            state.put("workflowLlmTimeoutMs", timeoutMs.toString());
        }
    }

    private static List<String> toTransformList(Object transformObj) {
        if (transformObj == null) return List.of();
        if (transformObj instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) out.add(e.toString().trim());
            }
            return out;
        }
        return List.of(transformObj.toString().trim());
    }

    /**
     * Thread-only Discord sessions use {@code bot:{id}:conv:{threadId}} (no {@code :user} suffix);
     * room traffic uses {@code bot:{id}:conv:{channelId}:{userId}}.
     */
    public static boolean isThreadScopedSessionKey(String stateKey, String botId) {
        if (stateKey == null || botId == null) {
            return false;
        }
        String prefix = "bot:" + botId + ":conv:";
        if (!stateKey.startsWith(prefix)) {
            return false;
        }
        String rest = stateKey.substring(prefix.length());
        return !rest.contains(":");
    }
}
