package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.events.Event;

import java.util.Map;

/**
 * Workflow action: create a Discord text channel (lifecycle room). Bind: guildId, channelName (or from state).
 * When channelName is blank, builds a Discord-valid name from state project + codeChange (repo segment + slug + short suffix).
 * Returns channel id or "CHANNEL_CREATE_FAILED" on gateway failure.
 */
public final class CreateChannelAction implements com.vinekeepers.workflow.WorkflowAction {

    /** Sentinel returned when channel creation fails (e.g. for workflow branch). */
    public static final String CHANNEL_CREATE_FAILED = "CHANNEL_CREATE_FAILED";

    private static final int MAX_DISCORD_CHANNEL_NAME = 100;
    private static final String FALLBACK_NAME = "lifecycle-room";

    private final DiscordGateway gateway;

    public CreateChannelAction(DiscordGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (gateway == null || !gateway.isConnected()) {
            return CHANNEL_CREATE_FAILED;
        }
        String guildId = getString(bind, "guildId");
        if (guildId == null || guildId.isBlank()) {
            guildId = getString(state, "guildId");
        }
        if ((guildId == null || guildId.isBlank()) && event != null) {
            String sourceId = event.getSourceId();
            if (sourceId != null && sourceId.startsWith("discord:")) {
                guildId = sourceId.substring("discord:".length()).trim();
                if (guildId != null && guildId.isBlank()) guildId = null;
            }
        }
        if (guildId == null || guildId.isBlank()) {
            return CHANNEL_CREATE_FAILED;
        }
        String channelName = getString(bind, "channelName");
        if (channelName == null || channelName.isBlank()) {
            channelName = getString(state, "channelName");
        }
        if (channelName == null || channelName.isBlank()) {
            channelName = buildChannelNameFromState(state);
        }
        channelName = normalizeChannelName(channelName);
        try {
            String channelId = gateway.createTextChannel(guildId, channelName);
            return channelId != null ? channelId : CHANNEL_CREATE_FAILED;
        } catch (Exception e) {
            return CHANNEL_CREATE_FAILED;
        }
    }

    /**
     * Normalizes a channel name to Discord-safe lowercase (a-z, 0-9, hyphen, underscore).
     */
    static String normalizeChannelName(String name) {
        if (name == null || name.isBlank()) return FALLBACK_NAME;
        String normalized = name.trim()
                .replaceAll("[^a-zA-Z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) return FALLBACK_NAME;
        if (normalized.length() > MAX_DISCORD_CHANNEL_NAME) {
            normalized = normalized.substring(0, MAX_DISCORD_CHANNEL_NAME);
        }
        return normalized;
    }

    /**
     * Builds a Discord-valid channel name from state project and codeChange:
     * repo segment (owner/repo or slug) + slug from codeChange + short uniqueness suffix.
     */
    static String buildChannelNameFromState(Map<String, Object> state) {
        String project = getString(state, "project");
        String codeChange = getString(state, "codeChange");
        String repoSegment = repoSegmentFromProject(project);
        String slug = slugFromCodeChange(codeChange);
        String suffix = Long.toHexString(System.currentTimeMillis()).toLowerCase();
        if (suffix.length() > 8) suffix = suffix.substring(suffix.length() - 8);
        String name = (repoSegment + "-" + slug + "-" + suffix)
                .replaceAll("[^a-zA-Z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(java.util.Locale.ROOT);
        if (name.isBlank()) name = FALLBACK_NAME;
        if (name.length() > MAX_DISCORD_CHANNEL_NAME) {
            name = name.substring(0, MAX_DISCORD_CHANNEL_NAME);
        }
        return name;
    }

    private static String repoSegmentFromProject(String project) {
        if (project == null || project.isBlank()) return "repo";
        String t = project.trim();
        if (t.startsWith("https://github.com/")) {
            String path = t.substring("https://github.com/".length());
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            if (!path.isBlank()) return path.replace("/", "-").replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase(java.util.Locale.ROOT);
        }
        if (t.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            return t.replace("/", "-").replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase(java.util.Locale.ROOT);
        }
        return t.replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase(java.util.Locale.ROOT);
    }

    private static String slugFromCodeChange(String codeChange) {
        if (codeChange == null || codeChange.isBlank()) return "change";
        String s = codeChange.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (s.length() > 32) s = s.substring(0, 32);
        return s.isBlank() ? "change" : s;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
