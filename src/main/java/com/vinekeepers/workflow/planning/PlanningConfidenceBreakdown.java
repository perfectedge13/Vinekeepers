package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable factor snapshot for planning confidence (observability + persistence). No gap policy. */
public record PlanningConfidenceBreakdown(
        double repoEvidenceGroundingScore,
        double clarificationConfidenceScore,
        boolean depthOk,
        boolean structuredParseOk,
        int structuredKnownFactCount,
        int materialUnknownCount,
        String notes) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public String toJson() {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("repoEvidenceGroundingScore", repoEvidenceGroundingScore);
            m.put("clarificationConfidenceScore", clarificationConfidenceScore);
            m.put("depthOk", depthOk);
            m.put("structuredParseOk", structuredParseOk);
            m.put("structuredKnownFactCount", structuredKnownFactCount);
            m.put("materialUnknownCount", materialUnknownCount);
            m.put("notes", notes != null ? notes : "");
            return JSON.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public static PlanningConfidenceBreakdown fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return new PlanningConfidenceBreakdown(0, 0, false, false, 0, 0, "");
        }
        try {
            var n = JSON.readTree(raw.trim());
            return new PlanningConfidenceBreakdown(
                    n.path("repoEvidenceGroundingScore").asDouble(0),
                    n.path("clarificationConfidenceScore").asDouble(0),
                    n.path("depthOk").asBoolean(false),
                    n.path("structuredParseOk").asBoolean(false),
                    n.path("structuredKnownFactCount").asInt(0),
                    n.path("materialUnknownCount").asInt(0),
                    n.path("notes").asText(""));
        } catch (Exception e) {
            return new PlanningConfidenceBreakdown(0, 0, false, false, 0, 0, "");
        }
    }
}
