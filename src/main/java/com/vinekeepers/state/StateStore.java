package com.vinekeepers.state;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store for workflow state by key (e.g. conversation id).
 */
public final class StateStore {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <S> Optional<S> get(String key, Class<S> type) {
        Object v = store.get(key);
        if (v == null) return Optional.empty();
        if (type.isInstance(v)) return Optional.of((S) v);
        return Optional.empty();
    }

    public void put(String key, Object state) {
        store.put(key, state);
    }

    public void remove(String key) {
        store.remove(key);
    }

    public boolean contains(String key) {
        return store.containsKey(key);
    }
}
