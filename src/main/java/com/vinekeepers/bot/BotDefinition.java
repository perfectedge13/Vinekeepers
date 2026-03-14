package com.vinekeepers.bot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Definition of a bot: id, persona, model, tool and memory policies, workflow type and params.
 * Connector-scoped identity (e.g. Discord token key, handlesOwnedSpaces) is held in
 * connectorIdentities; use getConnectorIdentity(connectorId) for adapter-specific attributes.
 */
public final class BotDefinition {

    private final String id;
    private final Persona persona;
    private final ModelProfile modelProfile;
    private final ToolPolicy toolPolicy;
    private final MemoryPolicy memoryPolicy;
    private final String workflowType;
    private final Map<String, Object> workflowParams;
    private final ConversationMode conversationMode;
    private final String sessionKeyStrategy;
    private final Map<String, ConnectorIdentity> connectorIdentities;

    public BotDefinition(
            String id,
            Persona persona,
            ModelProfile modelProfile,
            ToolPolicy toolPolicy,
            MemoryPolicy memoryPolicy) {
        this(id, persona, modelProfile, toolPolicy, memoryPolicy, "stub", null,
                ConversationMode.SINGLE_EVENT, null, null);
    }

    public BotDefinition(
            String id,
            Persona persona,
            ModelProfile modelProfile,
            ToolPolicy toolPolicy,
            MemoryPolicy memoryPolicy,
            String workflowType,
            Map<String, Object> workflowParams) {
        this(id, persona, modelProfile, toolPolicy, memoryPolicy, workflowType, workflowParams,
                ConversationMode.SINGLE_EVENT, null, null);
    }

    public BotDefinition(
            String id,
            Persona persona,
            ModelProfile modelProfile,
            ToolPolicy toolPolicy,
            MemoryPolicy memoryPolicy,
            String workflowType,
            Map<String, Object> workflowParams,
            ConversationMode conversationMode,
            String sessionKeyStrategy) {
        this(id, persona, modelProfile, toolPolicy, memoryPolicy, workflowType, workflowParams,
                conversationMode, sessionKeyStrategy, null);
    }

    public BotDefinition(
            String id,
            Persona persona,
            ModelProfile modelProfile,
            ToolPolicy toolPolicy,
            MemoryPolicy memoryPolicy,
            String workflowType,
            Map<String, Object> workflowParams,
            ConversationMode conversationMode,
            String sessionKeyStrategy,
            Map<String, ConnectorIdentity> connectorIdentities) {
        this.id = Objects.requireNonNull(id, "id");
        this.persona = Objects.requireNonNull(persona, "persona");
        this.modelProfile = Objects.requireNonNull(modelProfile, "modelProfile");
        this.toolPolicy = toolPolicy != null ? toolPolicy : ToolPolicy.allowAll();
        this.memoryPolicy = memoryPolicy != null ? memoryPolicy : new MemoryPolicy(4096);
        this.workflowType = workflowType != null && !workflowType.isBlank() ? workflowType : "stub";
        this.workflowParams = workflowParams != null ? Map.copyOf(workflowParams) : Map.of();
        this.conversationMode = conversationMode != null ? conversationMode : ConversationMode.SINGLE_EVENT;
        this.sessionKeyStrategy = sessionKeyStrategy;
        this.connectorIdentities = connectorIdentities != null ? Map.copyOf(connectorIdentities) : Map.of();
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

    public ConversationMode getConversationMode() {
        return conversationMode;
    }

    public String getSessionKeyStrategy() {
        return sessionKeyStrategy;
    }

    /**
     * Returns connector-scoped identity for the given connector id, if present.
     * Adapters read attributes via getAttribute(key), e.g. "tokenEnvKey", "handlesOwnedSpaces" for Discord.
     */
    public Optional<ConnectorIdentity> getConnectorIdentity(String connectorId) {
        if (connectorId == null || connectorId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(connectorIdentities.get(connectorId));
    }
}
