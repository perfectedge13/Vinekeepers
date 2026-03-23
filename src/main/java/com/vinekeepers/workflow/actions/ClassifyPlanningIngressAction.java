package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.v2.GraphWorkflowRunner;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes planning ingress into explicit workflow-owned modes so v2 phases branch on intent instead of
 * legacy branch positions.
 */
public final class ClassifyPlanningIngressAction implements com.vinekeepers.workflow.WorkflowAction {

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        String existing = firstNonBlank(getString(bind, "planningIngressMode"), getString(state, "planningIngressMode"));
        String eventKind = event != null ? firstNonBlank(event.getKind(), "") : "";
        String activeCapability = firstNonBlank(getString(state, GraphWorkflowRunner.ACTIVE_STEPS_CAPABILITY_KEY), "");
        String waitingForField = firstNonBlank(getString(bind, "waitingForField"), getString(state, "waitingForField"));
        String customId =
                event != null && event.getPayload() != null ? firstNonBlank(getString(event.getPayload(), "customId"), "") : "";
        String canonicalStage = firstNonBlank(getString(state, "canonicalPlanningIntakeStage"), "");

        String normalized = normalizeExplicit(existing);
        if (normalized.isBlank()) {
            if ("interaction".equalsIgnoreCase(eventKind)
                    && isApprovalInteraction(activeCapability, waitingForField, customId, canonicalStage)) {
                normalized = "APPROVAL_INTERACTION";
            } else if ("interaction".equalsIgnoreCase(eventKind)) {
                normalized = "USER_INTERACTION";
            } else if ("true".equalsIgnoreCase(String.valueOf(state != null ? state.get("planningIntakeThread") : null))) {
                normalized = "USER_MESSAGE";
            } else {
                normalized = "UNKNOWN";
            }
        }

        spread.put("planningIngressMode", normalized);
        spread.put("planningEventKind", eventKind);
        spread.put("planningActiveCapabilityId", activeCapability);
        spread.put("planningAwaitingField", waitingForField != null ? waitingForField : "");
        return spread;
    }

    private static boolean isApprovalInteraction(
            String activeCapability, String waitingForField, String customId, String canonicalStage) {
        if ("cap_planning_approval".equals(activeCapability) || "cap_planning_human_review".equals(activeCapability)) {
            return true;
        }
        if ("planApprovalDecision".equals(waitingForField) || "readinessProceedRaw".equals(waitingForField)) {
            return true;
        }
        String normalizedCustomId = customId != null ? customId.trim().toLowerCase(Locale.ROOT) : "";
        if (normalizedCustomId.equals("approve")
                || normalizedCustomId.equals("approve_with_risks")
                || normalizedCustomId.equals("revise")
                || normalizedCustomId.equals("reject")
                || normalizedCustomId.equals("proceed")) {
            return true;
        }
        return "AWAITING_APPROVAL".equalsIgnoreCase(canonicalStage)
                || "READINESS_GATE".equalsIgnoreCase(canonicalStage);
    }

    private static String normalizeExplicit(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SYNTHETIC_KICKOFF", "USER_MESSAGE", "APPROVAL_INTERACTION", "USER_INTERACTION" -> normalized;
            default -> "";
        };
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
