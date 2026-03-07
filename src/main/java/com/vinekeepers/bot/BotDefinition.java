package com.vinekeepers.bot;

import java.util.Map;
import java.util.Objects;

/**
 * Definition of a bot: id, persona, model, tool and memory policies, workflow type and params.
 */
public final class BotDefinition {

    private final String id;
    private final Persona persona;
    private final ModelProfile modelProfile;
    private final ToolPolicy toolPolicy;
    private final MemoryPolicy memoryPolicy;
    private final String workflowType;
    private final Map<String, Object> workflowParams;

    public BotDefinition(
            String id,
            Persona persona,
            ModelProfile modelProfile,
            ToolPolicy toolPolicy,
            MemoryPolicy memoryPolicy) {
        this(id, persona, modelProfile, toolPolicy, memoryPolicy, "stub", null);
    }

    public BotDefinition(
            String id,
            Persona persona,
            ModelProfile modelProfile,
            ToolPolicy toolPolicy,
            MemoryPolicy memoryPolicy,
            String workflowType,
            Map<String, Object> workflowParams) {
        this.id = Objects.requireNonNull(id, "id");
        this.persona = Objects.requireNonNull(persona, "persona");
        this.modelProfile = Objects.requireNonNull(modelProfile, "modelProfile");
        this.toolPolicy = toolPolicy != null ? toolPolicy : ToolPolicy.allowAll();
        this.memoryPolicy = memoryPolicy != null ? memoryPolicy : new MemoryPolicy(4096);
        this.workflowType = workflowType != null && !workflowType.isBlank() ? workflowType : "stub";
        this.workflowParams = workflowParams != null ? Map.copyOf(workflowParams) : Map.of();
    }

    public String getId() {
        return id;
    }

    public Persona getPersona() {
        return persona;
    }

    public ModelProfile getModelProfile() {
        return modelProfile;
    }

    public ToolPolicy getToolPolicy() {
        return toolPolicy;
    }

    public MemoryPolicy getMemoryPolicy() {
        return memoryPolicy;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public Map<String, Object> getWorkflowParams() {
        return workflowParams;
    }
}
