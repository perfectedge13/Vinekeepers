package com.vinekeepers.core.cursor;

import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.workflow.actions.CreateThreadAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls active Cursor cloud runs and relays updates back to Discord.
 */
public final class CursorCloudRunMonitor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CursorCloudRunMonitor.class);
    private static final long DEFAULT_POLL_INTERVAL_MS = 15_000L;

    private final CursorCloudAdapter adapter;
    private final StateStore stateStore;
    private final ScheduledExecutorService scheduler;
    private final long pollIntervalMs;

    private volatile ReplySender replySender;

    public CursorCloudRunMonitor(CursorCloudAdapter adapter, StateStore stateStore, long pollIntervalMs) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cursor-cloud-run-monitor");
            thread.setDaemon(true);
            return thread;
        });
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : DEFAULT_POLL_INTERVAL_MS;
    }

    public void setReplySender(ReplySender replySender) {
        this.replySender = replySender;
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::tickSafely, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    void tickSafely() {
        try {
            tick();
        } catch (RuntimeException e) {
            log.warn("Cursor cloud monitor tick failed: {}", e.getMessage());
        }
    }

    void tick() {
        for (String key : stateStore.keys()) {
            Optional<LifecycleRunRecord> candidate = stateStore.get(key, LifecycleRunRecord.class);
            if (candidate.isEmpty()) {
                continue;
            }
            LifecycleRunRecord runState = candidate.get();
            if (runState.isTerminal() && runState.isTerminalNotificationSent()) {
                continue;
            }
            try {
                pollRun(runState);
            } catch (RuntimeException e) {
                log.warn("Cursor poll failed for agent {}: {}", runState.getAgentId(), e.getMessage());
            }
        }
    }

    private void pollRun(LifecycleRunRecord runState) {
        String previousStatus = runState.getStatus();
        String previousPrUrl = runState.getPrUrl();
        String previousAssistantMessageId = runState.getLastAssistantMessageId();

        CursorAgentDetails details = adapter.getAgent(runState.getAgentId());
        runState.applyAgentDetails(details);

        if (!Objects.equals(previousStatus, runState.getStatus())) {
            sendUpdate(runState, formatStatusUpdate(runState));
        }
        if (!Objects.equals(previousPrUrl, runState.getPrUrl()) && runState.getPrUrl() != null) {
            sendUpdate(runState, "Cursor opened a pull request: " + runState.getPrUrl());
        }

        CursorAgentConversation conversation = adapter.getConversation(runState.getAgentId());
        Optional<CursorAgentMessage> latestAssistant = conversation.latestAssistantMessage();
        if (latestAssistant.isPresent()
                && !Objects.equals(previousAssistantMessageId, latestAssistant.get().id())) {
            runState.recordAssistantMessage(latestAssistant.get());
            sendUpdate(runState, "Cursor feedback: " + latestAssistant.get().text());
        }

        if (runState.isTerminal() && !runState.isTerminalNotificationSent()) {
            sendUpdate(runState, formatTerminalUpdate(runState));
            runState.markTerminalNotificationSent();
        }
    }

    private void sendUpdate(LifecycleRunRecord runState, String message) {
        ReplySender sender = replySender;
        if (sender == null || message == null || message.isBlank()) {
            return;
        }
        String deliveryId = runState.getDeliveryChannelId();
        boolean useThread = deliveryId != null && !deliveryId.isBlank() && !CreateThreadAction.THREAD_CREATE_FAILED.equals(deliveryId);
        String sendTarget = useThread ? deliveryId : runState.getChannelId();
        String messageId = useThread ? null : runState.getReplyToMessageId();
        sender.send(sendTarget, messageId, message);
    }

    private static String formatStatusUpdate(LifecycleRunRecord runState) {
        StringBuilder message = new StringBuilder("Cursor agent status: ")
                .append(runState.getStatus());
        if (runState.getBranchName() != null && !runState.getBranchName().isBlank()) {
            message.append(" on branch `").append(runState.getBranchName()).append("`");
        }
        if (runState.getAgentUrl() != null && !runState.getAgentUrl().isBlank()) {
            message.append(". Track it at ").append(runState.getAgentUrl());
        }
        return message.toString();
    }

    private static String formatTerminalUpdate(LifecycleRunRecord runState) {
        String status = runState.getStatus() != null ? runState.getStatus() : "UNKNOWN";
        if ("FINISHED".equalsIgnoreCase(status)) {
            StringBuilder message = new StringBuilder("Cursor finished the Luna request.");
            if (runState.getPrUrl() != null && !runState.getPrUrl().isBlank()) {
                message.append(" PR: ").append(runState.getPrUrl());
            }
            if (runState.getSummary() != null && !runState.getSummary().isBlank()) {
                message.append(" Summary: ").append(runState.getSummary());
            }
            return message.toString();
        }
        StringBuilder message = new StringBuilder("Cursor run ended with status ")
                .append(status)
                .append(".");
        if (runState.getSummary() != null && !runState.getSummary().isBlank()) {
            message.append(" Summary: ").append(runState.getSummary());
        } else if (runState.getLastAssistantMessage() != null && !runState.getLastAssistantMessage().isBlank()) {
            message.append(" Last feedback: ").append(runState.getLastAssistantMessage());
        }
        return message.toString();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
