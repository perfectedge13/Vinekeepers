package com.vinekeepers.state.planning;

import java.util.List;
import java.util.Objects;

/**
 * One deterministic critique finding against the planning package (Phase C).
 */
public final class PlanCritiqueFinding {

    private final String id;
    private final String category;
    private final String severity;
    private final String code;
    private final String message;
    private final String ref;
    private final boolean blocksApproval;
    private final List<String> relatedFieldKeys;

    public PlanCritiqueFinding(
            String id,
            String category,
            String severity,
            String code,
            String message,
            String ref) {
        this(
                id,
                category,
                severity,
                code,
                message,
                ref,
                defaultBlocksApproval(severity),
                List.of());
    }

    public PlanCritiqueFinding(
            String id,
            String category,
            String severity,
            String code,
            String message,
            String ref,
            boolean blocksApproval,
            List<String> relatedFieldKeys) {
        this.id = Objects.requireNonNull(id, "id");
        this.category = category != null ? category : "";
        this.severity = severity != null ? severity : "INFO";
        this.code = code != null ? code : "";
        this.message = message != null ? message : "";
        this.ref = ref != null ? ref : "";
        this.blocksApproval = blocksApproval;
        this.relatedFieldKeys = relatedFieldKeys != null ? List.copyOf(relatedFieldKeys) : List.of();
    }

    public static boolean defaultBlocksApproval(String severity) {
        if (severity == null) {
            return false;
        }
        String s = severity.trim().toUpperCase();
        return "MUST_FIX".equals(s) || "BLOCKER".equals(s);
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getRef() {
        return ref;
    }

    public boolean isBlocksApproval() {
        return blocksApproval;
    }

    public List<String> getRelatedFieldKeys() {
        return relatedFieldKeys;
    }
}
