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
    void requiredFieldPromptForOpenQuestionsUsesClarificationSafeWording() {
        FieldDefinition field = new FieldDefinition(
                "open_questions",
                "Open questions",
                "text",
                true,
                "Unknowns to confirm, one per line.");
        SectionDefinition section = new SectionDefinition(
                "backlog",
                "Unresolved questions",
                false,
                true,
                List.of(field));
        ArtifactDefinition artifact = new ArtifactDefinition(
                "open_questions_block",
                "Open questions",
                List.of(),
                true,
                List.of(section));
        String prompt = PlanningPromptFormatter.requiredFieldPrompt(artifact, section, field, -1);
        assertTrue(prompt.contains("None — ready to implement"));
        assertFalse(prompt.toLowerCase().contains("open questions"));
        assertTrue(ClarificationPromptQualityGate.passes(prompt));
    }

    @Test
    void requiredFieldPromptUsesLabelHintAndDefaultWhenNoHint() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition withHint = sec.getFields().get(0);
        String withHintPrompt = PlanningPromptFormatter.requiredFieldPrompt(art, sec, withHint, -1);
        assertTrue(withHintPrompt.contains("**Question:**"));
        assertTrue(withHintPrompt.contains("ADR-style"));
        assertTrue(withHintPrompt.contains("on the plan"));

        FieldDefinition noHint = new FieldDefinition("x", "Scope", "text", true, "");
        String noHintPrompt = PlanningPromptFormatter.requiredFieldPrompt(art, sec, noHint, -1);
        assertTrue(noHintPrompt.contains("**Question:**"));
        assertTrue(noHintPrompt.contains("send the specific text or decision to store on the plan"));
    }

    @Test
    void requiredFieldPromptRepeatableRowMentionsRowNumber() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        FieldDefinition f = sec.getFields().get(0);
        String line = PlanningPromptFormatter.requiredFieldPrompt(art, sec, f, 0);
        assertTrue(line.contains("Decision 1"));
        assertTrue(line.contains("save it on the plan"));
    }

    @Test
    void repeatableSectionEmptyAndSectionMissingPromptsArePlainEnglish() {
        WorkProfileDefinition profile = sampleProfile();
        ArtifactDefinition art = profile.getArtifactsById().get("governance_decisions");
        SectionDefinition sec = art.getSections().get(0);
        String emptyPrompt = PlanningPromptFormatter.repeatableSectionEmptyPrompt(art, sec);
        assertTrue(emptyPrompt.contains("**Question:**"));
        assertTrue(emptyPrompt.contains("needs at least one entry"));
        assertTrue(emptyPrompt.contains("save it on the plan"));
        String missingPrompt = PlanningPromptFormatter.requiredSectionMissingPrompt(art, sec);
        assertTrue(missingPrompt.contains("**Question:**"));
        assertTrue(missingPrompt.contains("is missing"));
        assertTrue(missingPrompt.contains("save it on the plan"));
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
                "**Question:** The _Risk Entry_ section (Risk Register) needs at least one entry. "
                        + "Reply with the first item to add so we can save it on the plan.",
                PlanningPromptFormatter.repeatableSectionEmptyPrompt(art, sec));
        assertEquals(
                "**Question:** The _Risk Entry_ block (Risk Register) is missing. "
                        + "Reply with the main content we should add for that section so we can save it on the plan.",
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
