package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps work-profile metadata to plain-English prompts (avoids exposing internal artifact/field ids to users).
 */
public final class PlanningPromptFormatter {

    private PlanningPromptFormatter() {
    }

    /**
     * One or two sentences asking for a required field, using label and optional hint.
     */
    public static String requiredFieldPrompt(
            ArtifactDefinition artifact,
            SectionDefinition section,
            FieldDefinition field,
            int repeatableRowIndex) {
        String label = field.getLabel() != null && !field.getLabel().isBlank()
                ? field.getLabel()
                : "this item";
        String sectionTitle = section.getTitle() != null && !section.getTitle().isBlank()
                ? section.getTitle()
                : section.getSectionId();
        String artTitle = artifact.getTitle() != null && !artifact.getTitle().isBlank()
                ? artifact.getTitle()
                : artifact.getArtifactId();
        String rowNote = repeatableRowIndex >= 0 ? " (entry " + (repeatableRowIndex + 1) + ")" : "";
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(label).append("**").append(rowNote);
        sb.append(" under _").append(sectionTitle).append("_ (").append(artTitle).append(") is still empty.");
        if (field.getPromptHint() != null && !field.getPromptHint().isBlank()) {
            sb.append(" ").append(field.getPromptHint());
        }
        sb.append(" Reply in one message with what we should record.");
        return sb.toString();
    }

    public static String repeatableSectionEmptyPrompt(ArtifactDefinition artifact, SectionDefinition section) {
        String sectionTitle = section.getTitle() != null && !section.getTitle().isBlank()
                ? section.getTitle()
                : section.getSectionId();
        String artTitle = artifact.getTitle() != null && !artifact.getTitle().isBlank()
                ? artifact.getTitle()
                : artifact.getArtifactId();
        return "The _" + sectionTitle + "_ section (" + artTitle + ") needs at least one entry. "
                + "Reply with the first item to add.";
    }

    public static String requiredSectionMissingPrompt(ArtifactDefinition artifact, SectionDefinition section) {
        String sectionTitle = section.getTitle() != null && !section.getTitle().isBlank()
                ? section.getTitle()
                : section.getSectionId();
        String artTitle = artifact.getTitle() != null && !artifact.getTitle().isBlank()
                ? artifact.getTitle()
                : artifact.getArtifactId();
        return "The _" + sectionTitle + "_ block (" + artTitle + ") is missing. "
                + "Reply with the main content we should add for that section.";
    }

    /**
     * Human-readable missing-required summary for coordinator/scribe lines (not raw paths).
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
                    if (hp.length >= 3 && profile != null) {
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
}
