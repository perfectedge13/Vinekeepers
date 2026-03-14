package com.vinekeepers.connectors;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of connector adapters by connector id.
 * Bootstrap registers adapters and invokes registerBots on each as needed.
 */
public final class ConnectorRegistry {

    private final Map<String, ConnectorAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * Register an adapter for the given connector id.
     */
    public void register(String connectorId, ConnectorAdapter adapter) {
        if (connectorId == null || connectorId.isBlank()) {
            return;
        }
        adapters.put(connectorId, Objects.requireNonNull(adapter, "adapter"));
    }

    /**
     * Get the adapter for the given connector id, or null if not registered.
     */
    public ConnectorAdapter get(String connectorId) {
        return connectorId != null ? adapters.get(connectorId) : null;
    }
}
