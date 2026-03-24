package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiCallContext;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanDecision;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanRisk;
import com.vinekeepers.state.planning.PlanRiskDecisionStatus;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single semantic authority for planning evaluation: gaps, confidence, one-question clarification, AIRD classification,
 * and packet readiness all come from one structured LLM response.
 */
public final class PlanningEvaluationService {

    public record EvaluationContext(
            String phaseContext,
            String repoGroundingSummary,
            String repoEvidenceJson,
            boolean depthOk,
            String depthReason,
            boolean structuredParseFailed,
            int priorClarificationAttempts) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EVALUATION_REPAIR_SHAPE_HINT =
            "Preserve the intended planning evaluation shape: confidence object, gaps array, ask_user_required boolean, "
                    + "best_question object, assumptions_to_add array, issues_to_add array, risks_to_add array, "
                    + "decisions_to_add array, ready_for_packet boolean.";
    private static final String SYSTEM = """
            You evaluate a software feature planning state.
            Reply with exactly one valid JSON object only. No markdown fences. No prose.

            You are the single semantic authority for:
            - confidence
            - gap analysis
            - whether a user question is needed
            - the single best question if needed
            - assumptions / issues / risks / decisions to add
            - whether the plan is ready for packet

            Rules:
            - Missing fields alone MUST NOT trigger a question.
            - Only blocking or branching gaps may justify ask_user_required=true.
            - Low-risk unknowns should become assumptions_to_add, not questions.
            - Return at most one best_question.
            - best_question.text must be empty when ask_user_required is false.
            - ready_for_packet must be false when blocking gaps remain.
            - Base all reasoning on the canonical state snapshot provided.

            JSON schema:
            {
              "confidence": {
                "score": 0,
                "level": "low",
                "summary": ""
              },
              "gaps": [
                {
                  "id": "missing_authority",
                  "kind": "MISSING_AUTHORITY",
                  "description": "",
                  "blocking": true,
                  "askable": true,
                  "assumable": false
                }
              ],
              "ask_user_required": false,
              "best_question": {
                "text": "",
                "rationale": ""
              },
              "assumptions_to_add": [
                { "statement": "", "severity": "MEDIUM" }
              ],
              "issues_to_add": [
                { "title": "", "detail": "", "severity": "MEDIUM", "blocking": false }
              ],
              "risks_to_add": [
                { "statement": "", "impact": "", "likelihood": "" }
              ],
              "decisions_to_add": [
                { "decision": "", "rationale": "", "status": "OPEN" }
              ],
              "ready_for_packet": false
            }

            Allowed gap kinds include:
            BLOCKING_CONSTRAINT
            BRANCHING_DECISION
            CONTRADICTION
            UNVERIFIED_ASSUMPTION
            WEAK_VALIDATION
            MISSING_AUTHORITY
            MISSING_IMPLEMENTATION_SCOPE
            MISSING_ROLLOUT_BOUNDARY

            If unsure, stay conservative, prefer assumptions over user questions, and keep JSON valid.
            """;

    private final OpenAiChatClient openAiChatClient;

    public PlanningEvaluationService(OpenAiChatClient openAiChatClient) {
        this.openAiChatClient = openAiChatClient;
    }

