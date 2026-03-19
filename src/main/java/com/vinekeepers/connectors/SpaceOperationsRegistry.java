package com.vinekeepers.connectors;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of SpaceOperations by connector id (source prefix).
 * Used by workflow actions to delegate create room/thread to the appropriate connector.
 */
public final class SpaceOperationsRegistry {

    private final ConcurrentHashMap<String, SpaceOperations> byConnectorId = new ConcurrentHashMap<>();

    /**
     * Register space operations for a connector id (e.g. "discord").
     */
    public void register(String connectorId, SpaceOperations ops) {
        if (connectorId == null || connectorId.isBlank()) {
            return;
        }
        if (ops != null) {
            byConnectorId.put(connectorId.trim(), ops);
        }
    }

    /**
     * Get space operations for the given connector id, or null if not registered.
     */
    public SpaceOperations get(String connectorId) {
        if (connectorId == null || connectorId.isBlank()) {
            return null;
        }
        return byConnectorId.get(connectorId.trim());
    }
}
