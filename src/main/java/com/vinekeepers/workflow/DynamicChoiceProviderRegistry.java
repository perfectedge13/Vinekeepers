package com.vinekeepers.workflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named dynamic choice providers for workflow steps.
 */
public final class DynamicChoiceProviderRegistry {

    private final Map<String, DynamicChoiceProvider> providers = new ConcurrentHashMap<>();

    public void register(String id, DynamicChoiceProvider provider) {
        if (id != null && !id.isBlank() && provider != null) {
            providers.put(id, provider);
        }
    }

    public DynamicChoiceProvider get(String id) {
        return id != null ? providers.get(id) : null;
    }
}
