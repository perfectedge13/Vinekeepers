package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.state.planning.PlanningProposal;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON helpers for {@link PlanningProposal} lists in workflow state.
 */
public final class PlanningProposalJson {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<PlanningProposal>> LIST_TYPE = new TypeReference<>() {};

    private PlanningProposalJson() {
    }

    public static String toJson(List<PlanningProposal> proposals) throws JsonProcessingException {
        return JSON.writeValueAsString(proposals != null ? proposals : List.of());
    }

    public static List<PlanningProposal> parseList(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.readValue(json, LIST_TYPE);
    }

    public static List<String> parseStringList(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return JSON.readValue(json, new TypeReference<>() {});
    }

    public static String stringListToJson(List<String> ids) throws JsonProcessingException {
        return JSON.writeValueAsString(ids != null ? ids : List.of());
    }
}
