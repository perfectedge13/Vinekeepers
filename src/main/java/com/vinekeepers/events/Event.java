package com.vinekeepers.events;

import java.util.Map;
import java.util.Objects;

/**
 * An event with a filterable payload (key-value attributes) for routing.
 */
public final class Event {

    private final String sourceId;
    private final String kind;
    private final Map<String, Object> payload;

    public Event(String sourceId, String kind, Map<String, Object> payload) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getKind() {
        return kind;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @SuppressWarnings("unchecked")
    public <T> T getPayload(String key, Class<T> type) {
        Object v = payload.get(key);
        if (v == null) return null;
        if (type.isInstance(v)) return (T) v;
        return null;
    }
}
