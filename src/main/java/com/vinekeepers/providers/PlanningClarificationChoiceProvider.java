package com.vinekeepers.providers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.DynamicChoiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code planningClarificationChoicesJson} from workflow state (written by {@code execute_planning_room_cycle}).
 */
public final class PlanningClarificationChoiceProvider implements DynamicChoiceProvider {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public List<ResponseIntent.Choice> getChoices(Event event, ConfigurableWorkflowState state) {
        if (state == null || state.getData() == null) {
            return List.of();
        }
        Object raw = state.get("planningClarificationChoicesJson");
        if (raw == null) {
            return List.of();
        }
        String text = raw.toString().trim();
        if (text.isBlank() || "[]".equals(text)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> maps = JSON.readValue(text, new TypeReference<>() {});
            List<ResponseIntent.Choice> out = new ArrayList<>();
            for (Map<String, Object> m : maps) {
                if (m == null) {
                    continue;
                }
                String id = m.get("id") != null ? m.get("id").toString() : "";
                String label = m.get("label") != null ? m.get("label").toString() : id;
                String desc = m.get("description") != null ? m.get("description").toString() : "";
                if (!id.isBlank()) {
                    out.add(new ResponseIntent.Choice(id, label, desc));
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
