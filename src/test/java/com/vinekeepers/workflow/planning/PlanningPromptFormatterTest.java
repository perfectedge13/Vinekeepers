package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.workflow.discovery.ClarificationPromptQualityGate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void requiredFieldPromptAvoidsGenericOpenQuestionsWording() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition f = sec.getFields().get(0);
        String p = PlanningPromptFormatter.requiredFieldPrompt(art, sec, f, -1);
        assertFalse(p.toLowerCase().contains("open questions"));
    }

    @Test
    void requiredFieldPromptNeverEmitsLegacyReadyToImplementRitual() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition f = sec.getFields().get(0);
        String prompt = PlanningPromptFormatter.requiredFieldPrompt(art, sec, f, -1);
        assertFalse(prompt.contains("None — ready to implement"));
        assertTrue(ClarificationPromptQualityGate.passes(prompt));
    }

    @Test
    void requiredFieldPromptUsesLabelHintAndDefaultWhenNoHint() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition withHint = sec.getFields().get(0);
        String withHintPrompt = PlanningPromptFormatter.requiredFieldPrompt(art, sec, withHint, -1);
        assertTrue(withHintPrompt.contains("Internal profile record"));
        assertTrue(withHintPrompt.contains("ADR-style"));
        assertTrue(withHintPrompt.contains("saved plan") || withHintPrompt.contains("on the plan"));

        FieldDefinition noHint = new FieldDefinition("x", "Scope", "text", true, "");
        String noHintPrompt = PlanningPromptFormatter.requiredFieldPrompt(art, sec, noHint, -1);
        assertTrue(noHintPrompt.contains("Internal profile record"));
        assertTrue(noHintPrompt.contains("send the specific text or decision to store on the plan"));
    }

    @Test
    void requiredFieldPromptRepeatableRowIsSelfContained() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition f = sec.getFields().get(0);
        String line = PlanningPromptFormatter.requiredFieldPrompt(art, sec, f, 0);
        assertTrue(line.contains("this **Decision** entry") || line.contains("**Decision** (row)"));
        assertFalse(line.contains("Decision 1"));
        assertTrue(line.contains("diagnostics/readiness") || line.contains("on the plan"));
    }

    @Test
    void repeatableSectionEmptyAndSectionMissingPromptsArePlainEnglish() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        String emptyPrompt = PlanningPromptFormatter.repeatableSectionEmptyPrompt(art, sec);
        assertTrue(emptyPrompt.contains("Internal profile record"));
        assertTrue(emptyPrompt.contains("needs at least one entry"));
        assertTrue(emptyPrompt.contains("readiness tracking"));
        String missingPrompt = PlanningPromptFormatter.requiredSectionMissingPrompt(art, sec);
        assertTrue(missingPrompt.contains("Internal profile record"));
        assertTrue(missingPrompt.contains("is missing"));
        assertTrue(missingPrompt.contains("readiness tracking"));
    }

    @Test
    void promptsTitleCaseSnakeIdsWhenArtifactOrSectionTitleMissing() {
        FieldDefinition field = new FieldDefinition("x", "Note", "text", true, "");
        SectionDefinition secUntitled = new SectionDefinition("risk_entry", "", true, true, List.of(field));
        ArtifactDefinition artUntitled =
                new ArtifactDefinition("risk_register", "", List.of(), false, List.of(secUntitled));
        WorkProfileDefinition p = new WorkProfileDefinition("p", "Profile", List.of(artUntitled));
        ArtifactDefinition art = p.getArtifact("risk_register").orElseThrow();
        SectionDefinition sec = art.getSections().get(0);
        assertEquals(
                "**Internal profile record:** The _Risk Entry_ section (Risk Register) needs at least one entry for "
                        + "readiness tracking.",
                PlanningPromptFormatter.repeatableSectionEmptyPrompt(art, sec));
        assertEquals(
                "**Internal profile record:** The _Risk Entry_ block (Risk Register) is missing for readiness "
                        + "tracking.",
                PlanningPromptFormatter.requiredSectionMissingPrompt(art, sec));
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

        String repeatableSummary = PlanningPromptFormatter.formatMissingRequiredSummary(
                profile,
                List.of("governance_decisions.adr_entry[0].decision_text"));
        assertTrue(
                repeatableSummary.contains("this **Decision** entry")
                        || repeatableSummary.contains("**Decision** (row)"));
        assertFalse(repeatableSummary.contains("Decision 1"));

        String emptySec = PlanningPromptFormatter.formatMissingRequiredSummary(
                profile,
                List.of("governance_decisions.adr_entry (repeatable section empty)"));
        assertTrue(emptySec.contains("ADR entry"));
        assertTrue(emptySec.contains("readiness tracking") || emptySec.contains("entry"));

        String missingSec = PlanningPromptFormatter.formatMissingRequiredSummary(
                profile,
                List.of("governance_decisions.adr_entry (section missing)"));
        assertTrue(missingSec.contains("missing"));
    }
}
