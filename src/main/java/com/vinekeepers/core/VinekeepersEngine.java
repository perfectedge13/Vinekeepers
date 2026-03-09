package com.vinekeepers.core;

import com.vinekeepers.audit.AuditLog;
import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.NormalizedEventContext;
import com.vinekeepers.bot.Router;
import com.vinekeepers.connectors.DiscordReplySender;
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
    private volatile DiscordReplySender replySender;

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
     * Set the Discord reply sender for legacy text-only delivery when no sink is registered.
     */
    public void setReplySender(DiscordReplySender replySender) {
        this.replySender = replySender;
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
        ReplyTarget target = resolveReplyTarget(event);
        deliverReply(event, outbound, target);
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

    private static ReplyTarget resolveReplyTarget(Event event) {
        String sourceId = event.getSourceId();
        String channelId = event.getPayload("channelId", String.class);
        if (channelId == null) {
            channelId = event.getPayload("channel", String.class);
        }
        if (channelId == null) {
            channelId = "";
        }
        String messageId = event.getPayload("messageId", String.class);
        if (messageId == null) {
            messageId = event.getPayload("message_id", String.class);
        }
        if (messageId == null) {
            messageId = "";
        }
        Boolean deferred = event.getPayload("deferred", Boolean.class);
        String interactionId = event.getPayload("interactionId", String.class);
        String token = event.getPayload("token", String.class);
        if ("interaction".equals(event.getKind()) && interactionId != null && token != null) {
            return new com.vinekeepers.interactions.InteractionTarget(
                    sourceId, channelId, messageId, interactionId, token, Boolean.TRUE.equals(deferred));
        }
        return new com.vinekeepers.interactions.ChannelTarget(sourceId, channelId, messageId);
    }

    private void deliverReply(Event event, OutboundResponse outbound, ReplyTarget target) {
        if (outbound == null) {
            return;
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
        if (replySender != null && event.getSourceId().startsWith("discord:")) {
            String text = outbound.getText().orElse("");
            if (!text.isEmpty()) {
                String channelId = event.getPayload("channelId", String.class);
                if (channelId == null) channelId = event.getPayload("channel", String.class);
                String messageId = event.getPayload("messageId", String.class);
                if (messageId == null) messageId = event.getPayload("message_id", String.class);
                if (channelId != null) {
                    replySender.send(channelId, messageId, text);
                }
            }
        }
    }

    private static String lastUserMessage(Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        return context.getText() != null ? context.getText() : "";
    }

}