    public PlanningEvaluationDecision evaluate(
            Event event,
            Map<String, Object> state,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            EvaluationContext context) {
        if (plan == null) {
            return failure("NO_PLAN");
        }
        if (profile == null) {
            return failure("NO_PROFILE");
        }
        if (openAiChatClient == null || !openAiChatClient.isConfigured()) {
            return failure("NO_API_KEY");
        }
        String userPayload = buildUserPayload(plan, profile, state, context);
        String raw;
        try {
            OpenAiCallContext callCtx =
                    OpenAiCallContext.planning(
                            event,
                            state,
                            "Evaluating planning gaps, confidence, and one clarification question (single semantic pass).");
            raw = openAiChatClient.complete(SYSTEM, userPayload, null, null, callCtx);
        } catch (Exception e) {
            return failure("EVALUATION_TRANSPORT_ERROR");
        }
        if (raw == null || raw.startsWith("ERROR:")) {
            return failure("EVALUATION_TRANSPORT_ERROR");
        }
        PlanningLlmJsonSupport.ParsedJsonObjectResult parsed =
                PlanningLlmJsonSupport.parseJsonObjectWithRepair(
                        openAiChatClient,
                        raw,
                        "planning evaluation",
                        event,
                        state,
                        EVALUATION_REPAIR_SHAPE_HINT);
        if (!parsed.success()) {
            return new PlanningEvaluationDecision(
                    false,
                    PlanningCanonicalNextAction.BLOCK,
                    PlanningIntakeStage.FAILED,
                    PlanningInteractionState.NONE,
                    repoGroundingState(plan, context != null ? context.repoEvidenceJson() : ""),
                    new PlanningEvaluationDecision.EvaluationConfidence(0, "low", ""),
                    List.of(),
                    false,
                    new PlanningEvaluationDecision.EvaluationBestQuestion("", ""),
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    false,
                    false,
                    false,
                    parsed.repairAttempted() ? "EVALUATION_REPAIR_EXHAUSTED" : "EVALUATION_JSON_INVALID",
                    "",
                    parsed.repairAttempted(),
                    parsed.repairExhausted(),
                    parsed.repairAttempted() ? "EVALUATION_REPAIR_EXHAUSTED" : "EVALUATION_JSON_INVALID");
        }
        return parseAndValidate(parsed.root(), plan, context, parsed.repairAttempted(), parsed.repairExhausted());
    }

    public FeaturePlanState applyEvaluationDeltas(FeaturePlanState plan, PlanningEvaluationDecision decision) {
        if (plan == null || decision == null || !decision.success()) {
            return plan;
        }
        FeaturePlanState next = plan;
        Set<String> assumptionKeys = new LinkedHashSet<>();
        for (PlanAssumption assumption : next.getAssumptions()) {
            assumptionKeys.add(normalize(assumption.getStatement()));
        }
        for (PlanningEvaluationDecision.AssumptionProposal proposal : decision.assumptionsToAdd()) {
            String statement = proposal != null ? blankToEmpty(proposal.statement()) : "";
            String normalized = normalize(statement);
            if (normalized.isBlank() || !assumptionKeys.add(normalized)) {
                continue;
            }
            next = next.withAppendedAssumption(
                    new PlanAssumption(
                            stableId("asm", normalized),
                            statement,
                            "OPEN",
                            normalizeSeverity(proposal != null ? proposal.severity() : ""),
                            "PLANNING_EVALUATION",
                            "",
                            List.of(),
                            Instant.now(),
                            Instant.now()));
        }

        Set<String> issueKeys = new LinkedHashSet<>();
        for (PlanIssue issue : next.getIssues()) {
            issueKeys.add(normalize(issue.getTitle() + "|" + issue.getDetail()));
        }
        for (PlanningEvaluationDecision.IssueProposal proposal : decision.issuesToAdd()) {
            String title = proposal != null ? blankToEmpty(proposal.title()) : "";
            String detail = proposal != null ? blankToEmpty(proposal.detail()) : "";
            String normalized = normalize(title + "|" + detail);
            if (normalized.isBlank() || !issueKeys.add(normalized)) {
                continue;
            }
            next = next.withAppendedIssue(
                    new PlanIssue(
                            stableId("iss", normalized),
                            title,
                            detail,
                            proposal != null && proposal.blocking() ? PlanIssueStatus.BLOCKING : PlanIssueStatus.OPEN,
                            normalizeSeverity(proposal != null ? proposal.severity() : ""),
                            "PLANNING_EVALUATION",
                            "",
                            List.of(),
                            Instant.now(),
                            Instant.now()));
        }

        Set<String> riskKeys = new LinkedHashSet<>();
        for (PlanRisk risk : next.getRisks()) {
            riskKeys.add(normalize(risk.getStatement()));
        }
        for (PlanningEvaluationDecision.RiskProposal proposal : decision.risksToAdd()) {
            String statement = proposal != null ? blankToEmpty(proposal.statement()) : "";
            String normalized = normalize(statement);
            if (normalized.isBlank() || !riskKeys.add(normalized)) {
                continue;
            }
            next = next.withAppendedRisk(
                    new PlanRisk(
                            stableId("risk", normalized),
                            statement,
                            proposal != null ? blankToEmpty(proposal.impact()) : "",
                            proposal != null ? blankToEmpty(proposal.likelihood()) : "",
                            PlanRiskDecisionStatus.OPEN,
                            "PLANNING_EVALUATION",
                            List.of(),
                            Instant.now()));
        }

        Set<String> decisionKeys = new LinkedHashSet<>();
        for (PlanDecision existingDecision : next.getDecisions()) {
            decisionKeys.add(normalize(existingDecision.getDecision()));
        }
        for (PlanningEvaluationDecision.DecisionProposal proposal : decision.decisionsToAdd()) {
            String decisionText = proposal != null ? blankToEmpty(proposal.decision()) : "";
            String normalized = normalize(decisionText);
            if (normalized.isBlank() || !decisionKeys.add(normalized)) {
                continue;
            }
            next = next.withAppendedDecision(
                    new PlanDecision(
                            stableId("dec", normalized),
                            decisionText,
                            proposal != null ? blankToEmpty(proposal.rationale()) : "",
                            normalizeDecisionStatus(proposal != null ? proposal.status() : ""),
                            "PLANNING_EVALUATION",
                            List.of(),
                            Instant.now()));
        }

        return next.withPlanConfidence(toPlanConfidence(next, decision))
                .withPlanningConfidenceBreakdownJson(toConfidenceBreakdown(next, decision, null).toJson());
    }

