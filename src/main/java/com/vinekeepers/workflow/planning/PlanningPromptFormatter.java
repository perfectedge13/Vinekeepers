package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps work-profile metadata to plain-English strings for discovery gap records and internal readiness summaries.
 * Live {@code arrietty_room_v2} user questions come only from canonical coordinator clarification ({@code planning_clarification}),
 * not from these field/section prompts; intake blocking uses workspace gaps only. Copy uses internal completeness
 * labels (avoid the word {@code completeness} so {@link com.vinekeepers.workflow.discovery.ClarificationPromptQualityGate}
 * can still vet strings when tests reuse the formatter).
 */
public final class PlanningPromptFormatter {

    private PlanningPromptFormatter() {
    }

    /**
     * One or two sentences for a required-field discovery gap record, using label and optional hint.
     *
     * @apiNote Internal/diagnostics/readiness only. Not used to drive live {@code planning_clarification}; Arrietty asks
     *     only via canonical coordinator gaps.
     */
    public static String requiredFieldPrompt(
            ArtifactDefinition artifact,
            SectionDefinition section,
            FieldDefinition field,
            int repeatableRowIndex) {
        String label = field.getLabel() != null && !field.getLabel().isBlank()
                ? field.getLabel()
                : "this item";
        StringBuilder sb = new StringBuilder();
        if (repeatableRowIndex >= 0) {
            sb.append("**Internal profile record:** Capture on the plan for **")
                    .append(label)
                    .append("** (row). For diagnostics/readiness only — include: ");
        } else {
            sb.append("**Internal profile record:** Capture **")
                    .append(label)
                    .append("** on the saved plan. ");
        }
        if (field.getPromptHint() != null && !field.getPromptHint().isBlank()) {
            sb.append(field.getPromptHint().trim());
        } else {
            sb.append("send the specific text or decision to store on the plan.");
        }
        return sb.toString();
    }

    /**
     * @apiNote Internal readiness/discovery gap copy only; not live coordinator clarification.
     */
    public static String repeatableSectionEmptyPrompt(ArtifactDefinition artifact, SectionDefinition section) {
        String sectionTitle = section.getTitle() != null && !section.getTitle().isBlank()
                ? section.getTitle()
                : titleCaseSnake(section.getSectionId());
        String artTitle = artifact.getTitle() != null && !artifact.getTitle().isBlank()
                ? artifact.getTitle()
                : titleCaseSnake(artifact.getArtifactId());
        return "**Internal profile record:** The _" + sectionTitle + "_ section (" + artTitle
                + ") needs at least one entry for readiness tracking.";
    }

    /**
     * @apiNote Internal readiness/discovery gap copy only; not live coordinator clarification.
     */
    public static String requiredSectionMissingPrompt(ArtifactDefinition artifact, SectionDefinition section) {
        String sectionTitle = section.getTitle() != null && !section.getTitle().isBlank()
                ? section.getTitle()
                : titleCaseSnake(section.getSectionId());
        String artTitle = artifact.getTitle() != null && !artifact.getTitle().isBlank()
                ? artifact.getTitle()
                : titleCaseSnake(artifact.getArtifactId());
        return "**Internal profile record:** The _" + sectionTitle + "_ block (" + artTitle
                + ") is missing for readiness tracking.";
    }

    /**
     * Human-readable missing-required summary for internal coordinator/scribe lines (not raw paths).
     *
     * @apiNote Readiness/diagnostics only; not a live planning clarification driver.
     */
    public static String formatMissingRequiredSummary(WorkProfileDefinition profile, List<String> internalPaths) {
        if (internalPaths == null || internalPaths.isEmpty()) {
            return "All required profile fields present.";
        }
        List<String> lines = new ArrayList<>();
        for (String path : internalPaths) {
            lines.add(internalPathToFriendlyLine(profile, path));
        }
        return "Still needed: " + String.join("; ", lines);
    }

