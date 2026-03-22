package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.actions.UpsertArtifactSectionDataAction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared JSON extraction and artifact upsert application for planning LLM passes.
 */
public final class PlanningLlmJsonSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PlanningLlmJsonSupport() {}

    public static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end > 0) {
                t = t.substring(0, end).trim();
            }
        }
        int start = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (start >= 0 && last > start) {
            return t.substring(start, last + 1);
        }
        return t;
    }

    public static int applyUpserts(
            JsonNode root,
            Event event,
            Map<String, Object> state,
            String contextId,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        JsonNode upsertsNode = root.path("upserts");
        if (!upsertsNode.isArray()) {
            return 0;
        }
        UpsertArtifactSectionDataAction upsertAction = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
        Map<String, Object> base = new LinkedHashMap<>();
        if (state != null) {
            base.putAll(state);
        }
        base.put("contextId", contextId);
        int applied = 0;
        for (JsonNode n : upsertsNode) {
            if (!n.isObject()) {
                continue;
            }
            Map<String, Object> u;
            try {
                u = JSON.convertValue(n, new TypeReference<>() {});
            } catch (IllegalArgumentException e) {
                continue;
            }
            Object aid = u.get("artifactId");
            Object sid = u.get("sectionId");
            if (aid == null || sid == null) {
                continue;
            }
            Object dataObj = u.get("data");
            if (!(dataObj instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            String mode = u.get("mode") != null ? u.get("mode").toString() : "replace";
            Object res = upsertAction.run(
                    event,
                    base,
                    Map.of("artifactId", aid.toString(), "sectionId", sid.toString(), "mode", mode, "data", dataMap));
            if ("OK".equals(res)) {
                applied++;
            }
        }
        return applied;
    }

    public static List<String> readFollowUpQuestions(JsonNode root) {
        List<String> followUps = new ArrayList<>();
        JsonNode fq = root.path("follow_up_questions");
        if (fq.isArray()) {
            for (JsonNode n : fq) {
                if (n.isTextual()) {
                    String q = n.asText().trim();
                    if (!q.isBlank()) {
                        followUps.add(q);
                    }
                }
            }
        }
        return followUps;
    }
}
