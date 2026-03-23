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
import com.vinekeepers.workflow.actions.CoordinatorIntakeBootstrapAction;
import com.vinekeepers.workflow.ConfigurableWorkflowRunner;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.SessionKeyStrategies;
import com.vinekeepers.workflow.WorkflowRunResult;
import com.vinekeepers.workflow.WorkflowRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
    private static final String COORDINATOR_KICKOFF_FAILURE_KEY = "coordinatorKickoffFailureReason";
    private static final String COORDINATOR_KICKOFF_FAILURE_MESSAGE =
            "Planning hit an internal error during kickoff. See server logs.";

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
    /** Short-lived dedupe for duplicate Discord publishes (same message/interaction id). */
    private final ConcurrentHashMap<String, Long> discordEventDedupe = new ConcurrentHashMap<>();
    /** Covers slow engine work so the same Discord snowflake is not processed twice if a second gateway publish arrives late. */
    private static final long DISCORD_DEDUPE_TTL_MS = 180_000L;

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
     * (e.g. immediately after the intake bot posts the handoff). Does not use the router — only {@code coordinatorBotId} runs.
     * Idempotent: no-ops when the thread session already has planning in progress, is waiting for input, or
     * completed (conversational thread terminal).
     *
     * @return {@code RAN}, {@code RAN_ERROR}, {@code RAN_NO_OUTBOUND}, {@code SKIPPED_WAITING},
     *         {@code SKIPPED_IN_PROGRESS}, {@code SKIPPED_COMPLETED}, {@code NO_BOT}, or {@code NO_RUNNER}
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
                log.info(
                        "Coordinator planning kickoff skipped (waiting input): botId={} sessionKey={}",
                        coordinatorBotId,
                        stateKey);
                return "SKIPPED_WAITING";
            }
            if (s.getStatus() == ConfigurableWorkflowState.Status.COMPLETED
                    && ConfigurableWorkflowRunner.isThreadScopedSessionKey(stateKey, coordinatorBotId)) {
                log.info(
                        "Coordinator planning kickoff skipped (thread completed): botId={} sessionKey={}",
                        coordinatorBotId,
                        stateKey);
                return "SKIPPED_COMPLETED";
            }
            if ((s.getStatus() == ConfigurableWorkflowState.Status.ACTIVE
                    || s.getStatus() == ConfigurableWorkflowState.Status.ERROR)
                    && s.getStepIndex() > 0) {
                log.info(
                        "Coordinator planning kickoff skipped (in progress): botId={} sessionKey={} step={}",
                        coordinatorBotId,
                        stateKey,
                        s.getStepIndex());
                return "SKIPPED_IN_PROGRESS";
            }
        }
        auditRecorder.record(AuditLog.fromEvent(syntheticThreadMessage, coordinatorBotId, "received", ""));
        WorkflowDeliveryOutcome outcome = runWorkflowReasonerAndDeliver(syntheticThreadMessage, bot, coordinatorBotId);
        boolean kickoffVisibleOutcome = hasVisibleCoordinatorKickoffOutcome(stateKey);
        String failureDetail = summarizeKickoffFailure(outcome, kickoffVisibleOutcome);
        if (failureDetail != null) {
            markCoordinatorKickoffFailure(stateKey, failureDetail);
            boolean fallbackSent = outcome.replyDelivered()
                    || deliverKickoffFailureNotice(syntheticThreadMessage, coordinatorBotId);
            String status = classifyKickoffStatus(outcome);
            log.warn(
                    "Coordinator planning kickoff failed for botId={} sessionKey={} status={} detail={} fallbackSent={}",
                    coordinatorBotId,
                    stateKey,
                    status,
                    failureDetail,
                    fallbackSent);
            return status;
        }
        log.info("Coordinator planning kickoff finished for botId={} sessionKey={}", coordinatorBotId, stateKey);
        return "RAN";
    }

    @Override
    public void onEvent(Event event) {
        log.debug("Engine received event: {} {}", event.getSourceId(), event.getKind());
        if (isDuplicateDiscordPublication(event)) {
            log.debug("Skipping duplicate Discord event within dedupe window: {} {}", event.getSourceId(), event.getKind());
            return;
        }
        Map<String, Object> payload = event.getPayload();
        String correlationId = extractCorrelationId(payload, event.getKind());
        MDC.put("vk_correlationId", correlationId);
        MDC.put("vk_sourceId", event.getSourceId());
        MDC.put("vk_eventKind", event.getKind());
        if (payload != null) {
            Object ingest = payload.get("ingestBotId");
            if (ingest != null && !ingest.toString().isBlank()) {
                MDC.put("vk_ingestBotId", ingest.toString());
            }
        }
        long t0 = System.nanoTime();
        try {
            log.info(
                    "Engine handling event thread={} sourceId={} kind={} correlationId={}",
                    Thread.currentThread().getName(),
                    event.getSourceId(),
                    event.getKind(),
                    correlationId);
            List<String> botIds = router.route(event);
            if (isDiscordMessage(event) && !router.isCoordinatorExclusivePlanningDiscordEvent(event)) {
                addBotsWithWaitingSessionForDiscordMessage(event, botIds);
            }
            for (String botId : botIds) {
                MDC.put("vk_routeBotId", botId);
                handleEventForBot(event, botId);
                MDC.remove("vk_routeBotId");
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (ms > 2_000L) {
                log.warn(
                        "Engine event slow: {}ms thread={} correlationId={} kind={}",
                        ms,
                        Thread.currentThread().getName(),
                        correlationId,
                        event.getKind());
            } else {
                log.debug("Engine event finished: {}ms correlationId={}", ms, correlationId);
            }
        } finally {
            MDC.remove("vk_correlationId");
            MDC.remove("vk_sourceId");
            MDC.remove("vk_eventKind");
            MDC.remove("vk_ingestBotId");
            MDC.remove("vk_routeBotId");
        }
    }

    private static String extractCorrelationId(Map<String, Object> payload, String kind) {
        if (payload == null) {
            return "n/a";
        }
        if ("message".equals(kind)) {
            Object mid = payload.get("messageId");
            return mid != null && !mid.toString().isBlank() ? mid.toString() : "n/a";
        }
        if ("interaction".equals(kind)) {
            Object iid = payload.get("interactionId");
            return iid != null && !iid.toString().isBlank() ? iid.toString() : "n/a";
        }
        return "n/a";
    }

    private static boolean isDiscordMessage(Event event) {
        return "message".equals(event.getKind()) && event.getSourceId() != null && event.getSourceId().startsWith("discord");
    }

    private static boolean isDiscordInteraction(Event event) {
        return "interaction".equals(event.getKind()) && event.getSourceId() != null && event.getSourceId().startsWith("discord");
    }

    /**
     * Multiple bot tokens may observe the same Discord message; only the first publication within the TTL is processed.
     */
    private boolean isDuplicateDiscordPublication(Event event) {
        if (!isDiscordMessage(event) && !isDiscordInteraction(event)) {
            return false;
        }
        Map<String, Object> p = event.getPayload();
        if (p == null) {
            return false;
        }
        String dedupeId;
        if (isDiscordMessage(event)) {
            Object mid = p.get("messageId");
            dedupeId = mid != null ? mid.toString() : "";
        } else {
            Object iid = p.get("interactionId");
            dedupeId = iid != null ? iid.toString() : "";
        }
        if (dedupeId.isBlank()) {
            return false;
        }
        String key = event.getSourceId() + ":" + event.getKind() + ":" + dedupeId;
        long now = System.currentTimeMillis();
        Long prev = discordEventDedupe.putIfAbsent(key, now);
        if (prev == null) {
            trimDiscordDedupeIfHuge();
            return false;
        }
        if (now - prev < DISCORD_DEDUPE_TTL_MS) {
            return true;
        }
        discordEventDedupe.put(key, now);
        return false;
    }

    private void trimDiscordDedupeIfHuge() {
        if (discordEventDedupe.size() <= 8_000) {
            return;
        }
        long cutoff = System.currentTimeMillis() - DISCORD_DEDUPE_TTL_MS;
        discordEventDedupe.entrySet().removeIf(e -> e.getValue() < cutoff);
        if (discordEventDedupe.size() > 8_000) {
            discordEventDedupe.clear();
        }
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

        String sessionKey =
                SessionKeyStrategies.resolve(bot.getSessionKeyStrategy(), event)
                        .resolveSessionKey(botId, event);
        MDC.put("vk_sessionKey", sessionKey);
        try {
            auditRecorder.record(AuditLog.fromEvent(event, botId, "received", ""));
            runWorkflowReasonerAndDeliver(event, bot, botId);
        } finally {
            MDC.remove("vk_sessionKey");
        }
    }

    private WorkflowDeliveryOutcome runWorkflowReasonerAndDeliver(Event event, BotDefinition bot, String botId) {
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
            return new WorkflowDeliveryOutcome(workflowResult, false, outbound == null, "missing sourceId");
        }
        String sourcePrefix = sourceId.contains(":") ? sourceId.substring(0, sourceId.indexOf(':')) : sourceId;
        ReplyTargetResolver resolver = resolversByConnectorId.get(sourcePrefix);
        if (resolver == null) {
            log.warn("No reply target resolver for source prefix {}; skipping reply delivery", sourcePrefix);
            return new WorkflowDeliveryOutcome(workflowResult, false, outbound == null, "missing reply target resolver");
        }
        Optional<ReplyTarget> targetOpt = resolver.resolve(event);
        if (targetOpt.isEmpty()) {
            log.warn("Reply target resolver returned empty; skipping reply delivery");
            return new WorkflowDeliveryOutcome(workflowResult, false, outbound == null, "reply target unresolved");
        }
        boolean delivered = deliverReply(event, outbound, targetOpt.get(), botId);
        return new WorkflowDeliveryOutcome(workflowResult, delivered, outbound == null, delivered ? "" : "reply not delivered");
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

    private OutboundResponse deferredInteractionAck(String handlingBotId) {
        if (handlingBotId != null && !handlingBotId.isBlank()) {
            BotDefinition b = bots.get(handlingBotId);
            if (b != null && usesPlanningCoordinatorDeferredInteractionAck(b)) {
                return OutboundResponse.ofText("Working on your update…");
            }
        }
        return DEFERRED_INTERACTION_ACK;
    }

    /**
     * When {@code workflow.params.deferredInteractionAckPlanningCoordinator} is true, use planning-style deferred copy.
     * Otherwise, when the bot uses the Arrietty room workflow ({@code workflowRef} starting with {@code arrietty_room} or
     * {@code workflow.type} {@code arrietty_room}), use the same deferred-interaction copy.
     */
    private static boolean usesPlanningCoordinatorDeferredInteractionAck(BotDefinition b) {
        if (b.getWorkflowParams() != null) {
            Object flag = b.getWorkflowParams().get("deferredInteractionAckPlanningCoordinator");
            if (Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag))) {
                return true;
            }
        }
        return arriettyRoomWorkflowRefOrType(b);
    }

    private static boolean arriettyRoomWorkflowRefOrType(BotDefinition b) {
        if ("arrietty_room".equalsIgnoreCase(b.getWorkflowType())) {
            return true;
        }
        if (!"configured".equalsIgnoreCase(b.getWorkflowType()) || b.getWorkflowParams() == null) {
            return false;
        }
        Object ref = b.getWorkflowParams().get("workflowRef");
        if (ref == null) {
            return false;
        }
        String r = ref.toString().trim();
        return r.startsWith("arrietty_room");
    }

    private boolean deliverReply(Event event, OutboundResponse outbound, ReplyTarget target, String handlingBotId) {
        ReplyTarget resolved = target;
        if (handlingBotId != null && !handlingBotId.isBlank()
                && target instanceof com.vinekeepers.interactions.ChannelTarget ct
                && ct.replyAsBotId() == null) {
            resolved = new com.vinekeepers.interactions.ChannelTarget(
                    ct.sourceId(), ct.channelId(), ct.messageId(), handlingBotId);
        }
        if (outbound == null) {
            if (resolved instanceof com.vinekeepers.interactions.InteractionTarget it && it.alreadyDeferred()) {
                outbound = deferredInteractionAck(handlingBotId);
            } else {
                return false;
            }
        }
        String sourcePrefix = event.getSourceId().contains(":") ? event.getSourceId().substring(0, event.getSourceId().indexOf(':')) : event.getSourceId();
        AppReplySink sink = sinks.get(sourcePrefix);
        if (sink != null) {
            if (resolved instanceof com.vinekeepers.interactions.InteractionTarget it && it.alreadyDeferred()) {
                sink.sendFollowUp(outbound, resolved);
            } else if (resolved instanceof com.vinekeepers.interactions.InteractionTarget) {
                sink.respondImmediately(outbound, resolved);
            } else {
                sink.respondImmediately(outbound, resolved);
            }
            return true;
        }
        ReplySender sender = replySendersByConnectorId.get(sourcePrefix);
        if (sender != null) {
            String text = outbound.getText().orElse("");
            if (!text.isEmpty()) {
                String channelId = resolved.channelId();
                String messageId = resolved.messageId();
                if (channelId != null) {
                    sender.send(channelId, messageId, text);
                    return true;
                }
            }
        }
        return false;
    }

    private static String lastUserMessage(Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        return context.getText() != null ? context.getText() : "";
    }

    private String summarizeKickoffFailure(WorkflowDeliveryOutcome outcome, boolean kickoffVisibleOutcome) {
        if (outcome == null) {
            return "Coordinator kickoff outcome missing.";
        }
        WorkflowRunResult workflowResult = outcome.workflowResult();
        if (workflowResult != null) {
            String error = workflowResult.getErrorMessage();
            if (error != null && !error.isBlank()) {
                return error;
            }
            boolean blankTerminal =
                    !workflowResult.isCompleted()
                            && !workflowResult.isWaiting()
                            && workflowResult.getReplyMessage().isBlank()
                            && workflowResult.getRichReply().isEmpty();
            if (blankTerminal) {
                return "Workflow failed without an error message.";
            }
        }
        if (!outcome.replyDelivered() && !kickoffVisibleOutcome) {
            return outcome.deliveryIssue() != null && !outcome.deliveryIssue().isBlank()
                    ? "Coordinator kickoff produced no visible reply: " + outcome.deliveryIssue()
                    : "Coordinator kickoff produced no visible reply.";
        }
        return null;
    }

    private boolean hasVisibleCoordinatorKickoffOutcome(String stateKey) {
        if (stateKey == null || stateKey.isBlank()) {
            return false;
        }
        Optional<ConfigurableWorkflowState> stateOpt = stateStore.get(stateKey, ConfigurableWorkflowState.class);
        if (stateOpt.isEmpty()) {
            return false;
        }
        Object raw = stateOpt.get().get(CoordinatorIntakeBootstrapAction.KICKOFF_VISIBLE_OUTCOME_KEY);
        return raw != null
                && CoordinatorIntakeBootstrapAction.KICKOFF_VISIBLE_OUTCOME_TRUE.equalsIgnoreCase(raw.toString().trim());
    }

    private String classifyKickoffStatus(WorkflowDeliveryOutcome outcome) {
        WorkflowRunResult workflowResult = outcome != null ? outcome.workflowResult() : null;
        boolean workflowErrored =
                workflowResult != null
                        && ((workflowResult.getErrorMessage() != null)
                        || (!workflowResult.isCompleted()
                        && !workflowResult.isWaiting()
                        && workflowResult.getReplyMessage().isBlank()
                        && workflowResult.getRichReply().isEmpty()));
        return workflowErrored ? "RAN_ERROR" : "RAN_NO_OUTBOUND";
    }

    private void markCoordinatorKickoffFailure(String stateKey, String reason) {
        ConfigurableWorkflowState state =
                stateStore.get(stateKey, ConfigurableWorkflowState.class).orElseGet(ConfigurableWorkflowState::new);
        state.put(COORDINATOR_KICKOFF_FAILURE_KEY, reason != null ? reason : "Coordinator kickoff failed.");
        state.markError();
        stateStore.put(stateKey, state);
    }

    private boolean deliverKickoffFailureNotice(Event event, String botId) {
        String sourceId = event.getSourceId();
        if (sourceId == null) {
            return false;
        }
        String sourcePrefix = sourceId.contains(":") ? sourceId.substring(0, sourceId.indexOf(':')) : sourceId;
        ReplyTargetResolver resolver = resolversByConnectorId.get(sourcePrefix);
        if (resolver == null) {
            return false;
        }
        Optional<ReplyTarget> targetOpt = resolver.resolve(event);
        if (targetOpt.isEmpty()) {
            return false;
        }
        return deliverReply(event, OutboundResponse.ofText(COORDINATOR_KICKOFF_FAILURE_MESSAGE), targetOpt.get(), botId);
    }

    private record WorkflowDeliveryOutcome(
            WorkflowRunResult workflowResult,
            boolean replyDelivered,
            boolean outboundMissing,
            String deliveryIssue) {}

}
