package com.vinekeepers.workflow.steps;

import com.vinekeepers.bot.NormalizedEventContext;
import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowTransforms;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Step that extracts event fields into workflow state. Source paths: payload.&lt;key&gt; (flat)
 * and context.&lt;field&gt; (NormalizedEventContext getters). List-valued context fields
 * are stored as List in state. Order per mapping: read → default if missing/blank → transforms → state.put.
 */
public final class ExtractEventFieldsStep implements WorkflowStep {

    private final List<Map<String, Object>> fromEvent;

    public ExtractEventFieldsStep(List<Map<String, Object>> fromEvent) {
        this.fromEvent = fromEvent != null ? List.copyOf(fromEvent) : List.of();
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        NormalizedEventContext ctx = event != null ? NormalizedEventContext.from(event) : null;
        Map<String, Object> payload = event != null && event.getPayload() != null ? event.getPayload() : Map.of();
        for (Map<String, Object> mapping : fromEvent) {
            String fromPath = (String) mapping.get("from");
            String storeIn = (String) mapping.get("storeIn");
            if (fromPath == null || fromPath.isBlank() || storeIn == null || storeIn.isBlank()) continue;
            Object raw = resolve(fromPath, payload, ctx);
            Object value = applyDefaultAndTransforms(mapping, raw);
            if (value != null) {
                state.put(storeIn, value);
            }
        }
        return StepResult.goTo(stepIndex + 1);
    }

    private Object resolve(String fromPath, Map<String, Object> payload, NormalizedEventContext ctx) {
        String path = fromPath.trim();
        if (path.startsWith("payload.")) {
            String key = path.substring("payload.".length()).trim();
            if (key.contains(".")) return null;
            return payload.get(key);
        }
        if (path.startsWith("context.")) {
            String field = path.substring("context.".length()).trim().toLowerCase(Locale.ROOT);
            if (ctx == null) return null;
            return getContextValue(ctx, field);
        }
        return null;
    }

    private Object getContextValue(NormalizedEventContext ctx, String field) {
        return switch (field) {
            case "channelid" -> ctx.getChannelId();
            case "threadid" -> ctx.getThreadId();
            case "conversationid" -> ctx.getConversationId();
            case "actorid" -> ctx.getActorId();
            case "actorusername" -> ctx.getActorUsername();
            case "text" -> ctx.getText();
            case "sourcetype" -> ctx.getSourceType();
            case "eventtype" -> ctx.getEventType();
            case "repo" -> ctx.getRepo();
            case "interactionid" -> ctx.getInteractionId();
            case "token" -> ctx.getToken();
            case "customid" -> ctx.getCustomId();
            case "mentions" -> ctx.getMentions();
            case "labels" -> ctx.getLabels();
            case "interactionvalues" -> ctx.getInteractionValues();
            default -> null;
        };
    }

    private Object applyDefaultAndTransforms(Map<String, Object> mapping, Object raw) {
        Object defaultObj = mapping.get("default");
        Object transformObj = mapping.get("transforms");
        List<String> transformNames = toTransformList(transformObj);
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) out.add(e.toString());
            }
            return out;
        }
        String str = raw != null ? Objects.toString(raw) : null;
        if (str == null || str.isBlank()) {
            if (defaultObj != null) str = Objects.toString(defaultObj);
            else return null;
        }
        String defaultStr = defaultObj != null ? Objects.toString(defaultObj) : "";
        return WorkflowTransforms.applyAll(str, transformNames, defaultStr);
    }

    private static List<String> toTransformList(Object transformObj) {
        if (transformObj == null) return List.of();
        if (transformObj instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) out.add(e.toString().trim());
            }
            return out;
        }
        return List.of(transformObj.toString().trim());
    }
}
