package com.vinekeepers.workflow.v2;

import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.DynamicChoiceProviderRegistry;
import com.vinekeepers.workflow.StepOutcome;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowDefinition;
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
    private final Map<String, Object> siblingWorkflows;
    private final DynamicChoiceProviderRegistry choiceProviderRegistry;

    public GraphWorkflowRunner(
            WorkflowV2Model model,
            WorkflowActionRegistry actionRegistry,
            ToolRunner toolRunner,
            ToolPolicy toolPolicy,
            ConversationMode conversationMode,
            String sessionKeyStrategyName) {
        this(
                model,
                actionRegistry,
                toolRunner,
                toolPolicy,
                conversationMode,
                sessionKeyStrategyName,
                null,
                null);
    }

    public GraphWorkflowRunner(
            WorkflowV2Model model,
            WorkflowActionRegistry actionRegistry,
            ToolRunner toolRunner,
            ToolPolicy toolPolicy,
            ConversationMode conversationMode,
            String sessionKeyStrategyName,
            Map<String, Object> siblingWorkflows,
            DynamicChoiceProviderRegistry choiceProviderRegistry) {
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
        this.siblingWorkflows = siblingWorkflows != null && !siblingWorkflows.isEmpty() ? Map.copyOf(siblingWorkflows) : Map.of();
        this.choiceProviderRegistry = choiceProviderRegistry;
    }

    @Override
    public WorkflowRunResult runResult(Event event, StateStore stateStore, String botId) {
        String stateKey = com.vinekeepers.workflow.SessionKeyStrategies.resolve(sessionKeyStrategyName, event)
                .resolveSessionKey(botId, event);
        ConfigurableWorkflowState state =
                stateStore.get(stateKey, ConfigurableWorkflowState.class).orElseGet(ConfigurableWorkflowState::new);
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
            Object phaseObj = state.get(PHASE_KEY);
            if (phaseObj != null && !phaseObj.toString().isBlank()) {
                phaseId = phaseObj.toString().trim();
            }
            pipeIdx = parseInt(state.get(PIPELINE_INDEX_KEY), pipeIdx);

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
                if (cap == null) {
                    state.markError();
                    stateStore.put(stateKey, state);
                    return WorkflowRunResult.error("Unknown capability: " + capId);
                }
                String capKind = cap.getKind();
                if ("linear_workflow_ref".equalsIgnoreCase(capKind)) {
                    WorkflowRunResult delegated = runLinearWorkflowRef(cap.getWorkflowRef(), event, stateStore, botId);
                    state = stateStore.get(stateKey, ConfigurableWorkflowState.class).orElse(state);
                    if (delegated.isWaiting()) {
                        return delegated;
                    }
                    String err = delegated.getErrorMessage();
                    if (err != null && !err.isBlank()) {
                        state.markError();
                        stateStore.put(stateKey, state);
                        return delegated;
                    }
                    pipeIdx++;
                    state.put(PIPELINE_INDEX_KEY, String.valueOf(pipeIdx));
                    state.markActive();
                    stateStore.put(stateKey, state);
                    if (delegated.isCompleted()
                            && completedRunHasUserVisibleOutcome(delegated)
                            && pipeIdx >= pipeline.size()) {
                        WorkflowRunResult forwarded =
                                collapseThroughEmptyTerminalPhases(
                                        stateKey, state, phaseId, pipeIdx, delegated, stateStore);
                        if (forwarded != null) {
                            return forwarded;
                        }
                        state = stateStore.get(stateKey, ConfigurableWorkflowState.class).orElse(state);
                    }
                    continue;
                }
                if (!"legacy_action".equalsIgnoreCase(capKind)) {
                    state.markError();
                    stateStore.put(stateKey, state);
                    return WorkflowRunResult.error("Unknown or unsupported capability kind: " + capId);
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
            String nextPhase = resolveNextPhaseAfterPipelineExhausted(phase, state.getData());
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

        state.put("planningWorkflowStepLimitReached", "true");
        state.markError();
        stateStore.put(stateKey, state);
        return WorkflowRunResult.error("Graph workflow step limit reached.");
    }

    @SuppressWarnings("unchecked")
    private WorkflowRunResult runLinearWorkflowRef(
            String workflowRef, Event event, StateStore stateStore, String botId) {
        if (workflowRef == null || workflowRef.isBlank()) {
            return WorkflowRunResult.error("linear_workflow_ref: missing workflowRef");
        }
        Object raw = siblingWorkflows.get(workflowRef.trim());
        if (!(raw instanceof Map<?, ?> wMap)) {
            return WorkflowRunResult.error("linear_workflow_ref: unknown workflow: " + workflowRef);
        }
        Map<String, Object> wm = (Map<String, Object>) wMap;
        Object stepsObj = wm.get("steps");
        if (!(stepsObj instanceof List<?>)) {
            return WorkflowRunResult.error("linear_workflow_ref: workflow has no steps: " + workflowRef);
        }
        List<Map<String, Object>> steps = (List<Map<String, Object>>) stepsObj;
        WorkflowDefinition def =
                new WorkflowDefinition(
                        workflowRef.trim(),
                        steps,
                        WorkflowDefinition.copyLlmMap(wm.get("llm")),
                        schemaFromWorkflowRoot(wm),
                        WorkflowTemplatePolicy.fromYaml(wm.get("templates")));
        ConfigurableWorkflowRunner inner =
                new ConfigurableWorkflowRunner(
                        def,
                        actionRegistry,
                        toolRunner,
                        toolPolicy,
                        conversationMode,
                        sessionKeyStrategyName,
                        choiceProviderRegistry);
        return inner.runResult(event, stateStore, botId);
    }

    private static String schemaFromWorkflowRoot(Map<String, Object> wm) {
        if (wm == null) {
            return null;
        }
        Object s = wm.get("workflowSchema");
        return s != null ? s.toString().trim() : null;
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

    /**
     * True when a completed linear delegation carries text or a rich reply (intent/components) the engine should not drop.
     */
    static boolean completedRunHasUserVisibleOutcome(WorkflowRunResult r) {
        if (r == null || !r.isCompleted()) {
            return false;
        }
        if (r.getReplyMessage() != null && !r.getReplyMessage().isBlank()) {
            return true;
        }
        Optional<OutboundResponse> rich = r.getRichReply();
        return rich.map(or -> or.getText().isPresent() || or.getIntent().isPresent()).orElse(false);
    }

    private String resolveNextPhaseAfterPipelineExhausted(WorkflowV2PhaseModel phase, Map<String, Object> data) {
        String nextPhase = null;
        if (phase.getRulesRef() != null && !phase.getRulesRef().isBlank()) {
            List<Map<String, Object>> rules = model.getRulesets().get(phase.getRulesRef());
            Optional<String> tr = WorkflowRulesEngine.firstMatchingTransition(data, rules);
            if (tr.isPresent()) {
                nextPhase = tr.get();
            }
        }
        if (nextPhase == null && phase.getDefaultNextPhase() != null && !phase.getDefaultNextPhase().isBlank()) {
            nextPhase = phase.getDefaultNextPhase();
        }
        return nextPhase;
    }

    /**
     * When the current phase pipeline is already exhausted ({@code pipeIdx >= size}), walk default/terminal phases that
     * would end in {@code completed("")} and instead return the delegated linear result (preserves {@code done} text).
     *
     * @return {@code delegated} if the v2 graph was collapsed to {@code __v2_done}; {@code null} if more capability
     *         work remains in a subsequent phase (state updated for the outer loop).
     */
    private WorkflowRunResult collapseThroughEmptyTerminalPhases(
            String stateKey,
            ConfigurableWorkflowState state,
            String startPhaseId,
            int pipeIdxAfterLinear,
            WorkflowRunResult delegated,
            StateStore stateStore) {
        String pid = startPhaseId;
        int pdx = pipeIdxAfterLinear;
        while (true) {
            WorkflowV2PhaseModel ph = model.getPhases().get(pid);
            if (ph == null) {
                return null;
            }
            List<String> pip = ph.getPipeline();
            if (pdx < pip.size()) {
                state.put(PHASE_KEY, pid);
                state.put(PIPELINE_INDEX_KEY, String.valueOf(pdx));
                state.markActive();
                stateStore.put(stateKey, state);
                return null;
            }
            String nextPhase = resolveNextPhaseAfterPipelineExhausted(ph, state.getData());
            if (nextPhase != null && !nextPhase.isBlank()) {
                pid = nextPhase.trim();
                pdx = 0;
                state.put(PHASE_KEY, pid);
                state.put(PIPELINE_INDEX_KEY, "0");
                state.markActive();
                stateStore.put(stateKey, state);
                continue;
            }
            if (ph.isTerminal() || pip.isEmpty()) {
                state.put(PHASE_KEY, "__v2_done");
                state.markCompleted();
                stateStore.put(stateKey, state);
                return delegated;
            }
            state.put(PHASE_KEY, "__v2_done");
            state.markCompleted();
            stateStore.put(stateKey, state);
            return delegated;
        }
    }
}
