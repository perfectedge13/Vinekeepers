package com.vinekeepers.bot;

import java.util.Objects;

/**
 * Model configuration (provider, model id, params).
 */
public final class ModelProfile {

    private final String provider;
    private final String modelId;

    public ModelProfile(String provider, String modelId) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
    }

    public String getProvider() {
        return provider;
    }

    public String getModelId() {
        return modelId;
    }
}
