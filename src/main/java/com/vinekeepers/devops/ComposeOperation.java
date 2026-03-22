package com.vinekeepers.devops;

import java.util.Locale;

public enum ComposeOperation {
    UP,
    STOP,
    RESTART,
    PS;

    public static ComposeOperation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UP;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "down", "stop" -> STOP;
            case "restart" -> RESTART;
            case "ps", "status" -> PS;
            default -> UP;
        };
    }
}
