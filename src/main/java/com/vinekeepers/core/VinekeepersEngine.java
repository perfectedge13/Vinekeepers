package com.vinekeepers.core;

import com.vinekeepers.audit.AuditLog;
import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.NormalizedEventContext;
import com.vinekeepers.bot.Router;
import com.vinekeepers.connectors.ReplyTargetResolver;
import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.events.Event;
import com.vinekeepers.events.EventSubscriber;
import com.vinekeepers.interactions.AppReplySink;
import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.interactions.ReplyTarget;
import com.vinekeepers.reasoner.ProposedToolCall;
import com.vinekeepers.reasoner.Reasoner;
import com.vinekeepers.reasoner.ReasonerInput;
import com.vinekeepers.reasoner.ReasonerOutput;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.tools.ToolRegistry;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.SessionKeyStrategies;
import com.vinekeepers.workflow.WorkflowRunResult;
import com.vinekeepers.workflow.WorkflowRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven engine: onEvent routes to bots, runs workflow + reasoner, records audit.
 */
public final class VinekeepersEngine implements EventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(VinekeepersEngine.class);

    private final Router router;
    private final Map<String, BotDefinition> bots = new ConcurrentHashMap<>();
    private final Map<String, WorkflowRunner> runners = new ConcurrentHashMap<>();
    private final Map<String, Reasoner> reasoners = new ConcurrentHashMap<>();
    private final StateStore stateStore;
    private final AuditRecorder auditRecorder;
    private final ToolRunner toolRunner;
    /** Transitional: sink per sourceId prefix (e.g. "discord"). */
    private final Map<String, AppReplySink> sinks = new ConcurrentHashMap<>();
    /** Reply senders keyed by connector id (sourceId prefix). */
    private final Map<String, ReplySender> replySendersByConnectorId = new ConcurrentHashMap<>();
    /** Reply target resolvers keyed by connector id (sourceId prefix). */
    private final Map<String, ReplyTargetResolver> resolversByConnectorId = new ConcurrentHashMap<>();

    public VinekeepersEngine(Router router, StateStore stateStore, AuditRecorder auditRecorder) {
        this(router, stateStore, auditRecorder, new ToolRunner(new ToolRegistry()));
    }

    public VinekeepersEngine(Router router, StateStore stateStore, AuditRecorder auditRecorder, ToolRunner toolRunner) {
        this.router = Objects.requireNonNull(router, "router");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.auditRecorder = auditRecorder != null ? auditRecorder : e -> {};
        this.toolRunner = Objects.requireNonNull(toolRunner, "toolRunner");
    }

    public void registerBot(BotDefinition bot) {
        bots.put(bot.getId(), bot);
    }

    public void registerRunner(String botId, WorkflowRunner runner) {
        runners.put(botId, runner);
    }

    public void registerReasoner(String botId, Reasoner reasoner) {
        reasoners.put(botId, reasoner);
    }

    /**
     * Register a reply sink for the given sourceId prefix (e.g. "discord").
     * Transitional; longer-term may use explicit connector/surface reference.
     */
    public void registerSink(String sourceIdPrefix, AppReplySink sink) {
        if (sourceIdPrefix != null && !sourceIdPrefix.isBlank() && sink != null) {
            sinks.put(sourceIdPrefix, sink);
        }
    }

    /**
     * Register a reply sender for the given connector id (used when no sink is registered for that source).
     */
    public void setReplySender(String connectorId, ReplySender sender) {
        if (connectorId != null && !connectorId.isBlank() && sender != null) {
            replySendersByConnectorId.put(connectorId, sender);
        }
    }

    /**
     * Set the reply sender for legacy text-only delivery when no sink is registered.
     * Backward compatibility: registers the sender under connector id "discord".
     */
    public void setReplySender(ReplySender replySender) {
        setReplySender("discord", replySender);
    }

    /**
     * Register a reply target resolver for the given connector id.
     * The engine uses the resolver as the single source of truth for ReplyTarget; when no resolver is registered or the resolver returns empty, the engine does not deliver the reply (fail closed). Delivery uses target.channelId()/messageId() when sending via reply sender.
     */
    public void registerReplyTargetResolver(String connectorId, ReplyTargetResolver resolver) {
        if (connectorId != null && !connectorId.isBlank() && resolver != null) {
            resolversByConnectorId.put(connectorId, resolver);
        }
    }

    /**
     * Runs the coordinator bot workflow once for a synthetic Discord message in the intake/spec thread
     * (e.g. immediately after Luna handoff). Does not use the router — only {@code coordinatorBotId} runs.
     * Idempotent: no-ops when the thread session already has planning in progress, is waiting for input, or
     * completed (conversational thread terminal).
     *
     * @return {@code RAN}, {@code SKIPPED_WAITING}, {@code SKIPPED_IN_PROGRESS}, {@code SKIPPED_COMPLETED},
     *         {@code NO_BOT}, or {@code NO_RUNNER}
     */
    public String dispatchCoordinatorPlanningKickoff(Event syntheticThreadMessage, String coordinatorBotId) {
        if (coordinatorBotId == null || coordinatorBotId.isBlank()) {
            return "NO_BOT";
        }
        BotDefinition bot = bots.get(coordinatorBotId);
        if (bot == null) {
            log.warn("No bot registered for coordinator id: {}", coordinatorBotId);
            return "NO_BOT";
        }
        if (runners.get(coordinatorBotId) == null) {
            log.warn("No workflow runner for coordinator id: {}", coordinatorBotId);
            return "NO_RUNNER";
        }
        String stateKey = SessionKeyStrategies.resolve(bot.getSessionKeyStrategy(), syntheticThreadMessage)
                .resolveSessionKey(coordinatorBotId, syntheticThreadMessage);
        Optional<ConfigurableWorkflowState> existing = stateStore.get(stateKey, ConfigurableWorkflowState.class);
        if (existing.isPresent()) {
            ConfigurableWorkflowState s = existing.get();
            if (s.getStatus() == ConfigurableWorkflowState.Status.WAITING_INPUT) {
                return "SKIPPED_WAITING";
            }
            if (s.getStatus() == ConfigurableWorkflowState.Status.COMPLETED
                    && ConfigurableWorkflowRunner.isThreadScopedSessionKey(stateKey, coordinatorBotId)) {
                return "SKIPPED_COMPLETED";
            }
            if ((s.getStatus() == ConfigurableWorkflowState.Status.ACTIVE
                    || s.getStatus() == ConfigurableWorkflowState.Status.ERROR)
                    && s.getStepIndex() > 0) {
                return "SKIPPED_IN_PROGRESS";
            }
        }
        auditRecorder.record(AuditLog.fromEvent(syntheticThreadMessage, coordinatorBotId, "received", ""));
        runWorkflowReasonerAndDeliver(syntheticThreadMessage, bot, coordinatorBotId);
        return "RAN";
    }

    @Override
    public void onEvent(Event event) {
        log.debug("Engine received event: {} {}", event.getSourceId(), event.getKind());
        List<String> botIds = router.route(event);
        if (isDiscordMessage(event)) {
            addBotsWithWaitingSessionForDiscordMessage(event, botIds);
        }
        for (String botId : botIds) {
            handleEventForBot(event, botId);
        }
    }

    private static boolean isDiscordMessage(Event event) {
        return "message".equals(event.getKind()) && event.getSourceId() != null && event.getSourceId().startsWith("discord");
    }

    /**
     * For Discord message events, adds to {@code botIds} any registered bot that has a workflow runner
     * and has state in stateStore under this event's session key with status WAITING_INPUT.
     */
    private void addBotsWithWaitingSessionForDiscordMessage(Event event, List<String> botIds) {
        Set<String> seen = new HashSet<>(botIds);
        for (BotDefinition bot : bots.values()) {
            if (seen.contains(bot.getId())) {
                continue;
            }
            if (runners.get(bot.getId()) == null) {
                continue;
            }
            String sessionKey = SessionKeyStrategies.resolve(bot.getSessionKeyStrategy(), event)
                    .resolveSessionKey(bot.getId(), event);
            Optional<ConfigurableWorkflowState> stateOpt = stateStore.get(sessionKey, ConfigurableWorkflowState.class);
            if (stateOpt.isPresent() && stateOpt.get().getStatus() == ConfigurableWorkflowState.Status.WAITING_INPUT) {
                botIds.add(bot.getId());
                seen.add(bot.getId());
            }
        }
    }

    private void handleEventForBot(Event event, String botId) {
        BotDefinition bot = bots.get(botId);
        if (bot == null) {
            log.warn("No bot registered for id: {}", botId);
            return;
        }

        auditRecorder.record(AuditLog.fromEvent(event, botId, "received", ""));
        runWorkflowReasonerAndDeliver(event, bot, botId);
    }

    private void runWorkflowReasonerAndDeliver(Event event, BotDefinition bot, String botId) {
        WorkflowRunResult workflowResult = WorkflowRunResult.continueWithoutReply();
        WorkflowRunner runner = runners.get(botId);
        if (runner != null) {
            workflowResult = runner.runResult(event, stateStore, botId);
            String detail = workflowResult.getReplyMessage();
            if (detail == null || detail.isBlank()) {
                detail = workflowResult.getErrorMessage();
            }
            auditRecorder.record(AuditLog.fromEvent(event, botId, "workflow", detail != null ? detail : ""));
        }

        ReasonerOutput reasonerOutput = runReasoner(bot, event, workflowResult);
        OutboundResponse outbound = buildOutboundResponse(workflowResult, reasonerOutput);
        String sourceId = event.getSourceId();
        if (sourceId == null) {
            log.warn("No reply target resolver for null sourceId; skipping reply delivery");
            return;
        }
        String sourcePrefix = sourceId.contains(":") ? sourceId.substring(0, sourceId.indexOf(':')) : sourceId;
        ReplyTargetResolver resolver = resolversByConnectorId.get(sourcePrefix);
        if (resolver == null) {
            log.warn("No reply target resolver for source prefix {}; skipping reply delivery", sourcePrefix);
            return;
        }
        Optional<ReplyTarget> targetOpt = resolver.resolve(event);
        if (targetOpt.isEmpty()) {
            log.warn("Reply target resolver returned empty; skipping reply delivery");
            return;
        }
        deliverReply(event, outbound, targetOpt.get());
    }

    private ReasonerOutput runReasoner(BotDefinition bot, Event event, WorkflowRunResult workflowResult) {
        Reasoner reasoner = reasoners.get(bot.getId());
        if (reasoner == null) {
            return ReasonerOutput.empty();
        }

        String stateKey = SessionKeyStrategies.resolve(bot.getSessionKeyStrategy(), event)
                .resolveSessionKey(bot.getId(), event);
        ConfigurableWorkflowState state = stateStore.get(stateKey, ConfigurableWorkflowState.class)
                .orElseGet(ConfigurableWorkflowState::new);
        ReasonerInput input = new ReasonerInput(
                event,
                bot.getId(),
                workflowResult.getReplyMessage(),
                state.getData(),
                lastUserMessage(event));
        ReasonerOutput output = reasoner.reason(input);
        if (output == null) {
            return ReasonerOutput.empty();
        }

        if (!output.getStatePatch().isEmpty()) {
            output.getStatePatch().forEach(state::put);
            stateStore.put(stateKey, state);
        }

        for (ProposedToolCall toolCall : output.getProposedToolCalls()) {
            try {
                Object toolResult = toolRunner.run(toolCall.toolId(), toolCall.args(), bot.getToolPolicy());
                auditRecorder.record(AuditLog.fromEvent(event, bot.getId(), "reasoner_tool",
                        toolResult != null ? toolResult.toString() : ""));
            } catch (RuntimeException e) {
                auditRecorder.record(AuditLog.fromEvent(event, bot.getId(), "reasoner_tool_error", e.getMessage()));
                log.warn("Reasoner tool execution failed for {}: {}", bot.getId(), e.getMessage());
            }
        }

        if (!output.getReplyText().isBlank()) {
            auditRecorder.record(AuditLog.fromEvent(event, bot.getId(), "reasoner", output.getReplyText()));
        }
        return output;
    }

    private static String selectReplyText(WorkflowRunResult workflowResult, ReasonerOutput reasonerOutput) {
        if (workflowResult != null) {
            if (workflowResult.getReplyMessage() != null && !workflowResult.getReplyMessage().isBlank()) {
                return workflowResult.getReplyMessage();
            }
            if (workflowResult.getErrorMessage() != null && !workflowResult.getErrorMessage().isBlank()) {
                return workflowResult.getErrorMessage();
            }
        }
        if (reasonerOutput != null && !reasonerOutput.getReplyText().isBlank()) {
            return reasonerOutput.getReplyText();
        }
        return "";
    }

    private static OutboundResponse buildOutboundResponse(WorkflowRunResult workflowResult, ReasonerOutput reasonerOutput) {
        if (workflowResult != null && workflowResult.getRichReply().isPresent()) {
            return workflowResult.getRichReply().get();
        }
        if (reasonerOutput != null && reasonerOutput.getRichReply().isPresent()) {
            return reasonerOutput.getRichReply().get();
        }
        String text = selectReplyText(workflowResult, reasonerOutput);
        if (text == null || text.isEmpty()) {
            return null;
        }
        return OutboundResponse.ofText(text);
    }

    /** Discord deferred interactions must receive a follow-up; use when workflow/reasoner produce no outbound. */
    private static final OutboundResponse DEFERRED_INTERACTION_ACK = OutboundResponse.ofText("Recorded.");

    private void deliverReply(Event event, OutboundResponse outbound, ReplyTarget target) {
        if (outbound == null) {
            if (target instanceof com.vinekeepers.interactions.InteractionTarget it && it.alreadyDeferred()) {
                outbound = DEFERRED_INTERACTION_ACK;
            } else {
                return;
            }
        }
        String sourcePrefix = event.getSourceId().contains(":") ? event.getSourceId().substring(0, event.getSourceId().indexOf(':')) : event.getSourceId();
        AppReplySink sink = sinks.get(sourcePrefix);
        if (sink != null) {
            if (target instanceof com.vinekeepers.interactions.InteractionTarget it && it.alreadyDeferred()) {
                sink.sendFollowUp(outbound, target);
            } else if (target instanceof com.vinekeepers.interactions.InteractionTarget) {
                sink.respondImmediately(outbound, target);
            } else {
                sink.respondImmediately(outbound, target);
            }
            return;
        }
        ReplySender sender = replySendersByConnectorId.get(sourcePrefix);
        if (sender != null) {
            String text = outbound.getText().orElse("");
            if (!text.isEmpty()) {
                String channelId = target.channelId();
                String messageId = target.messageId();
                if (channelId != null) {
                    sender.send(channelId, messageId, text);
                }
            }
        }
    }

    private static String lastUserMessage(Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        return context.getText() != null ? context.getText() : "";
    }

}
