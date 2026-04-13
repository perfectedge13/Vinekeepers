package com.vinekeepers.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional model configuration attached to a workflow step.
 */
public final class WorkflowStepModel {

    private final String provider;
    private final String modelId;

    public WorkflowStepModel(String provider, String modelId) {
        this.provider = provider != null ? provider.trim() : "";
        this.modelId = modelId != null ? modelId.trim() : "";
    }

    public String getProvider() {
        return provider;
    }

    public String getModelId() {
        return modelId;
    }

    public boolean isEmpty() {
        return provider.isBlank() && modelId.isBlank();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!provider.isBlank()) {
            out.put("provider", provider);
        }
        if (!modelId.isBlank()) {
            out.put("modelId", modelId);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static WorkflowStepModel fromConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            String modelId = text.trim();
            if (modelId.isBlank()) {
                return null;
            }
            return new WorkflowStepModel("openai", modelId);
        }
        if (raw instanceof Map<?, ?> map) {
            String provider = toText(((Map<String, Object>) map).get("provider"));
            String modelId = toText(((Map<String, Object>) map).get("modelId"));
            if (provider.isBlank() && modelId.isBlank()) {
                return null;
            }
            if (provider.isBlank()) {
                provider = "openai";
            }
            return new WorkflowStepModel(provider, modelId);
        }
        throw new IllegalArgumentException("Workflow step model must be a string or map with provider/modelId.");
    }

    private static String toText(Object value) {
        return value != null ? value.toString().trim() : "";
    }
}
