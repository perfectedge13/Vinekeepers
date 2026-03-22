package com.vinekeepers.devops;

/**
 * How host Docker Compose operations are executed for a {@link DeployTarget}.
 */
public enum ComposeHostExecutor {
    DIRECT,
    CURSOR_AGENT;

    public static ComposeHostExecutor fromYaml(Object raw) {
        if (raw == null) {
            return DIRECT;
        }
        String t = raw.toString().trim().toLowerCase();
        if ("cursor_agent".equals(t) || "cursor".equals(t)) {
            return CURSOR_AGENT;
        }
        return DIRECT;
    }
}
