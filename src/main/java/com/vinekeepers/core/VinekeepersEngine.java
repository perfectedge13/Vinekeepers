package com.vinekeepers.core;

import com.vinekeepers.audit.AuditLog;
import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.Router;
import com.vinekeepers.connectors.DiscordReplySender;
import com.vinekeepers.events.Event;
import com.vinekeepers.events.EventSubscriber;
import com.vinekeepers.reasoner.Reasoner;
import com.vinekeepers.reasoner.ReasonerInput;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.workflow.WorkflowRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private volatile DiscordReplySender replySender;

    public VinekeepersEngine(
            Router router,
            StateStore stateStore,
            AuditRecorder auditRecorder) {
        this.router = Objects.requireNonNull(router, "router");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.auditRecorder = auditRecorder != null ? auditRecorder : e -> {};
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
     * Set the Discord reply sender so workflow replies can be sent back to Discord.
     */
    public void setReplySender(DiscordReplySender replySender) {
        this.replySender = replySender;
    }

    @Override
    public void onEvent(Event event) {
        log.debug("Engine received event: {} {}", event.getSourceId(), event.getKind());
        List<String> botIds = router.route(event);
        for (String botId : botIds) {
            handleEventForBot(event, botId);
        }
    }

    private void handleEventForBot(Event event, String botId) {
        BotDefinition bot = bots.get(botId);
        if (bot == null) {
            log.warn("No bot registered for id: {}", botId);
            return;
        }
        if (auditRecorder != null) {
            auditRecorder.record(AuditLog.fromEvent(event, botId, "received", ""));
        }
        WorkflowRunner runner = runners.get(botId);
        if (runner != null) {
            String message = runner.run(event, stateStore, botId);
            if (auditRecorder != null && message != null) {
                auditRecorder.record(AuditLog.fromEvent(event, botId, "workflow", message));
            }
            sendReplyIfDiscord(event, message);
        }
        Reasoner reasoner = reasoners.get(botId);
        if (reasoner != null) {
            ReasonerInput input = new ReasonerInput(event, botId, "");
            var output = reasoner.reason(input);
            if (auditRecorder != null) {
                auditRecorder.record(AuditLog.fromEvent(event, botId, "reasoner", output.getResponse()));
            }
        }
    }

    private void sendReplyIfDiscord(Event event, String message) {
        if (message == null || message.isEmpty() || replySender == null) return;
        if (!event.getSourceId().startsWith("discord:")) return;
        String channelId = event.getPayload("channelId", String.class);
        if (channelId == null) channelId = event.getPayload("channel", String.class);
        if (channelId == null) return;
        String messageId = event.getPayload("messageId", String.class);
        if (messageId == null) messageId = event.getPayload("message_id", String.class);
        replySender.send(channelId, messageId, message);
    }
}
