package com.vinekeepers.workflow.v2;

import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepOutcome;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunResult;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.steps.CallActionStep;
import com.vinekeepers.workflow.template.WorkflowTemplatePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Config-driven phase graph runner ({@code workflowSchema: v2}): runs capability pipelines and rulesets.
 */
public final class GraphWorkflowRunner implements WorkflowRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphWorkflowRunner.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String PHASE_KEY = "__v2_phaseId";
    public static final String PIPELINE_INDEX_KEY = "__v2_pipelineIndex";

    private static final String THREAD_PLANNING_IDLE_MESSAGE =
            "This intake/spec thread already completed the planning launch. "
                    + "For a new change, start again from the main channel with @Luna.";

    private final WorkflowV2Model model;
    private final String sessionKeyStrategyName;
    private final ConversationMode conversationMode;
    private final WorkflowActionRegistry actionRegistry;
    private final ToolRunner toolRunner;
    private final ToolPolicy toolPolicy;
    private final WorkflowTemplatePolicy templatePolicy;

    public GraphWorkflowRunner(
            WorkflowV2Model model,
            WorkflowActionRegistry actionRegistry,
            ToolRunner toolRunner,
            ToolPolicy toolPolicy,
            ConversationMode conversationMode,
            String sessionKeyStrategyName) {
        WorkflowV2Model resolved =
                model != null
                        ? model
                        : new WorkflowV2Model(
                                "",
                                "",
                                Map.of(),
                                Map.of(),
                                Map.of(),
                                Map.of(),
                                Map.of(),
                                WorkflowTemplatePolicy.LEGACY_FULL_STATE);
        this.model = resolved;
        this.actionRegistry = actionRegistry != null ? actionRegistry : new WorkflowActionRegistry();
        this.toolRunner = toolRunner;
        this.toolPolicy = toolPolicy != null ? toolPolicy : ToolPolicy.allowAll();
        this.conversationMode = conversationMode != null ? conversationMode : ConversationMode.SINGLE_EVENT;
        this.sessionKeyStrategyName = sessionKeyStrategyName;
        this.templatePolicy = resolved.getTemplatePolicy();
    }

    @Override
    public WorkflowRunResult runResult(Event event, StateStore stateStore, String botId) {
        String stateKey = com.vinekeepers.workflow.SessionKeyStrategies.resolve(sessionKeyStrategyName, event)
                .resolveSessionKey(botId, event);
        ConfigurableWorkflowState state = stateStore.get(stateKey, ConfigurableWorkflowState.class)
                .orElseGet(ConfigurableWorkflowState::new);
        state.put("__sessionKey", stateKey);
        state.put("__botId", botId);

        boolean terminal = state.getStatus() == ConfigurableWorkflowState.Status.COMPLETED
                || "__v2_done".equals(state.get(PHASE_KEY));
        if (terminal
                && conversationMode == ConversationMode.CONVERSATIONAL
                && com.vinekeepers.workflow.ConfigurableWorkflowRunner.isThreadScopedSessionKey(stateKey, botId)) {
            stateStore.put(stateKey, state);
            return WorkflowRunResult.completed(THREAD_PLANNING_IDLE_MESSAGE);
        }
        if (terminal) {
            state.resetForNewRun();
            state.put("__sessionKey", stateKey);
            state.put("__botId", botId);
        }

        applyWorkflowLlmDefaults(state);
        templatePolicy.writeIntoState(state);
        Map<String, Object> deliberation = model.getDeliberation();
        if (deliberation != null && !deliberation.isEmpty()) {
            try {
                state.put("workflowDeliberationMetaJson", JSON.writeValueAsString(deliberation));
            } catch (Exception e) {
                state.put("workflowDeliberationMetaJson", "{}");
            }
            Object profileHint = deliberation.get("profileHint");
            if (profileHint != null) {
                state.put("deliberationProfileHint", profileHint.toString());
            }
            Object label = deliberation.get("label");
            if (label != null) {
                state.put("deliberationLabel", label.toString());
            }
        }

        String phaseId = state.get(PHASE_KEY) != null ? state.get(PHASE_KEY).toString().trim() : "";
        if (phaseId.isBlank()) {
            phaseId = model.getEntryPhase();
            state.put(PHASE_KEY, phaseId);
        }
        int pipeIdx = parseInt(state.get(PIPELINE_INDEX_KEY), 0);

        int maxMicroSteps = 100;
        for (int guard = 0; guard < maxMicroSteps; guard++) {
            WorkflowV2PhaseModel phase = model.getPhases().get(phaseId);
            if (phase == null) {
                state.markError();
                stateStore.put(stateKey, state);
                return WorkflowRunResult.error("Unknown workflow phase: " + phaseId);
            }

            List<String> pipeline = phase.getPipeline();
            if (pipeIdx < pipeline.size()) {
                String capId = pipeline.get(pipeIdx);
                WorkflowV2CapabilityModel cap = model.getCapabilities().get(capId);
                if (cap == null || !"legacy_action".equalsIgnoreCase(cap.getKind())) {
                    state.markError();
                    stateStore.put(stateKey, state);
                    return WorkflowRunResult.error("Unknown or unsupported capability: " + capId);
                }
                CallActionStep step =
                        new CallActionStep(
                                actionRegistry,
                                toolRunner,
                                toolPolicy,
                                cap.getAction(),
                                cap.getBind(),
                                null,
                                cap.isStoreSpread(),
                                null);
                StepResult result;
                try {
                    result = step.execute(event, state, guard);
                } catch (SecurityException e) {
                    log.warn("Graph workflow tool denied for {}: {}", botId, e.getMessage());
                    state.markError();
                    stateStore.put(stateKey, state);
                    return WorkflowRunResult.error(e.getMessage());
                } catch (RuntimeException e) {
                    log.warn("Graph workflow step failed for {}: {}", botId, e.getMessage());
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
                if (!result.getClearKeys().isEmpty()) {
                    state.clearKeys(result.getClearKeys());
                }
                if (result.getOutcome() == StepOutcome.WAITING) {
                    state.markWaiting(result.getWaitingForField(), result.getPromptMessage());
                    stateStore.put(stateKey, state);
                    return result.getRichReply().isPresent()
                            ? WorkflowRunResult.waiting(result.getRichReply().get(), result.getWaitingForField())
                            : WorkflowRunResult.waiting(result.getPromptMessage(), result.getWaitingForField());
                }
                if (result.getOutcome() == StepOutcome.ERROR) {
                    state.markError();
                    stateStore.put(stateKey, state);
                    return WorkflowRunResult.error(result.getMessage());
                }

                pipeIdx++;
                state.put(PIPELINE_INDEX_KEY, String.valueOf(pipeIdx));
                state.markActive();
                stateStore.put(stateKey, state);
                continue;
            }

            // Pipeline finished for this phase
            String nextPhase = null;
            if (phase.getRulesRef() != null && !phase.getRulesRef().isBlank()) {
                List<Map<String, Object>> rules = model.getRulesets().get(phase.getRulesRef());
                Optional<String> tr = WorkflowRulesEngine.firstMatchingTransition(state.getData(), rules);
                if (tr.isPresent()) {
                    nextPhase = tr.get();
                }
            }
            if (nextPhase == null && phase.getDefaultNextPhase() != null && !phase.getDefaultNextPhase().isBlank()) {
                nextPhase = phase.getDefaultNextPhase();
            }
            if (nextPhase != null) {
                phaseId = nextPhase;
                pipeIdx = 0;
                state.put(PHASE_KEY, phaseId);
                state.put(PIPELINE_INDEX_KEY, "0");
                stateStore.put(stateKey, state);
                continue;
            }

            if (phase.isTerminal() || pipeline.isEmpty()) {
                state.put(PHASE_KEY, "__v2_done");
                state.markCompleted();
                stateStore.put(stateKey, state);
                return WorkflowRunResult.completed("");
            }

            state.put(PHASE_KEY, "__v2_done");
            state.markCompleted();
            stateStore.put(stateKey, state);
            return WorkflowRunResult.completed("");
        }

        state.markError();
        stateStore.put(stateKey, state);
        return WorkflowRunResult.error("Graph workflow step limit reached.");
    }

    private static void applyDeliberationHints(ConfigurableWorkflowState state, WorkflowV2Model model) {
        if (state == null || model == null) {
            return;
        }
        Map<String, Object> del = model.getDeliberation();
        if (del.isEmpty()) {
            return;
        }
        Object hint = del.get("profileHint");
        if (hint != null && !hint.toString().isBlank()) {
            state.put("deliberationProfileHint", hint.toString().trim());
        }
        Object label = del.get("label");
        if (label != null && !label.toString().isBlank()) {
            state.put("deliberationLabel", label.toString().trim());
        }
    }

    private void applyWorkflowLlmDefaults(ConfigurableWorkflowState state) {
        Map<String, Object> workflowLlmDefaults = model.getLlm();
        if (state == null || workflowLlmDefaults == null || workflowLlmDefaults.isEmpty()) {
            return;
        }
        Object provider = workflowLlmDefaults.get("provider");
        if (provider != null && !provider.toString().isBlank()) {
            state.put("workflowLlmProvider", provider.toString());
        }
        Object modelName = workflowLlmDefaults.get("model");
        if (modelName != null && !modelName.toString().isBlank()) {
            state.put("workflowLlmModel", modelName.toString());
        }
        Object timeoutMs = workflowLlmDefaults.get("timeoutMs");
        if (timeoutMs != null) {
            state.put("workflowLlmTimeoutMs", timeoutMs.toString());
        }
    }

    private static int parseInt(Object o, int dflt) {
        if (o == null) {
            return dflt;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
