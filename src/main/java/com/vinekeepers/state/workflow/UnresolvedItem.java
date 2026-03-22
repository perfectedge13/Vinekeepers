package com.vinekeepers.state.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generic unresolved clarification/decision item (config-driven workflows; not planning-specific).
 */
public final class UnresolvedItem {

    private final String id;
    private final String fingerprint;
    private final UnresolvedItemStatus status;
    private final String promptTemplateRef;
    private final String questionText;
    private final String severity;
    private final Map<String, String> source;
    private final List<Map<String, String>> answers;
    private final List<String> affectedPaths;
    private final int repeatCount;

    public UnresolvedItem(
            String id,
            String fingerprint,
            UnresolvedItemStatus status,
            String promptTemplateRef,
            String questionText,
            String severity,
            Map<String, String> source,
            List<Map<String, String>> answers,
            List<String> affectedPaths) {
        this(id, fingerprint, status, promptTemplateRef, questionText, severity, source, answers, affectedPaths, 0);
    }

    public UnresolvedItem(
            String id,
            String fingerprint,
            UnresolvedItemStatus status,
            String promptTemplateRef,
            String questionText,
            String severity,
            Map<String, String> source,
            List<Map<String, String>> answers,
            List<String> affectedPaths,
            int repeatCount) {
        this.id = id != null ? id : "";
        this.fingerprint = fingerprint != null ? fingerprint : "";
        this.status = status != null ? status : UnresolvedItemStatus.OPEN;
        this.promptTemplateRef = promptTemplateRef != null ? promptTemplateRef : "";
        this.questionText = questionText != null ? questionText : "";
        this.severity = severity != null ? severity : "";
        this.source = source != null ? Map.copyOf(source) : Map.of();
        this.answers = answers != null ? List.copyOf(answers) : List.of();
        this.affectedPaths = affectedPaths != null ? List.copyOf(affectedPaths) : List.of();
        this.repeatCount = Math.max(0, repeatCount);
    }

    public String getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public UnresolvedItemStatus getStatus() {
        return status;
    }

    public String getPromptTemplateRef() {
        return promptTemplateRef;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getSeverity() {
        return severity;
    }

    public Map<String, String> getSource() {
        return source;
    }

    public List<Map<String, String>> getAnswers() {
        return answers;
    }

    public List<String> getAffectedPaths() {
        return affectedPaths;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public UnresolvedItem withStatus(UnresolvedItemStatus newStatus) {
        return new UnresolvedItem(
                id,
                fingerprint,
                newStatus,
                promptTemplateRef,
                questionText,
                severity,
                source,
                answers,
                affectedPaths,
                repeatCount);
    }

    public UnresolvedItem withIncrementRepeatCount() {
        return new UnresolvedItem(
                id,
                fingerprint,
                status,
                promptTemplateRef,
                questionText,
                severity,
                source,
                answers,
                affectedPaths,
                repeatCount + 1);
    }

    public UnresolvedItem withAppendedAnswer(String raw, String normalized) {
        List<Map<String, String>> next = new ArrayList<>(answers);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("raw", raw != null ? raw : "");
        row.put("normalized", normalized != null ? normalized : "");
        row.put("at", String.valueOf(System.currentTimeMillis()));
        next.add(Map.copyOf(row));
        return new UnresolvedItem(
                id,
                fingerprint,
                status,
                promptTemplateRef,
                questionText,
                severity,
                source,
                next,
                affectedPaths,
                repeatCount);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("fingerprint", fingerprint);
        m.put("status", status.name());
        m.put("promptTemplateRef", promptTemplateRef);
        m.put("questionText", questionText);
        m.put("severity", severity);
        m.put("source", new LinkedHashMap<>(source));
        m.put("answers", new ArrayList<>(answers));
        m.put("affectedPaths", new ArrayList<>(affectedPaths));
        m.put("repeatCount", repeatCount);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static UnresolvedItem fromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        String id = Objects.toString(m.get("id"), "");
        String fp = Objects.toString(m.get("fingerprint"), "");
        UnresolvedItemStatus st = UnresolvedItemStatus.OPEN;
        try {
            if (m.get("status") != null) {
                st = UnresolvedItemStatus.valueOf(m.get("status").toString().trim().toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {
            st = UnresolvedItemStatus.OPEN;
        }
        String ptr = Objects.toString(m.get("promptTemplateRef"), "");
        String q = Objects.toString(m.get("questionText"), "");
        String sev = Objects.toString(m.get("severity"), "");
        Map<String, String> src = new LinkedHashMap<>();
        if (m.get("source") instanceof Map<?, ?> sm) {
            for (Map.Entry<?, ?> e : sm.entrySet()) {
                if (e.getKey() != null) {
                    src.put(e.getKey().toString(), e.getValue() != null ? e.getValue().toString() : "");
                }
            }
        }
        List<Map<String, String>> ans = new ArrayList<>();
        if (m.get("answers") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> am) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : am.entrySet()) {
                        if (e.getKey() != null) {
                            row.put(e.getKey().toString(), e.getValue() != null ? e.getValue().toString() : "");
                        }
                    }
                    ans.add(row);
                }
            }
        }
        List<String> paths = new ArrayList<>();
        if (m.get("affectedPaths") instanceof List<?> pl) {
            for (Object o : pl) {
                if (o != null) {
                    paths.add(o.toString());
                }
            }
        }
        int repeat = 0;
        Object rc = m.get("repeatCount");
        if (rc != null) {
            try {
                repeat = Integer.parseInt(rc.toString().trim());
            } catch (NumberFormatException ignored) {
                repeat = 0;
            }
        }
        return new UnresolvedItem(id, fp, st, ptr, q, sev, src, ans, paths, repeat);
    }
}
