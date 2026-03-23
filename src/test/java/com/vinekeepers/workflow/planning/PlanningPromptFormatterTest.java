package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPromptFormatterTest {

    private static WorkProfileDefinition sampleProfile() {
        FieldDefinition field = new FieldDefinition(
                "decision_text",
                "Decision",
                "text",
                true,
                "ADR-style: decision, context, consequences.");
        SectionDefinition section = new SectionDefinition(
                "adr_entry", "ADR entry", true, true, List.of(field));
        ArtifactDefinition artifact = new ArtifactDefinition(
                "governance_decisions",
                "Governance decisions",
                List.of(),
                false,
                List.of(section));
        return new WorkProfileDefinition("p", "Profile", List.of(artifact));
    }

    @Test
    void requiredFieldPromptUsesLabelHintAndDefaultWhenNoHint() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition withHint = sec.getFields().get(0);
        assertTrue(PlanningPromptFormatter.requiredFieldPrompt(art, sec, withHint, -1)
                .contains("ADR-style"));

        FieldDefinition noHint = new FieldDefinition("x", "Scope", "text", true, "");
        assertTrue(PlanningPromptFormatter.requiredFieldPrompt(art, sec, noHint, -1)
                .contains("share concrete detail"));
    }

    @Test
    void requiredFieldPromptRepeatableRowMentionsRowNumber() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition f = sec.getFields().get(0);
        String line = PlanningPromptFormatter.requiredFieldPrompt(art, sec, f, 0);
        assertTrue(line.contains("item 1"));
    }

    @Test
    void repeatableSectionEmptyAndSectionMissingPromptsArePlainEnglish() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        assertTrue(PlanningPromptFormatter.repeatableSectionEmptyPrompt(art, sec)
                .contains("needs at least one entry"));
        assertTrue(PlanningPromptFormatter.requiredSectionMissingPrompt(art, sec)
                .contains("is missing"));
    }

    @Test
    void formatMissingRequiredSummaryEmptyAndResolvedPaths() {
        WorkProfileDefinition profile = sampleProfile();
        assertEquals(
                "All required profile fields present.",
                PlanningPromptFormatter.formatMissingRequiredSummary(profile, List.of()));

        String summary = PlanningPromptFormatter.formatMissingRequiredSummary(
                profile,
                List.of("governance_decisions.adr_entry.decision_text"));
        assertTrue(summary.startsWith("Still needed:"));
        assertTrue(summary.contains("Decision"));
        assertTrue(summary.contains("ADR-style"));

        String emptySec = PlanningPromptFormatter.formatMissingRequiredSummary(
                profile,
                List.of("governance_decisions.adr_entry (repeatable section empty)"));
        assertTrue(emptySec.contains("ADR entry"));
        assertTrue(emptySec.contains("first item"));

        String missingSec = PlanningPromptFormatter.formatMissingRequiredSummary(
                profile,
                List.of("governance_decisions.adr_entry (section missing)"));
        assertTrue(missingSec.contains("missing"));
    }
}
