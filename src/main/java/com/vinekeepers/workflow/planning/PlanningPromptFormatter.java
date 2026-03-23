package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps work-profile metadata to plain-English prompts (avoids exposing internal artifact/field ids to users).
 * Each prompt is a single concrete ask for a missing fact, aligned with the Arrietty clarification contract (no generic
 * completeness or meta “anything else?” style wording).
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
        if (isOpenQuestionsField(artifact, section, field)) {
            return "**Question:** Which unknowns still need confirmation before implementation? "
                    + "Put one item per line when possible. If nothing remains, reply with "
                    + "\"None — ready to implement\" so we can save it on the plan.";
        }
        String label = field.getLabel() != null && !field.getLabel().isBlank()
                ? field.getLabel()
                : "this item";
        StringBuilder sb = new StringBuilder();
        if (repeatableRowIndex >= 0) {
            sb.append("**Question:** What should we record for **")
                    .append(label)
                    .append(' ')
                    .append(repeatableRowIndex + 1)
                    .append("** in this list? Reply in **one message** so we can save it on the plan. Include: ");
        } else {
            sb.append("**Question:** What should we record for **")
                    .append(label)
                    .append("** on the plan? ");
        }
        if (field.getPromptHint() != null && !field.getPromptHint().isBlank()) {
            sb.append(field.getPromptHint().trim());
        } else {
            sb.append("send the specific text or decision to store on the plan.");
        }
        return sb.toString();
    }

    private static boolean isOpenQuestionsField(
            ArtifactDefinition artifact, SectionDefinition section, FieldDefinition field) {
        if (artifact == null || section == null || field == null) {
            return false;
        }
        return "open_questions_block".equals(artifact.getArtifactId())
                && "backlog".equals(section.getSectionId())
                && "open_questions".equals(field.getFieldId());
    }

    public static String repeatableSectionEmptyPrompt(ArtifactDefinition artifact, SectionDefinition section) {
        String sectionTitle = section.getTitle() != null && !section.getTitle().isBlank()
                ? section.getTitle()
                : titleCaseSnake(section.getSectionId());
        String artTitle = artifact.getTitle() != null && !artifact.getTitle().isBlank()
                ? artifact.getTitle()
                : titleCaseSnake(artifact.getArtifactId());
        return "**Question:** The _" + sectionTitle + "_ section (" + artTitle + ") needs at least one entry. "
                + "Reply with the first item to add so we can save it on the plan.";
    }

    public static String requiredSectionMissingPrompt(ArtifactDefinition artifact, SectionDefinition section) {
        String sectionTitle = section.getTitle() != null && !section.getTitle().isBlank()
                ? section.getTitle()
                : titleCaseSnake(section.getSectionId());
        String artTitle = artifact.getTitle() != null && !artifact.getTitle().isBlank()
                ? artifact.getTitle()
                : titleCaseSnake(artifact.getArtifactId());
        return "**Question:** The _" + sectionTitle + "_ block (" + artTitle + ") is missing. "
                + "Reply with the main content we should add for that section so we can save it on the plan.";
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
}