    public PlanningConfidenceBreakdown toConfidenceBreakdown(
            FeaturePlanState plan, PlanningEvaluationDecision decision, EvaluationContext context) {
        double repoGrounding = repoGroundingScore(plan, context != null ? context.repoGroundingSummary() : "");
        return new PlanningConfidenceBreakdown(
                repoGrounding,
                decision != null && decision.confidence() != null
                        ? Math.max(0.0, Math.min(1.0, decision.confidence().score() / 100.0))
                        : 0.0,
                context == null || context.depthOk(),
                context == null || !context.structuredParseFailed(),
                countKnownFacts(plan),
                decision != null && decision.gaps() != null ? decision.gaps().size() : 0,
                decision != null && decision.confidence() != null ? decision.confidence().summary() : "");
    }

    public PlanConfidence toPlanConfidence(FeaturePlanState plan, PlanningEvaluationDecision decision) {
        PlanningEvaluationDecision.EvaluationConfidence confidence =
                decision != null && decision.confidence() != null
                        ? decision.confidence()
                        : new PlanningEvaluationDecision.EvaluationConfidence(0, "low", "");
        List<String> reasons = new ArrayList<>();
        if (decision != null && decision.gaps() != null) {
            for (CanonicalPlanningGap gap : decision.gaps()) {
                if (gap == null) {
                    continue;
                }
                String reason = gap.evidenceSummary() != null && !gap.evidenceSummary().isBlank()
                        ? gap.evidenceSummary().trim()
                        : gap.questionSeed();
                if (reason != null && !reason.isBlank()) {
                    reasons.add(reason);
                }
            }
        }
        List<String> gapIds =
                decision != null && decision.gaps() != null
                        ? decision.gaps().stream()
                                .map(CanonicalPlanningGap::gapId)
                                .filter(s -> s != null && !s.isBlank())
                                .limit(6)
                                .toList()
                        : List.of();
        return new PlanConfidence(
                confidence.level(),
                confidence.summary(),
                decision != null && decision.readyForPacket()
                        ? PlanReadinessStatus.REVIEWABLE
                        : PlanReadinessStatus.NOT_READY,
                Instant.now(),
                Math.max(0.0, Math.min(1.0, confidence.score() / 100.0)),
                reasons,
                countKnownFacts(plan),
                decision != null && decision.gaps() != null ? decision.gaps().size() : 0,
                gapIds);
    }

