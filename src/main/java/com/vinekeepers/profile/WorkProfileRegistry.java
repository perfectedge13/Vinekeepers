package com.vinekeepers.profile;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of loaded work profiles, keyed by profileId.
 */
public final class WorkProfileRegistry {

    private final ConcurrentHashMap<String, WorkProfileDefinition> byId = new ConcurrentHashMap<>();

    public void register(WorkProfileDefinition profile) {
        if (profile == null) {
            return;
        }
        byId.put(profile.getProfileId(), profile);
    }

    public Optional<WorkProfileDefinition> get(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(profileId.trim()));
    }

    public Map<String, WorkProfileDefinition> snapshot() {
        return Map.copyOf(byId);
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
