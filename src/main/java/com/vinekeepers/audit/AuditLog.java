package com.vinekeepers.audit;

import com.vinekeepers.events.Event;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit log entry for events and engine actions.
 */
public final class AuditLog {

    private final Instant timestamp;
    private final String eventSourceId;
    private final String eventKind;
    private final String botId;
    private final String action;
    private final String detail;

    public AuditLog(Instant timestamp, String eventSourceId, String eventKind, String botId, String action, String detail) {
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.eventSourceId = eventSourceId != null ? eventSourceId : "";
        this.eventKind = eventKind != null ? eventKind : "";
        this.botId = botId != null ? botId : "";
        this.action = action != null ? action : "";
        this.detail = detail != null ? detail : "";
    }

    public static AuditLog fromEvent(Event event, String botId, String action, String detail) {
        return new AuditLog(
                Instant.now(),
                event.getSourceId(),
                event.getKind(),
                botId,
                action,
                detail);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getEventSourceId() {
        return eventSourceId;
    }

    public String getEventKind() {
        return eventKind;
    }

    public String getBotId() {
        return botId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }
}