    private PlanningEvaluationDecision parseAndValidate(
            JsonNode root,
            FeaturePlanState plan,
            EvaluationContext context,
            boolean repairAttempted,
            boolean repairExhausted) {
        PlanningEvaluationDecision.EvaluationConfidence confidence = parseConfidence(root.path("confidence"));
        List<CanonicalPlanningGap> gaps = parseGaps(root.path("gaps"));
        boolean askUserRequired = root.path("ask_user_required").asBoolean(false);
        PlanningEvaluationDecision.EvaluationBestQuestion bestQuestion = parseBestQuestion(root.path("best_question"));
        List<PlanningEvaluationDecision.AssumptionProposal> assumptions = parseAssumptions(root.path("assumptions_to_add"));
        List<PlanningEvaluationDecision.IssueProposal> issues = parseIssues(root.path("issues_to_add"));
        List<PlanningEvaluationDecision.RiskProposal> risks = parseRisks(root.path("risks_to_add"));
        List<PlanningEvaluationDecision.DecisionProposal> decisions = parseDecisions(root.path("decisions_to_add"));
        boolean readyForPacket = root.path("ready_for_packet").asBoolean(false);
        if (context != null && context.structuredParseFailed()) {
            readyForPacket = false;
        }
        if (context != null && !context.depthOk()) {
            readyForPacket = false;
        }
        if (hasDuplicateGapIds(gaps)) {
            return failure("EVALUATION_INVALID_DUPLICATE_GAP_ID", repairAttempted, repairExhausted);
        }
        if (askUserRequired) {
            CanonicalPlanningGap chosen = chooseAskGap(gaps);
            if (chosen == null) {
                return failure("EVALUATION_INVALID_NO_ELIGIBLE_ASK_GAP", repairAttempted, repairExhausted);
            }
            if (countEligibleAskGaps(gaps) != 1) {
                return failure("EVALUATION_INVALID_MULTIPLE_ASK_GAPS", repairAttempted, repairExhausted);
            }
            String canonicalQuestionText = PlanningQuestionComposer.presentCanonicalQuestion(bestQuestion.text());
            if (canonicalQuestionText.isBlank()) {
                return failure("EVALUATION_INVALID_QUESTION", repairAttempted, repairExhausted);
            }
            bestQuestion = new PlanningEvaluationDecision.EvaluationBestQuestion(
                    canonicalQuestionText,
                    blankToEmpty(bestQuestion.rationale()));
        } else if (!bestQuestion.text().isBlank()) {
            return failure("EVALUATION_INVALID_UNEXPECTED_QUESTION", repairAttempted, repairExhausted);
        }
        if (askUserRequired && readyForPacket) {
            return failure("EVALUATION_INVALID_CONFLICTING_ROUTE", repairAttempted, repairExhausted);
        }
        if (containsBlockingGap(gaps) && readyForPacket) {
            return failure("EVALUATION_INVALID_BLOCKING_READY", repairAttempted, repairExhausted);
        }
        CanonicalPlanningGap topGap = selectTopGap(gaps);
        PlanningCanonicalNextAction nextAction = determineNextAction(askUserRequired, readyForPacket, gaps);
        String blockReason = determineBlockReason(nextAction, topGap);
        if (nextAction == PlanningCanonicalNextAction.BLOCK && blockReason.isBlank()) {
            return failure("EVALUATION_INVALID_BLOCK_WITHOUT_REASON", repairAttempted, repairExhausted);
        }
        return new PlanningEvaluationDecision(
                true,
                nextAction,
                stageFor(nextAction),
                interactionStateFor(nextAction),
                repoGroundingState(plan, context != null ? context.repoEvidenceJson() : ""),
                confidence,
                List.copyOf(gaps),
                askUserRequired,
                bestQuestion,
                topGap != null ? blankToEmpty(topGap.gapId()) : "",
                topGap != null ? blankToEmpty(topGap.questionSeed()) : "",
                List.copyOf(assumptions),
                List.copyOf(issues),
                List.copyOf(risks),
                List.copyOf(decisions),
                readyForPacket,
                nextAction == PlanningCanonicalNextAction.READY_FOR_PACKET,
                false,
                false,
                blockReason,
                decisionSummary(confidence, topGap, blockReason),
                repairAttempted,
                repairExhausted,
                "");
    }

