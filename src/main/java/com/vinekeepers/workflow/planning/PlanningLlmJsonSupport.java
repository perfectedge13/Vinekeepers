package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiCallContext;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.actions.UpsertArtifactSectionDataAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared JSON extraction and artifact upsert application for planning LLM passes.
 */
public final class PlanningLlmJsonSupport {

    private static final Logger log = LoggerFactory.getLogger(PlanningLlmJsonSupport.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int REPAIR_USER_SNIPPET_MAX = 14_000;
    private static final String JSON_REPAIR_SYSTEM =
            "You fix JSON. Reply with a single valid JSON object only: no markdown fences, no explanation. "
                    + "Arrays must use [ ]. Objects must use { }. No trailing commas.";

    public record UpsertApplyResult(int attempted, int applied, List<String> rejected) {}

    private PlanningLlmJsonSupport() {}

    public static String extractJsonObject(String raw) {
        String t = stripMarkdownFence(raw);
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (inString) {
                if (ch == '\\') {
                    escaping = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                if (start < 0) {
                    start = i;
                }
                depth++;
                continue;
            }
            if (ch == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    return t.substring(start, i + 1);
                }
            }
        }
        return t;
    }

    public static JsonNode parseJsonObject(String raw) throws Exception {
        String cleaned = stripMarkdownFence(raw);
        try {
            return JSON.readTree(cleaned);
        } catch (Exception fullParseError) {
            String extracted = extractJsonObject(cleaned);
            if (extracted.equals(cleaned)) {
                throw fullParseError;
            }
            return JSON.readTree(extracted);
        }
    }

    public static String tryRepairJson(
            OpenAiChatClient client, String rawAssistant, String roleNameForPayload, Event event, Map<String, Object> state) {
        if (client == null || !client.isConfigured()) {
            return null;
        }
        String snippet = rawAssistant != null && rawAssistant.length() > REPAIR_USER_SNIPPET_MAX
                ? rawAssistant.substring(0, REPAIR_USER_SNIPPET_MAX) + "…"
                : rawAssistant;
        String user =
                "The following text was meant to be one JSON object but is invalid. "
                        + "Return only corrected JSON (same keys/shape intent: upserts array, follow_up_questions array).\n\n"
                        + snippet;
        String out;
        try {
            OpenAiCallContext repairCtx =
                    OpenAiCallContext.planning(event, state, jsonRepairActivityLine(roleNameForPayload));
            out = client.complete(JSON_REPAIR_SYSTEM, user, "gpt-4o-mini", null, repairCtx);
        } catch (Exception e) {
            log.debug("{} JSON repair call failed: {}", roleNameForPayload, e.getMessage());
            return null;
        }
        if (out == null || out.startsWith("ERROR:")) {
            return null;
        }
        return out;
    }

    public static int applyUpserts(
            JsonNode root,
            Event event,
            Map<String, Object> state,
            String contextId,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        return applyUpsertsDetailed(root, event, state, contextId, planStateStore, workProfileRegistry).applied();
    }

    public static UpsertApplyResult applyUpsertsDetailed(
            JsonNode root,
            Event event,
            Map<String, Object> state,
            String contextId,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        JsonNode upsertsNode = root.path("upserts");
        if (!upsertsNode.isArray()) {
            return new UpsertApplyResult(0, 0, List.of());
        }
        UpsertArtifactSectionDataAction upsertAction = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
        WorkProfileDefinition profile = resolveProfile(planStateStore, workProfileRegistry, contextId);
        Map<String, Object> base = new LinkedHashMap<>();
        if (state != null) {
            base.putAll(state);
        }
        base.put("contextId", contextId);
        int attempted = 0;
        int applied = 0;
        List<String> rejected = new ArrayList<>();
        for (JsonNode n : upsertsNode) {
            if (!n.isObject()) {
                continue;
            }
            attempted++;
            Map<String, Object> u;
            try {
                u = JSON.convertValue(n, new TypeReference<>() {});
            } catch (IllegalArgumentException e) {
                rejected.add("An upsert entry could not be decoded.");
                continue;
            }
            Object aid = u.get("artifactId");
            Object sid = u.get("sectionId");
            if (aid == null || sid == null) {
                rejected.add("An upsert entry is missing artifactId or sectionId.");
                continue;
            }
            Object dataObj = u.get("data");
            if (!(dataObj instanceof Map<?, ?>)) {
                rejected.add("Upsert " + aid + "/" + sid + " is missing an object data payload.");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            String validationError = validateDataKeys(profile, aid.toString(), sid.toString(), dataMap);
            if (validationError != null) {
                rejected.add(validationError);
                continue;
            }
            String mode = u.get("mode") != null ? u.get("mode").toString() : "replace";
            Object res = upsertAction.run(
                    event,
                    base,
                    Map.of("artifactId", aid.toString(), "sectionId", sid.toString(), "mode", mode, "data", dataMap));
            if ("OK".equals(res)) {
                applied++;
            } else if (res != null && !res.toString().isBlank()) {
                rejected.add(res.toString());
            }
        }
        return new UpsertApplyResult(attempted, applied, List.copyOf(rejected));
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

    private static String stripMarkdownFence(String raw) {
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
        return t;
    }

    private static String jsonRepairActivityLine(String roleNameForPayload) {
        String name = roleNameForPayload != null && !roleNameForPayload.isBlank()
                ? roleNameForPayload.trim()
                : "planning";
        return "Fixing " + name + " JSON output so we can apply updates (asking ChatGPT).";
    }

    public static String summarizeRejectedUpserts(UpsertApplyResult result) {
        if (result == null || result.rejected() == null || result.rejected().isEmpty()) {
            return "Structured planning upserts did not match the active work profile.";
        }
        String first = result.rejected().get(0);
        if (first == null || first.isBlank()) {
            return "Structured planning upserts did not match the active work profile.";
        }
        return first;
    }

    private static WorkProfileDefinition resolveProfile(
            FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry, String contextId) {
        if (planStateStore == null || workProfileRegistry == null || contextId == null || contextId.isBlank()) {
            return null;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null || plan.getProfileId() == null || plan.getProfileId().isBlank()) {
            return null;
        }
        return workProfileRegistry.get(plan.getProfileId()).orElse(null);
    }

    private static String validateDataKeys(
            WorkProfileDefinition profile, String artifactId, String sectionId, Map<String, Object> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            return "Upsert " + artifactId + "/" + sectionId + " has no field data.";
        }
        if (profile == null) {
            return null;
        }
        SectionDefinition section = profile.findSection(artifactId, sectionId).orElse(null);
        if (section == null) {
            return "Upsert " + artifactId + "/" + sectionId + " does not match this planning profile.";
        }
        Set<String> allowed = new LinkedHashSet<>();
        section.getFields().forEach(fd -> allowed.add(fd.getFieldId()));
        List<String> unknown = new ArrayList<>();
        for (String key : dataMap.keySet()) {
            if (key == null || key.isBlank() || !allowed.contains(key)) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            if (unknown.contains("fieldId")) {
                return "Upsert " + artifactId + "/" + sectionId
                        + " used the literal key `fieldId`; use real field ids like " + String.join(", ", allowed) + ".";
            }
            return "Upsert " + artifactId + "/" + sectionId + " used unsupported field ids "
                    + unknown + "; allowed fields: " + allowed + ".";
        }
        return null;
    }
}
