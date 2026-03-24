package com.vinekeepers.state.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable canonical decision for routing and UX projections after post-draft or post-critique normalization.
 */
public record PlanningCanonicalDecision(
        int version,
        String decisionId,
        String source,
        String normalizedAt,
        PlanningIntakeStage stage,
        PlanningCanonicalNextAction nextAction,
        PlanningInteractionState interactionState,
        String repoGroundingState,
        String confidenceSummary,
        boolean packetPostingAllowed,
        boolean reviewAllowed,
        boolean approvalAllowed,
        String topUnresolvedGap,
        String topUnresolvedGapId,
        String questionText,
        String blockingReason,
        List<String> explicitAssumptions,
        String materialStateChangeFingerprint) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int CURRENT_VERSION = 1;

    public PlanningCanonicalDecision {
        decisionId = blankToEmpty(decisionId);
        source = blankToEmpty(source);
        normalizedAt = blankToEmpty(normalizedAt);
        stage = stage != null ? stage : PlanningIntakeStage.GATHERING_CONTEXT;
        nextAction = nextAction != null ? nextAction : PlanningCanonicalNextAction.BLOCK;
        interactionState = interactionState != null ? interactionState : PlanningInteractionState.NONE;
        repoGroundingState = blankToDefault(repoGroundingState, "NOT_MATERIALIZED");
        confidenceSummary = blankToEmpty(confidenceSummary);
        topUnresolvedGap = blankToEmpty(topUnresolvedGap);
        topUnresolvedGapId = blankToEmpty(topUnresolvedGapId);
        questionText = blankToEmpty(questionText);
        blockingReason = blankToEmpty(blockingReason);
        explicitAssumptions = explicitAssumptions != null ? List.copyOf(explicitAssumptions) : List.of();
        materialStateChangeFingerprint = blankToEmpty(materialStateChangeFingerprint);
    }

    public static PlanningCanonicalDecision create(
            String source,
            PlanningIntakeStage stage,
            PlanningCanonicalNextAction nextAction,
            PlanningInteractionState interactionState,
            String repoGroundingState,
            String confidenceSummary,
            boolean packetPostingAllowed,
            boolean reviewAllowed,
            boolean approvalAllowed,
            String topUnresolvedGap,
            String topUnresolvedGapId,
            String questionText,
            String blockingReason,
            List<String> explicitAssumptions,
            String materialStateChangeFingerprint) {
        return new PlanningCanonicalDecision(
                CURRENT_VERSION,
                "pcd-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                source,
                Instant.now().toString(),
                stage,
                nextAction,
                interactionState,
                repoGroundingState,
                confidenceSummary,
                packetPostingAllowed,
                reviewAllowed,
                approvalAllowed,
                topUnresolvedGap,
                topUnresolvedGapId,
                questionText,
                blockingReason,
                explicitAssumptions,
                materialStateChangeFingerprint);
    }

    public String toJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", version);
        root.put("decisionId", decisionId);
        root.put("source", source);
        root.put("normalizedAt", normalizedAt);
        root.put("stage", stage.name());
        root.put("nextAction", nextAction.name());
        root.put("interactionState", interactionState.name());
        root.put("repoGroundingState", repoGroundingState);
        root.put("confidenceSummary", confidenceSummary);
        root.put("packetPostingAllowed", packetPostingAllowed);
        root.put("reviewAllowed", reviewAllowed);
        root.put("approvalAllowed", approvalAllowed);
        root.put("topUnresolvedGap", topUnresolvedGap);
        root.put("topUnresolvedGapId", topUnresolvedGapId);
        root.put("questionText", questionText);
        root.put("blockingReason", blockingReason);
        root.put("materialStateChangeFingerprint", materialStateChangeFingerprint);
        ArrayNode assumptions = root.putArray("explicitAssumptions");
        for (String assumption : explicitAssumptions) {
            if (assumption != null && !assumption.isBlank()) {
                assumptions.add(assumption.trim());
            }
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static PlanningCanonicalDecision fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        try {
            JsonNode n = JSON.readTree(raw);
            List<String> assumptions = new ArrayList<>();
            JsonNode arr = n.path("explicitAssumptions");
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    if (item != null && item.isTextual() && !item.asText("").isBlank()) {
                        assumptions.add(item.asText("").trim());
                    }
                }
            }
            return new PlanningCanonicalDecision(
                    n.path("version").asInt(CURRENT_VERSION),
                    n.path("decisionId").asText(""),
                    n.path("source").asText(""),
                    n.path("normalizedAt").asText(""),
                    parseStage(n.path("stage").asText("")),
                    parseNextAction(n.path("nextAction").asText("")),
                    parseInteractionState(n.path("interactionState").asText("")),
                    n.path("repoGroundingState").asText("NOT_MATERIALIZED"),
                    n.path("confidenceSummary").asText(""),
                    n.path("packetPostingAllowed").asBoolean(false),
                    n.path("reviewAllowed").asBoolean(false),
                    n.path("approvalAllowed").asBoolean(false),
                    n.path("topUnresolvedGap").asText(""),
                    n.path("topUnresolvedGapId").asText(""),
                    n.path("questionText").asText(""),
                    n.path("blockingReason").asText(""),
                    assumptions,
                    n.path("materialStateChangeFingerprint").asText(""));
        } catch (Exception e) {
            return empty();
        }
    }

    public static PlanningCanonicalDecision empty() {
        return new PlanningCanonicalDecision(
                CURRENT_VERSION,
                "",
                "",
                "",
                PlanningIntakeStage.GATHERING_CONTEXT,
                PlanningCanonicalNextAction.BLOCK,
                PlanningInteractionState.NONE,
                "NOT_MATERIALIZED",
                "",
                false,
                false,
                false,
                "",
                "",
                "",
                "",
                List.of(),
                "");
    }

    private static PlanningIntakeStage parseStage(String raw) {
        try {
            return PlanningIntakeStage.valueOf(blankToDefault(raw, PlanningIntakeStage.GATHERING_CONTEXT.name()).trim());
        } catch (Exception e) {
            return PlanningIntakeStage.GATHERING_CONTEXT;
        }
    }

    private static PlanningCanonicalNextAction parseNextAction(String raw) {
        try {
            return PlanningCanonicalNextAction.valueOf(blankToDefault(raw, PlanningCanonicalNextAction.BLOCK.name()).trim());
        } catch (Exception e) {
            return PlanningCanonicalNextAction.BLOCK;
        }
    }

    private static PlanningInteractionState parseInteractionState(String raw) {
        try {
            return PlanningInteractionState.valueOf(blankToDefault(raw, PlanningInteractionState.NONE.name()).trim());
        } catch (Exception e) {
            return PlanningInteractionState.NONE;
        }
    }

    private static String blankToDefault(String value, String dflt) {
        return value == null || value.isBlank() ? dflt : value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