    private static PlanningEvaluationDecision failure(String machineError) {
        return failure(machineError, false, false);
    }

    private static PlanningEvaluationDecision failure(String machineError, boolean repairAttempted, boolean repairExhausted) {
        return new PlanningEvaluationDecision(
                false,
                PlanningCanonicalNextAction.BLOCK,
                PlanningIntakeStage.FAILED,
                PlanningInteractionState.NONE,
                "NOT_MATERIALIZED",
                new PlanningEvaluationDecision.EvaluationConfidence(0, "low", ""),
                List.of(),
                false,
                new PlanningEvaluationDecision.EvaluationBestQuestion("", ""),
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                false,
                false,
                blankToEmpty(machineError),
                "",
                repairAttempted,
                repairExhausted,
                machineError);
    }

    private static PlanningEvaluationDecision.EvaluationConfidence parseConfidence(JsonNode node) {
        int score = node.path("score").asInt(0);
        score = Math.max(0, Math.min(100, score));
        String level = normalizeConfidenceLevel(node.path("level").asText(""));
        String summary = blankToEmpty(node.path("summary").asText(""));
        return new PlanningEvaluationDecision.EvaluationConfidence(score, level, summary);
    }

    private static List<CanonicalPlanningGap> parseGaps(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<CanonicalPlanningGap> gaps = new ArrayList<>();
        for (JsonNode child : node) {
            if (!child.isObject()) {
                continue;
            }
            String id = blankToEmpty(child.path("id").asText(""));
            String kind = blankToEmpty(child.path("kind").asText(""));
            String description = blankToEmpty(child.path("description").asText(""));
            if (id.isBlank() || kind.isBlank() || description.isBlank()) {
                continue;
            }
            boolean blocking = child.path("blocking").asBoolean(false);
            boolean askable = child.path("askable").asBoolean(false);
            boolean assumable = child.path("assumable").asBoolean(!blocking);
            gaps.add(CanonicalPlanningGap.fromEvaluation(id, kind, description, blocking, askable, assumable, "", List.of()));
        }
        return gaps;
    }