    private static String internalPathToFriendlyLine(WorkProfileDefinition profile, String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.contains("(repeatable section empty)")) {
            String prefix = path.replace(" (repeatable section empty)", "").trim();
            String[] parts = prefix.split("\\.", 2);
            if (parts.length >= 2 && profile != null) {
                return profile.getArtifactsById().values().stream()
                        .filter(a -> a.getArtifactId().equals(parts[0]))
                        .findFirst()
                        .flatMap(a -> a.getSections().stream()
                                .filter(s -> s.getSectionId().equals(parts[1]))
                                .findFirst()
                                .map(s -> repeatableSectionEmptyPrompt(a, s)))
                        .orElse(path);
            }
            return path;
        }
        if (path.contains("(section missing)")) {
            String prefix = path.replace(" (section missing)", "").trim();
            String[] parts = prefix.split("\\.", 2);
            if (parts.length >= 2 && profile != null) {
                return profile.getArtifactsById().values().stream()
                        .filter(a -> a.getArtifactId().equals(parts[0]))
                        .findFirst()
                        .flatMap(a -> a.getSections().stream()
                                .filter(s -> s.getSectionId().equals(parts[1]))
                                .findFirst()
                                .map(s -> requiredSectionMissingPrompt(a, s)))
                        .orElse(path);
            }
            return path;
        }
        // artifact.section.field or artifact.section[i].field
        int bracket = path.indexOf('[');
        if (bracket > 0) {
            int close = path.indexOf(']', bracket);
            if (close > 0) {
                try {
                    int idx = Integer.parseInt(path.substring(bracket + 1, close).trim());
                    String head = path.substring(0, bracket);
                    String tail = path.length() > close + 1 ? path.substring(close + 2) : "";
                    String[] hp = head.split("\\.", 3);
                    if (hp.length >= 2 && profile != null) {
                        ArtifactDefinition art = profile.getArtifactsById().get(hp[0]);
                        SectionDefinition sec = findSection(art, hp[1]);
                        FieldDefinition fld = findField(sec, tail);
                        if (art != null && sec != null && fld != null) {
                            return requiredFieldPrompt(art, sec, fld, idx);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        String[] parts = path.split("\\.", 3);
        if (parts.length >= 3 && profile != null) {
            ArtifactDefinition art = profile.getArtifactsById().get(parts[0]);
            SectionDefinition sec = findSection(art, parts[1]);
            FieldDefinition fld = findField(sec, parts[2]);
            if (art != null && sec != null && fld != null) {
                return requiredFieldPrompt(art, sec, fld, -1);
            }
        }
        return path;
    }

    private static SectionDefinition findSection(ArtifactDefinition art, String sectionId) {
        if (art == null) {
            return null;
        }
        return art.getSections().stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .orElse(null);
    }

    private static FieldDefinition findField(SectionDefinition sec, String fieldId) {
        if (sec == null) {
            return null;
        }
        return sec.getFields().stream()
                .filter(f -> f.getFieldId().equals(fieldId))
                .findFirst()
                .orElse(null);
    }

    private static String titleCaseSnake(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p.substring(0, 1).toUpperCase(Locale.ROOT)).append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    public static String formatAskUser(PlanningDecisionSnapshot snapshot) {
        requireAction(snapshot, PlanningNextAction.ASK_USER);
        return snapshot.nextQuestion();
    }

    public static String formatBlocked(PlanningDecisionSnapshot snapshot) {
        requireAction(snapshot, PlanningNextAction.BLOCKED);
        String reason = snapshot.blockingReason();
        return reason.isBlank()
                ? "Planning cannot proceed on this pass. The packet is not being posted yet."
                : "Planning cannot proceed on this pass. The packet is not being posted yet. " + reason;
    }

    public static String formatPacketIntro(PlanningDecisionSnapshot snapshot) {
        requireAction(snapshot, PlanningNextAction.READY_FOR_PACKET);
        return "**Update:** I'm posting the planning packet now (may be several messages).";
    }

    private static void requireAction(PlanningDecisionSnapshot snapshot, PlanningNextAction expected) {
        PlanningNextAction actual = snapshot != null ? snapshot.nextAction() : null;
        if (actual != expected) {
            throw new IllegalArgumentException("PlanningDecisionSnapshot action must be " + expected + " but was " + actual);
        }
    }
}
