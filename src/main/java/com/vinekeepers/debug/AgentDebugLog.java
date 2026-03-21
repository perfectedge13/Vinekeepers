package com.vinekeepers.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Append-only NDJSON for debug sessions (Cursor agent). Do not log secrets.
 */
public final class AgentDebugLog {

    private AgentDebugLog() {
    }

    // #region agent log
    public static void log(String hypothesisId, String location, String message, Map<String, Object> data) {
        long ts = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"timestamp\":").append(ts);
        sb.append(",\"hypothesisId\":\"").append(esc(hypothesisId)).append("\"");
        sb.append(",\"location\":\"").append(esc(location)).append("\"");
        sb.append(",\"message\":\"").append(esc(message)).append("\"");
        if (data != null && !data.isEmpty()) {
            sb.append(",\"data\":{");
            boolean first = true;
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("\"").append(esc(String.valueOf(e.getKey()))).append("\":");
                Object v = e.getValue();
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof Boolean b) {
                    sb.append(b);
                } else if (v instanceof Number n) {
                    sb.append(n);
                } else {
                    sb.append("\"").append(esc(String.valueOf(v))).append("\"");
                }
            }
            sb.append("}");
        }
        sb.append("}\n");
        try {
            Path p = Path.of(System.getProperty("user.dir"), ".cursor", "debug.log");
            Files.createDirectories(p.getParent());
            Files.writeString(p, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // fail silent
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }
    // #endregion
}