    private static PlanningEvaluationDecision.EvaluationBestQuestion parseBestQuestion(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new PlanningEvaluationDecision.EvaluationBestQuestion("", "");
        }
        return new PlanningEvaluationDecision.EvaluationBestQuestion(
                blankToEmpty(node.path("text").asText("")),
                blankToEmpty(node.path("rationale").asText("")));
    }

    private static List<PlanningEvaluationDecision.AssumptionProposal> parseAssumptions(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlanningEvaluationDecision.AssumptionProposal> out = new ArrayList<>();
        for (JsonNode child : node) {
            if (child.isTextual()) {
                String text = blankToEmpty(child.asText(""));
                if (!text.isBlank()) {
                    out.add(new PlanningEvaluationDecision.AssumptionProposal(text, PlanGovernanceSeverity.MEDIUM));
                }
                continue;
            }
            if (!child.isObject()) {
                continue;
            }
            String statement = blankToEmpty(child.path("statement").asText(""));
            if (statement.isBlank()) {
                continue;
            }
            out.add(new PlanningEvaluationDecision.AssumptionProposal(
                    statement,
                    normalizeSeverity(child.path("severity").asText(""))));
        }
        return out;
    }

    private static List<PlanningEvaluationDecision.IssueProposal> parseIssues(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlanningEvaluationDecision.IssueProposal> out = new ArrayList<>();
        for (JsonNode child : node) {
            if (!child.isObject()) {
                continue;
            }
            String title = blankToEmpty(child.path("title").asText(""));
            String detail = blankToEmpty(child.path("detail").asText(""));
            if (title.isBlank() && detail.isBlank()) {
                continue;
            }
            out.add(new PlanningEvaluationDecision.IssueProposal(
                    title,
                    detail,
                    normalizeSeverity(child.path("severity").asText("")),
                    child.path("blocking").asBoolean(false)));
        }
        return out;
    }

    private static List<PlanningEvaluationDecision.RiskProposal> parseRisks(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlanningEvaluationDecision.RiskProposal> out = new ArrayList<>();
        for (JsonNode child : node) {
            if (!child.isObject()) {
                continue;
            }
            String statement = blankToEmpty(child.path("statement").asText(""));
            if (statement.isBlank()) {
                continue;
            }
            out.add(new PlanningEvaluationDecision.RiskProposal(
                    statement,
                    blankToEmpty(child.path("impact").asText("")),
                    blankToEmpty(child.path("likelihood").asText(""))));
        }
        return out;
    }

    private static List<PlanningEvaluationDecision.DecisionProposal> parseDecisions(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlanningEvaluationDecision.DecisionProposal> out = new ArrayList<>();
        for (JsonNode child : node) {
            if (!child.isObject()) {
                continue;
            }
            String decision = blankToEmpty(child.path("decision").asText(""));
            if (decision.isBlank()) {
                continue;
            }
            out.add(new PlanningEvaluationDecision.DecisionProposal(
                    decision,
                    blankToEmpty(child.path("rationale").asText("")),
                    normalizeDecisionStatus(child.path("status").asText(""))));
        }
        return out;
    }

    private static String buildUserPayload(
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Map<String, Object> state,
            EvaluationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase_context", context != null ? blankToEmpty(context.phaseContext()) : "");
        payload.put("repo_grounding_summary", context != null ? blankToEmpty(context.repoGroundingSummary()) : "");
        payload.put("repo_evidence_json", context != null ? blankToEmpty(context.repoEvidenceJson()) : "");
        payload.put("depth_ok", context == null || context.depthOk());
        payload.put("depth_reason", context != null ? blankToEmpty(context.depthReason()) : "");
        payload.put("structured_parse_failed", context != null && context.structuredParseFailed());
        payload.put("prior_clarification_attempts", context != null ? Math.max(0, context.priorClarificationAttempts()) : 0);
        payload.put("required_artifacts", requiredArtifacts(profile));
        payload.put("plan", planSnapshot(plan));
        if (state != null) {
            payload.put("planning_repo_evidence_json", blankToEmpty(asString(state.get("planningRepoEvidenceJson"))));
        }
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static List<Map<String, Object>> requiredArtifacts(WorkProfileDefinition profile) {
        if (profile == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ArtifactDefinition artifact : profile.getArtifactsById().values()) {
            Map<String, Object> artifactMap = new LinkedHashMap<>();
            artifactMap.put("artifactId", artifact.getArtifactId());
            artifactMap.put("requiredForApproval", artifact.isRequiredForApproval());
            List<Map<String, Object>> sections = new ArrayList<>();
            for (SectionDefinition section : artifact.getSections()) {
                Map<String, Object> sectionMap = new LinkedHashMap<>();
                sectionMap.put("sectionId", section.getSectionId());
                sectionMap.put("required", section.isRequired());
                sectionMap.put("repeatable", section.isRepeatable());
                List<Map<String, Object>> fields = new ArrayList<>();
                for (FieldDefinition field : section.getFields()) {
                    Map<String, Object> fieldMap = new LinkedHashMap<>();
                    fieldMap.put("fieldId", field.getFieldId());
                    fieldMap.put("required", field.isRequired());
                    fieldMap.put("type", field.getType());
                    fields.add(fieldMap);
                }
                sectionMap.put("fields", fields);
                sections.add(sectionMap);
            }
            artifactMap.put("sections", sections);
            out.add(artifactMap);
        }
        return out;
    }

    private static Map<String, Object> planSnapshot(FeaturePlanState plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contextId", plan.getContextId());
        payload.put("title", plan.getTitle());
        payload.put("initialRequest", plan.getInitialRequest());
        payload.put("repoRef", plan.getRepoRef());
        payload.put("repoWorkspaceStatus", plan.getRepoWorkspaceStatus());
        payload.put("repoLocalPath", plan.getRepoLocalPath());
        payload.put("assumptions", plan.getAssumptions().stream().map(PlanAssumption::getStatement).toList());
        payload.put(
                "issues",
                plan.getIssues().stream()
                        .map(issue -> Map.of(
                                "title", blankToEmpty(issue.getTitle()),
                                "detail", blankToEmpty(issue.getDetail()),
                                "status", blankToEmpty(issue.getStatus())))
                        .toList());
        payload.put(
                "risks",
                plan.getRisks().stream()
                        .map(risk -> Map.of(
                                "statement", blankToEmpty(risk.getStatement()),
                                "impact", blankToEmpty(risk.getImpact()),
                                "likelihood", blankToEmpty(risk.getLikelihood())))
                        .toList());
        payload.put(
                "decisions",
                plan.getDecisions().stream()
                        .map(decision -> Map.of(
                                "decision", blankToEmpty(decision.getDecision()),
                                "rationale", blankToEmpty(decision.getRationale()),
                                "status", blankToEmpty(decision.getStatus())))
                        .toList());
        payload.put("unresolvedQuestions", plan.getUnresolvedQuestions());
        payload.put("artifacts", JSON.valueToTree(plan.getArtifacts()));
        payload.put("packetDraftSummary", PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"));
        payload.put("requestExploration", PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body"));
        return payload;
    }

    private static CanonicalPlanningGap chooseAskGap(List<CanonicalPlanningGap> gaps) {
        if (gaps == null) {
            return null;
        }
        for (CanonicalPlanningGap gap : gaps) {
            if (gap != null && gap.askable() && (gap.blocking() || gap.branching())) {
                return gap;
            }
        }
        return null;
    }

    private static CanonicalPlanningGap selectTopGap(List<CanonicalPlanningGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return null;
        }
        for (CanonicalPlanningGap gap : gaps) {
            if (gap != null && gap.blocking()) {
                return gap;
            }
        }
        for (CanonicalPlanningGap gap : gaps) {
            if (gap != null && gap.branching()) {
                return gap;
            }
        }
        for (CanonicalPlanningGap gap : gaps) {
            if (gap != null) {
                return gap;
            }
        }
        return null;
    }

    private static boolean containsBlockingGap(List<CanonicalPlanningGap> gaps) {
        if (gaps == null) {
            return false;
        }
        for (CanonicalPlanningGap gap : gaps) {
            if (gap != null && gap.blocking()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDuplicateGapIds(List<CanonicalPlanningGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return false;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (CanonicalPlanningGap gap : gaps) {
            if (gap == null || gap.gapId() == null || gap.gapId().isBlank()) {
                continue;
            }
            String normalized = gap.gapId().trim();
            if (!seen.add(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static int countEligibleAskGaps(List<CanonicalPlanningGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (CanonicalPlanningGap gap : gaps) {
            if (gap != null && gap.askable() && (gap.blocking() || gap.branching())) {
                count++;
            }
        }
        return count;
    }

    private static PlanningCanonicalNextAction determineNextAction(
            boolean askUserRequired, boolean readyForPacket, List<CanonicalPlanningGap> gaps) {
        if (askUserRequired) {
            return PlanningCanonicalNextAction.ASK_USER;
        }
        if (readyForPacket) {
            return PlanningCanonicalNextAction.READY_FOR_PACKET;
        }
        if (containsBlockingGap(gaps)) {
            return PlanningCanonicalNextAction.BLOCK;
        }
        return PlanningCanonicalNextAction.BLOCK;
    }

    private static PlanningIntakeStage stageFor(PlanningCanonicalNextAction nextAction) {
        return switch (nextAction) {
            case ASK_USER -> PlanningIntakeStage.CLARIFYING;
            case READY_FOR_PACKET, CONTINUE_SYNTHESIS -> PlanningIntakeStage.DRAFTING;
            case BLOCK -> PlanningIntakeStage.FAILED;
        };
    }

    private static PlanningInteractionState interactionStateFor(PlanningCanonicalNextAction nextAction) {
        return nextAction == PlanningCanonicalNextAction.ASK_USER
                ? PlanningInteractionState.WAITING_FOR_TEXT_REPLY
                : PlanningInteractionState.NONE;
    }

    private static String determineBlockReason(PlanningCanonicalNextAction nextAction, CanonicalPlanningGap topGap) {
        if (nextAction != PlanningCanonicalNextAction.BLOCK) {
            return "";
        }
        if (topGap != null) {
            return firstNonBlank(topGap.evidenceSummary(), topGap.questionSeed());
        }
        return "Planning evaluation reported a blocked state without an askable clarification path.";
    }

    private static String decisionSummary(
            PlanningEvaluationDecision.EvaluationConfidence confidence,
            CanonicalPlanningGap topGap,
            String blockReason) {
        return firstNonBlank(
                confidence != null ? confidence.summary() : "",
                topGap != null ? topGap.questionSeed() : "",
                blockReason);
    }

    static int countKnownFacts(FeaturePlanState plan) {
        if (plan == null) {
            return 0;
        }
        int count = 0;
        if (plan.getInitialRequest() != null && !plan.getInitialRequest().isBlank()) {
            count++;
        }
        count += plan.getAssumptions().size();
        count += plan.getIssues().size();
        count += plan.getRisks().size();
        count += plan.getDecisions().size();
        count += plan.getArtifacts().size();
        return count;
    }

    private static double repoGroundingScore(FeaturePlanState plan, String repoGroundingSummary) {
        double score = 0.0;
        if (plan != null && plan.getRepoLocalPath() != null && !plan.getRepoLocalPath().isBlank()) {
            score += 0.5;
        }
        String workspace = plan != null ? blankToEmpty(plan.getRepoWorkspaceStatus()).toUpperCase(Locale.ROOT) : "";
        if (workspace.contains("MATERIALIZED") || workspace.contains("RESOLVED")) {
            score += 0.3;
        }
        String summary = blankToEmpty(repoGroundingSummary).toLowerCase(Locale.ROOT);
        if (summary.contains("inspected") || summary.contains("observed")) {
            score += 0.2;
        }
        return Math.max(0.0, Math.min(1.0, score));
    }

    private static String repoGroundingState(FeaturePlanState plan, String repoEvidenceJson) {
        if (plan == null) {
            return "NOT_MATERIALIZED";
        }
        String workspace = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
        boolean materialized = "MATERIALIZED".equalsIgnoreCase(workspace) || "RESOLVED_LOCAL".equalsIgnoreCase(workspace);
        if (!materialized) {
            return "NOT_MATERIALIZED";
        }
        if (repoEvidenceScore(repoEvidenceJson) >= 0.55d) {
            return "INSPECTED";
        }
        return "MATERIALIZED_NOT_INSPECTED";
    }

    private static double repoEvidenceScore(String repoEvidenceJson) {
        if (repoEvidenceJson == null || repoEvidenceJson.isBlank()) {
            return -1.0d;
        }
        try {
            JsonNode node = JSON.readTree(repoEvidenceJson.trim());
            return node.path("repoGroundingScore").asDouble(-1.0d);
        } catch (Exception e) {
            return -1.0d;
        }
    }

    private static String normalizeConfidenceLevel(String raw) {
        String t = blankToEmpty(raw).toLowerCase(Locale.ROOT);
        return switch (t) {
            case "high" -> "high";
            case "medium" -> "medium";
            default -> "low";
        };
    }

    private static String normalizeSeverity(String raw) {
        String t = blankToEmpty(raw).toUpperCase(Locale.ROOT);
        return switch (t) {
            case PlanGovernanceSeverity.HIGH -> PlanGovernanceSeverity.HIGH;
            case PlanGovernanceSeverity.LOW -> PlanGovernanceSeverity.LOW;
            default -> PlanGovernanceSeverity.MEDIUM;
        };
    }

    private static String normalizeDecisionStatus(String raw) {
        String t = blankToEmpty(raw).toUpperCase(Locale.ROOT);
        return switch (t) {
            case PlanRiskDecisionStatus.RESOLVED -> PlanRiskDecisionStatus.RESOLVED;
            case PlanRiskDecisionStatus.WAIVED -> PlanRiskDecisionStatus.WAIVED;
            default -> PlanRiskDecisionStatus.OPEN;
        };
    }

    private static String stableId(String prefix, String text) {
        return prefix + "-" + Integer.toHexString(blankToEmpty(text).hashCode());
    }

    private static String normalize(String text) {
        return blankToEmpty(text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String blankToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
