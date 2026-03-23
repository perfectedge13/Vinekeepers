package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers plain-language readiness / critique copy used in Discord (no raw enum strings to users).
 */
class PlanningUserFacingCopyTest {

    private static WorkProfileDefinition sampleProfile() {
        FieldDefinition field = new FieldDefinition(
                "decision_text",
                "Decision",
                "text",
                true,
                "Describe the call.");
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
    void humanizeReadinessStatusUsesPlainEnglishForDecisionGate() {
        assertEquals(
                "Needs your decision",
                PlanningUserFacingCopy.humanizeReadinessStatus("NEEDS_HUMAN_DECISION"));
        assertEquals("Ready", PlanningUserFacingCopy.humanizeReadinessStatus("READY"));
        assertEquals("Not ready", PlanningUserFacingCopy.humanizeReadinessStatus("NOT_READY"));
    }

    @Test
    void readinessCheckpointGuideExplainsForkInPlainLanguage() {
        String g = PlanningUserFacingCopy.readinessCheckpointGuideForDiscord();
        assertTrue(g.contains("buttons in the next message"));
        assertTrue(g.contains("launch-approval menu"));
        assertTrue(g.contains("Not asking for"));
        assertFalse(g.contains("**Continue to approval**"));
        assertFalse(g.toLowerCase().contains("needs_human_decision"));
    }

    @Test
    void humanizeCritiqueSeverityMapsCommonCodes() {
        assertEquals("Must fix", PlanningUserFacingCopy.humanizeCritiqueSeverity("MUST_FIX"));
        assertEquals("Blocking", PlanningUserFacingCopy.humanizeCritiqueSeverity("BLOCKER"));
        assertEquals("Note", PlanningUserFacingCopy.humanizeCritiqueSeverity("INFO"));
    }

    @Test
    void describePlanningFieldRefUsesLabelsFromProfile() {
        WorkProfileDefinition profile = sampleProfile();
        assertEquals(
                "Initial request summary",
                PlanningUserFacingCopy.describePlanningFieldRef(profile, "initialRequest"));
        String path = PlanningUserFacingCopy.describePlanningFieldRef(
                profile, "governance_decisions.adr_entry.decision_text");
        assertTrue(path.contains("Governance decisions"));
        assertTrue(path.contains("ADR entry"));
        assertTrue(path.contains("Decision"));
    }

    @Test
    void formatCritiqueFindingBulletIncludesSeverityAndMessage() {
        WorkProfileDefinition profile = sampleProfile();
        PlanCritiqueFinding f = new PlanCritiqueFinding(
                "1",
                "readiness",
                "WARN",
                "CUSTOM",
                "Please clarify scope.",
                "governance_decisions.adr_entry.decision_text");
        String bullet = PlanningUserFacingCopy.formatCritiqueFindingBullet(f, profile);
        assertTrue(bullet.contains("Warning"));
        assertTrue(bullet.contains("Please clarify scope"));
    }

    @Test
    void humanizeReadinessAndRelatedStatusesUsePlainEnglish() {
        assertEquals("Blocked", PlanningUserFacingCopy.humanizeReadinessStatus("BLOCKED"));
        assertEquals("Needs revision", PlanningUserFacingCopy.humanizeReadinessStatus("NEEDS_REVISION"));
        assertEquals(
                "Conditionally ready",
                PlanningUserFacingCopy.humanizeReadinessStatus("CONDITIONALLY_READY"));
        assertEquals("Confirmed", PlanningUserFacingCopy.humanizeAssumptionStatus("CONFIRMED"));
        assertEquals("Accepted risk", PlanningUserFacingCopy.humanizeAssumptionStatus("ACCEPTED_RISK"));
        assertEquals("Resolved", PlanningUserFacingCopy.humanizeIssueStatus("RESOLVED"));
        assertEquals("Waived", PlanningUserFacingCopy.humanizeRiskDecisionStatus("WAIVED"));
        assertEquals("High", PlanningUserFacingCopy.humanizeGovernanceSeverity("HIGH"));
    }

    @Test
    void defaultCritiqueMessagesAvoidRawCodes() {
        assertTrue(
                PlanningUserFacingCopy.defaultMessageForCritiqueCode("OPEN_ISSUES")
                        .toLowerCase()
                        .contains("issue"));
        assertTrue(
                PlanningUserFacingCopy.defaultMessageForCritiqueCode("DISCOVERY_GAP_WORKSPACE")
                        .contains("workspace"));
        assertEquals(
                "A planning discovery item is still unresolved.",
                PlanningUserFacingCopy.defaultDiscoveryGapMessage("OTHER"));
    }

    @Test
    void humanizePlanningClarificationMergeErrorNeverEchoesRawExceptionText() {
        assertEquals(
                "That reply could not be applied; try again.",
                PlanningUserFacingCopy.humanizePlanningClarificationMergeError("java.lang.NullPointerException"));
        assertEquals(
                "That reply could not be applied; try again.",
                PlanningUserFacingCopy.humanizePlanningClarificationMergeError("Something vague went wrong"));
    }

    @Test
    void humanizePlanningRoomCycleErrorMapsCodesAndDepthPrefix() {
        assertTrue(
                PlanningUserFacingCopy.humanizePlanningRoomCycleErrorLine("NO_PLAN")
                        .toLowerCase()
                        .contains("draft"));
        assertEquals(
                "After several drafting passes the plan still needs more concrete detail before review.",
                PlanningUserFacingCopy.humanizePlanningRoomCycleErrorLine(
                        "DEPTH_FAIL_AFTER_RETRIES: thin outline"));
        assertEquals("", PlanningUserFacingCopy.humanizePlanningRoomCycleErrorLine("  "));
        assertEquals(
                "Planning hit an unexpected issue; try again or check configuration.",
                PlanningUserFacingCopy.humanizePlanningRoomCycleErrorCode("CUSTOM_CODE"));
        assertTrue(
                PlanningUserFacingCopy.humanizePlanningRoomCycleErrorCode("SYNTHESIS_UPSERTS_NOT_APPLIED")
                        .toLowerCase()
                        .contains("schema"));
    }

    @Test
    void repoGroundingPointerMentionsPacketSection() {
        String p = PlanningUserFacingCopy.repoGroundingPointerAfterPacket();
        assertTrue(p.contains("Repo / workspace (grounding)"));
        assertTrue(p.contains("planning packet"));
    }

    @Test
    void defaultMessageForIntakeDiscoveryUsesPlainLanguage() {
        assertTrue(
                PlanningUserFacingCopy.defaultMessageForCritiqueCode("INTAKE_DISCOVERY_INCOMPLETE")
                        .toLowerCase()
                        .contains("first planning"));
    }

    @Test
    void formatCritiqueFindingBulletReplacesRawRefInMessageAndAddsAreaWhenOmitted() {
        WorkProfileDefinition profile = sampleProfile();
        PlanCritiqueFinding withRawRef = new PlanCritiqueFinding(
                "1",
                "readiness",
                "MUST_FIX",
                "X",
                "Fix governance_decisions.adr_entry.decision_text before continuing.",
                "governance_decisions.adr_entry.decision_text");
        String b1 = PlanningUserFacingCopy.formatCritiqueFindingBullet(withRawRef, profile);
        assertTrue(b1.contains("Must fix"));
        assertFalse(b1.contains("governance_decisions.adr_entry"));
        assertTrue(b1.contains("Decision"));

        PlanCritiqueFinding terse = new PlanCritiqueFinding(
                "2", "readiness", "INFO", "Y", "Short note.", "governance_decisions.adr_entry.decision_text");
        String b2 = PlanningUserFacingCopy.formatCritiqueFindingBullet(terse, profile);
        assertTrue(b2.contains("Area:"));
    }
}
