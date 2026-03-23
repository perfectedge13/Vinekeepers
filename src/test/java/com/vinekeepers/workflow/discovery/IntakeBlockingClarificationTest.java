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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void buildIntakeBlockingClarification_oneQuestionFromFirstHighGap() throws JsonProcessingException {
        String rowDetail =
                "**Decision** (item %d in this list) — reply in **one message** with: What was decided and why.";
        String json =
                "[{\"gapId\":\"gap-a\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"missing row 0\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\""
                        + String.format(rowDetail, 1)
                        + "\"},{\"gapId\":\"gap-b\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"missing row 1\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\""
                        + String.format(rowDetail, 2)
                        + "\"},{\"gapId\":\"gap-c\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"missing row 2\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\""
                        + String.format(rowDetail, 3)
                        + "\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        String prompt = (String) spread.get("discoveryCurrentQuestionPrompt");
        assertNotNull(prompt);
        assertFalse(prompt.contains("Planning check-in"));
        assertTrue(prompt.contains("item 1"));
        assertFalse(prompt.contains("item 2"));
        assertEquals("[]", spread.get("discoveryBundledApplyJson"));
        assertEquals("REQUIRED_FIELD", spread.get("discoveryApplyKind"));
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
    void buildInsightDiscoveryAgendaAction_matchesIntakeBlockingPath() throws Exception {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        var action = new BuildInsightDiscoveryAgendaAction(new FeaturePlanStateStore(), reg);
        String json =
                "[{\"gapId\":\"g1\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"r\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"**Decision** (item 1 in this list) — reply in **one message** with: What was decided and why.\"}]";
        Object raw = action.run(new Event("e", "k", Map.of()), Map.of("discoveryGapsJson", json), Map.of());
        assertTrue(raw instanceof Map<?, ?>);
        Map<?, ?> out = (Map<?, ?>) raw;
        String prompt = (String) out.get("discoveryCurrentQuestionPrompt");
        assertTrue(prompt.contains("one message"));
        assertFalse(prompt.contains("Planning check-in"));
    }

    @Test
    void duplicateSemanticLines_notEmittedAsBundledBatch() throws JsonProcessingException {
        String identical =
                "Help us flesh out **Decision** with concrete detail.";
        String json =
                "[{\"gapId\":\"a\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"r1\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\""
                        + identical
                        + "\"},{\"gapId\":\"b\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"r2\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\""
                        + identical
                        + "\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        String prompt = (String) spread.get("discoveryCurrentQuestionPrompt");
        assertFalse(prompt.contains("Help us flesh out"));
        assertTrue(prompt.startsWith("Before we can finalize the plan"));
    }

    @Test
    void openQuestionsFallbackDoesNotExposeInternalFieldPath() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"oq\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"open_questions_block\",\"sectionId\":\"backlog\",\"fieldId\":\"open_questions\",\"reason\":\"Missing required field open_questions_block.backlog.open_questions\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"\"}]";
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        String prompt = (String) spread.get("discoveryCurrentQuestionPrompt");
        assertTrue(prompt.contains("Before we can finalize the plan"));
        assertTrue(prompt.contains("Reply in plain text in this thread."));
        assertFalse(prompt.contains("Missing required field"));
        assertFalse(prompt.contains("open_questions_block.backlog.open_questions"));
    }
}
