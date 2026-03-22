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
    void qualityGate_acceptsCanonicalGapDetail() {
        assertTrue(
                ClarificationPromptQualityGate.passes(
                        "For **Decision** (entry 1): ADR-style: decision, context, consequences."));
    }

    @Test
    void buildIntakeBlockingClarification_oneQuestionFromFirstHighGap() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"gap-a\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"missing row 0\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"For **Decision** (entry 1): ADR-style: decision, context, consequences.\"},{\"gapId\":\"gap-b\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"missing row 1\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"For **Decision** (entry 2): ADR-style: decision, context, consequences.\"},{\"gapId\":\"gap-c\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"missing row 2\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"For **Decision** (entry 3): ADR-style: decision, context, consequences.\"}]";
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        String prompt = (String) spread.get("discoveryCurrentQuestionPrompt");
        assertNotNull(prompt);
        assertFalse(prompt.contains("Planning check-in"));
        assertTrue(prompt.contains("entry 1") || prompt.contains("(entry 1)"));
        assertFalse(prompt.contains("entry 2"));
        assertEquals("[]", spread.get("discoveryBundledApplyJson"));
        assertEquals("REQUIRED_FIELD", spread.get("discoveryApplyKind"));
    }

    @Test
    void buildIntakeBlockingClarification_skipsLowSeverityOnly() throws JsonProcessingException {
        String json =
                "[{\"gapId\":\"low1\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"x\",\"sectionId\":\"y\",\"fieldId\":\"z\",\"reason\":\"r\",\"severity\":\"LOW\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"Some optional refinement text that is long enough here.\"}]";
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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
                "[{\"gapId\":\"g1\",\"kind\":\"REQUIRED_FIELD\",\"artifactId\":\"decision_log\",\"sectionId\":\"decisions\",\"fieldId\":\"decision_text\",\"reason\":\"r\",\"severity\":\"HIGH\",\"status\":\"OPEN\",\"source\":\"profile\",\"userFacingDetail\":\"For **Decision** (entry 1): ADR-style: decision, context, consequences.\"}]";
        @SuppressWarnings("unchecked")
        Map<String, Object> out =
                (Map<String, Object>) action.run(new Event("e", "k", Map.of()), Map.of("discoveryGapsJson", json), Map.of());
        String prompt = (String) out.get("discoveryCurrentQuestionPrompt");
        assertTrue(prompt.contains("ADR-style"));
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
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>)
                        StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(json);
        String prompt = (String) spread.get("discoveryCurrentQuestionPrompt");
        assertFalse(prompt.contains("Help us flesh out"));
        assertTrue(prompt.startsWith("Before we can finalize the plan"));
    }
}
