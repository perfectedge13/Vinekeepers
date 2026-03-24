package com.vinekeepers.workflow.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.actions.BuildInsightDiscoveryAgendaAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntakeBlockingClarificationTest {

    @Test
    void qualityGate_rejectsGenericFleshOutDecision() {
        assertFalse(
                ClarificationPromptQualityGate.passes(
                        "Help us flesh out **Decision** with concrete detail."));
    }

    @Test
    void qualityGate_rejectsPlanningCheckInWrapper() {
        assertFalse(
                ClarificationPromptQualityGate.passes(
                        "**Planning check-in** — a few focused questions.\n\n1. Something long enough to pass length."));
    }

    @Test
    void qualityGate_substantiveOpenQuestionLineRejectsMetaAndShortText() {
        assertFalse(ClarificationPromptQualityGate.isSubstantiveOpenQuestionLine("short"));
        assertFalse(
                ClarificationPromptQualityGate.isSubstantiveOpenQuestionLine(
                        "Are there any other open questions we should track?"));
        assertTrue(
                ClarificationPromptQualityGate.isSubstantiveOpenQuestionLine(
                        "What is the maximum acceptable latency for the export job when the warehouse is under peak load?"));
    }

    @Test
    void qualityGate_detectsInternalMechanismDetail() {
        assertTrue(
                ClarificationPromptQualityGate.isInternalMechanismDetail(
                        "com.example.Oops: boom\n\tat com.vinekeepers.workflow.X.y(X.java:12)"));
        assertFalse(ClarificationPromptQualityGate.isInternalMechanismDetail("Which region should host the primary database?"));
    }

    @Test
    void qualityGate_acceptsCanonicalGapDetail() {
        assertTrue(
                ClarificationPromptQualityGate.passes(
                        "**Decision** (item 1 in this list) — reply in **one message** with: "
                                + "What you decided; the situation or constraints behind it; and what that means for the work ahead "
                                + "(trade-offs, follow-ups, or options you ruled out). Plain sentences are fine — we store this on the plan."));
    }

    @Test
    void buildIntakeBlockingClarification_ignoresRequiredFieldGaps() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"gap-a\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"missing row 0\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"Need decision text.\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        assertEquals("", spread.get("discoveryCurrentQuestionPrompt"));
        assertEquals("[]", spread.get("discoveryBundledApplyJson"));
        assertEquals("NONE", spread.get("discoveryApplyKind"));
    }

    @Test
    void buildIntakeBlockingClarification_skipsLowSeverityOnly() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"low1\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"x\",\"sectionId\":\"y\",\"fieldId\":\"z\",\"reason\":\"r\",\"severity\":\"LOW\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"Some optional refinement text that is long enough here.\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        assertEquals("", spread.get("discoveryCurrentQuestionPrompt"));
        assertEquals("NONE", spread.get("discoveryApplyKind"));
    }

    @Test
    void buildIntakeBlockingClarification_skipsMediumSeverity_nonBlockingGating() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"med1\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"x\",\"sectionId\":\"y\",\"fieldId\":\"z\",\"reason\":\"r\",\"severity\":\"MEDIUM\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"Medium severity optional detail that is long enough to be substantive text.\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        assertEquals("", spread.get("discoveryCurrentQuestionPrompt"));
        assertEquals("NONE", spread.get("discoveryApplyKind"));
    }

    @Test
    void buildInsightDiscoveryAgendaAction_onlySurfacesWorkspaceBlockers() throws Exception {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var action = new BuildInsightDiscoveryAgendaAction(new FeaturePlanStateStore(), reg);
        String json =
                "[{\"gapId\":\"g1\",\"kind\":\"WORKSPACE\",\"artifactId\":\"\",\"sectionId\":\"\",\"fieldId\":\"\",\"reason\":\"workspace unavailable\",\"severity\":\"BLOCKER\",\"status\":\"OPEN\",\"source\":\"repo.workspace\",\"userFacingDetail\":\"Workspace checkout is unavailable. Reply with a local checkout path or access notes.\"}]";
        Object raw = action.run(new Event("e", "k", Map.of()), Map.of("discoveryGapsJson", json), Map.of());
        assertTrue(raw instanceof Map<?, ?>);
        Map<?, ?> out = (Map<?, ?>) raw;
        String prompt = (String) out.get("discoveryCurrentQuestionPrompt");
        assertTrue(prompt.contains("local checkout path"));
        assertEquals("WORKSPACE", out.get("discoveryApplyKind"));
    }

    @Test
    void duplicateRequiredFieldLines_notEmittedAsBundledBatch() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"a\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"r1\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"Need decision text.\"},{\"gapId\":\"b\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"r2\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"Need decision text.\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        assertEquals("", spread.get("discoveryCurrentQuestionPrompt"));
        assertEquals("NONE", spread.get("discoveryApplyKind"));
    }

    @Test
    void requiredFieldPromptDoesNotSurfaceInternalFieldPathBecauseItDoesNotSurface() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"req\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\",\"fieldId\":\"feature_summary\",\"reason\":\"Missing required field requirements_spec.narrative.feature_summary\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        String prompt = (String) spread.get("discoveryCurrentQuestionPrompt");
        assertEquals("", prompt);
        assertFalse(prompt.contains("Missing required field"));
        assertFalse(prompt.contains("requirements_spec.narrative.feature_summary"));
    }
}
