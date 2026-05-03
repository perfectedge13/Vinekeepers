package com.vinekeepers.bot;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final ConversationMode conversationMode;
    private final String sessionKeyStrategy;
    /** Optional env key for this bot's Discord token (e.g. DISCORD_BOT_TOKEN); enables per-bot connector identity. */
    private final String discordTokenEnvKey;
    /** When true, this bot is routed inbound events for lifecycle rooms it owns (single-owner precedence). */
    private final boolean handlesOwnedSpaces;
    /** Optional allow-list for configured per-step workflow models. Empty means any non-blank model is accepted. */
    private final Set<String> supportedStepModels;

    public BotDefinition(
            String id,
            Persona persona,
            ModelProfile modelProfile,
            ToolPolicy toolPolicy,
            MemoryPolicy memoryPolicy) {
        this(id, persona, modelProfile, toolPolicy, memoryPolicy, "stub", null,
                ConversationMode.SINGLE_EVENT, null);
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
                ConversationMode.SINGLE_EVENT, null);
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
            String discordTokenEnvKey) {
        this(id, persona, modelProfile, toolPolicy, memoryPolicy, workflowType, workflowParams,
                conversationMode, sessionKeyStrategy, discordTokenEnvKey, false);
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
            String discordTokenEnvKey,
            boolean handlesOwnedSpaces) {
        this(id, persona, modelProfile, toolPolicy, memoryPolicy, workflowType, workflowParams, conversationMode,
                sessionKeyStrategy, discordTokenEnvKey, handlesOwnedSpaces, Set.of());
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
            String discordTokenEnvKey,
            boolean handlesOwnedSpaces,
            Set<String> supportedStepModels) {
        this.id = Objects.requireNonNull(id, "id");
        this.persona = Objects.requireNonNull(persona, "persona");
        this.modelProfile = Objects.requireNonNull(modelProfile, "modelProfile");
        this.toolPolicy = toolPolicy != null ? toolPolicy : ToolPolicy.allowAll();
        this.memoryPolicy = memoryPolicy != null ? memoryPolicy : new MemoryPolicy(4096);
        this.workflowType = workflowType != null && !workflowType.isBlank() ? workflowType : "stub";
        this.workflowParams = workflowParams != null ? Map.copyOf(workflowParams) : Map.of();
        this.conversationMode = conversationMode != null ? conversationMode : ConversationMode.SINGLE_EVENT;
        this.sessionKeyStrategy = sessionKeyStrategy;
        this.discordTokenEnvKey = (discordTokenEnvKey != null && !discordTokenEnvKey.isBlank()) ? discordTokenEnvKey : null;
        this.handlesOwnedSpaces = handlesOwnedSpaces;
        this.supportedStepModels = supportedStepModels != null ? Set.copyOf(supportedStepModels) : Set.of();
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
     * Optional env key for this bot's Discord token (e.g. DISCORD_BOT_TOKEN).
     * When set, Bootstrap can create a dedicated Discord gateway/sender for this bot.
     */
    public String getDiscordTokenEnvKey() {
        return discordTokenEnvKey;
    }

    /**
     * When true, the router adds this bot for inbound events in lifecycle rooms it owns (single-owner precedence).
     */
    public boolean isHandlesOwnedSpaces() {
        return handlesOwnedSpaces;
    }

    public Set<String> getSupportedStepModels() {
        return supportedStepModels;
    }

    public Set<String> getSupportedModels() {
        return supportedStepModels;
    }
}
