package com.vinekeepers.bot;

import java.util.Objects;

/**
 * Runtime instance of a bot provisioned from a template (e.g. Arrietty).
 * Distinguishes bot id (template id) from display name and channel binding.
 */
public final class RuntimeBotInstance {

    private final String instanceId;
    private final String templateBotId;
    private final String displayName;
    private final String channelId;

    public RuntimeBotInstance(String instanceId, String templateBotId, String displayName, String channelId) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.templateBotId = Objects.requireNonNull(templateBotId, "templateBotId");
        this.displayName = displayName != null ? displayName : templateBotId;
        this.channelId = channelId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getTemplateBotId() {
        return templateBotId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getChannelId() {
        return channelId;
    }
}
