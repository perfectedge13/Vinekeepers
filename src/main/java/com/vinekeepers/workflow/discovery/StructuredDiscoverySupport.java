package com.vinekeepers.workflow.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.workflow.planning.PlanningPromptFormatter;
import com.vinekeepers.state.planning.DiscoveryAgenda;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.DiscoveryQuestion;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanSectionKey;
import com.vinekeepers.state.planning.PlanSectionStatus;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects structured discovery gaps, builds agendas, and projects plan section status from profile artifacts.
 * Intake blocking clarification uses {@link ClarificationPromptQualityGate#sanitizeBlockingQuestion} so only one concrete
 * question reaches Discord.
 */
public final class StructuredDiscoverySupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<DiscoveryGap>> GAP_LIST = new TypeReference<>() {};

    private StructuredDiscoverySupport() {}

    public static List<DiscoveryGap> collectGaps(FeaturePlanState plan, WorkProfileDefinition profile) {
        return collectGaps(plan, profile, false);
    }

    /**
     * When {@code workspaceBlockersOnly} is true, emit only repo workspace BLOCKER gaps (no required-field gaps).
     * Used before the first successful autonomous planning pass so drafting can fill profile fields first.
     */
    public static List<DiscoveryGap> collectGaps(
            FeaturePlanState plan, WorkProfileDefinition profile, boolean workspaceBlockersOnly) {
        List<DiscoveryGap> gaps = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger(1);
        if (plan == null || profile == null) {
            return gaps;
        }
        if (!workspaceBlockersOnly) {
            collectRequiredFieldGaps(plan, profile, gaps, seq);
        }
        collectWorkspaceGap(plan, gaps, seq);
        // Assumptions/issues are surfaced via classify_assumption_or_issue and legacy plan lists;
        // they are not emitted as discovery gaps here (no resolution signal — would loop forever).
        return gaps;
    }

    private static void collectRequiredFieldGaps(
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            List<DiscoveryGap> gaps,
            AtomicInteger seq) {
        for (ArtifactDefinition art : profile.getArtifactsById().values()) {
            ArtifactState artState = plan.getArtifacts().get(art.getArtifactId());
            for (SectionDefinition sec : art.getSections()) {
                SectionState secState =
                        artState != null ? artState.getSectionsById().get(sec.getSectionId()) : null;
                if (sec.isRepeatable()) {
                    if (sec.isRequired() && (secState == null || secState.getEntries().isEmpty())) {
                        gaps.add(requiredGap(
                                seq,
                                art.getArtifactId(),
                                sec.getSectionId(),
                                "",
                                "Repeatable section empty: " + art.getArtifactId() + "." + sec.getSectionId(),
                                sec.isRequired() ? "HIGH" : "LOW",
                                PlanningPromptFormatter.repeatableSectionEmptyPrompt(art, sec)));
                    }
                    if (secState != null && !secState.getEntries().isEmpty()) {
                        Set<String> missingFieldIds = new LinkedHashSet<>();
                        for (int i = 0; i < secState.getEntries().size(); i++) {
                            Map<String, Object> row = secState.getEntries().get(i);
                            for (FieldDefinition f : sec.getFields()) {
                                if (f.isRequired() && isMissing(row.get(f.getFieldId()))) {
                                    missingFieldIds.add(f.getFieldId());
                                }
                            }
                        }
                        for (String fieldId : missingFieldIds) {
                            FieldDefinition f = fieldById(sec, fieldId);
                            if (f == null || !f.isRequired()) {
                                continue;
                            }
                            int firstRow = firstRowMissingField(secState, fieldId);
                            gaps.add(requiredGap(
                                    seq,
                                    art.getArtifactId(),
                                    sec.getSectionId(),
                                    fieldId,
                                    "Missing required field "
                                            + art.getArtifactId()
                                            + "."
                                            + sec.getSectionId()
                                            + "[*]."
                                            + fieldId,
                                    "HIGH",
                                    PlanningPromptFormatter.requiredFieldPrompt(art, sec, f, firstRow)));
                        }
                    }
                } else {
                    if (sec.isRequired() && secState == null) {
                        gaps.add(requiredGap(
                                seq,
                                art.getArtifactId(),
                                sec.getSectionId(),
                                "",
                                "Required section missing: " + art.getArtifactId() + "." + sec.getSectionId(),
                                "HIGH",
                                PlanningPromptFormatter.requiredSectionMissingPrompt(art, sec)));
                    }
                    Map<String, Object> values = secState != null ? secState.getValues() : Map.of();
                    for (FieldDefinition f : sec.getFields()) {
                        if (f.isRequired() && isMissing(values.get(f.getFieldId()))) {
                            gaps.add(requiredGap(
                                    seq,
                                    art.getArtifactId(),
                                    sec.getSectionId(),
                                    f.getFieldId(),
                                    "Missing required field "
                                            + art.getArtifactId()
                                            + "."
                                            + sec.getSectionId()
                                            + "."
                                            + f.getFieldId(),
                                    severityForArtifact(art),
                                    PlanningPromptFormatter.requiredFieldPrompt(art, sec, f, -1)));
                        }
                    }
                }
            }
        }
    }

    private static String severityForArtifact(ArtifactDefinition art) {
        if (art.isRequiredForApproval()) {
            return "HIGH";
        }
        return "LOW";
    }

    private static DiscoveryGap requiredGap(
            AtomicInteger seq,
            String artifactId,
            String sectionId,
            String fieldId,
            String reason,
            String severity,
            String userFacingDetail) {
        return new DiscoveryGap(
                "gap-req-" + seq.getAndIncrement(),
                "REQUIRED_FIELD",
                artifactId,
                sectionId,
                fieldId,
                reason,
                severity,
                "OPEN",
                "profile",
                userFacingDetail != null ? userFacingDetail : "");
    }

    private static void collectWorkspaceGap(FeaturePlanState plan, List<DiscoveryGap> gaps, AtomicInteger seq) {
        String raw = plan.getRepoWorkspaceStatus();
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            RepoWorkspaceStatus s = RepoWorkspaceStatus.valueOf(raw.trim());
            if (s == RepoWorkspaceStatus.FAILED || s == RepoWorkspaceStatus.UNAVAILABLE) {
                String wsReason = "Repo workspace status is "
                        + s
                        + (plan.getRepoAccessNotes() != null && !plan.getRepoAccessNotes().isBlank()
                                ? ": " + plan.getRepoAccessNotes()
                                : "");
                gaps.add(new DiscoveryGap(
                        "gap-ws-" + seq.getAndIncrement(),
                        "WORKSPACE",
                        "",
                        "",
                        "",
                        wsReason,
                        "BLOCKER",
                        "OPEN",
                        "repo.workspace",
                        "We could not prepare a local workspace for this repository (" + s + "). "
                                + (plan.getRepoAccessNotes() != null && !plan.getRepoAccessNotes().isBlank()
                                        ? plan.getRepoAccessNotes() + " "
                                        : "")
                                + "Reply with access notes, a local checkout path, or how you want to proceed."));
            }
        } catch (IllegalArgumentException ignored) {
            // unknown status string — ignore
        }
    }

    private static boolean isMissing(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private static FieldDefinition fieldById(SectionDefinition sec, String fieldId) {
        if (sec == null || fieldId == null || fieldId.isBlank()) {
            return null;
        }
        for (FieldDefinition f : sec.getFields()) {
            if (fieldId.equals(f.getFieldId())) {
                return f;
            }
        }
        return null;
    }

    private static int firstRowMissingField(SectionState secState, String fieldId) {
        for (int i = 0; i < secState.getEntries().size(); i++) {
            Map<String, Object> row = secState.getEntries().get(i);
            if (isMissing(row.get(fieldId))) {
                return i;
            }
        }
        return 0;
    }

    public static Map<String, Object> spreadFromGaps(List<DiscoveryGap> gaps) throws JsonProcessingException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discoveryGapsJson", JSON.writeValueAsString(gaps));
        m.put("discoveryHasOpenGaps", gaps.isEmpty() ? "false" : "true");
        m.put("discoveryBlockingIssueMode", gaps.stream().anyMatch(g -> "BLOCKER".equalsIgnoreCase(g.getSeverity()))
                ? "true"
                : "false");
        return m;
    }

    public static List<DiscoveryGap> parseGapsJson(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.readValue(json, GAP_LIST);
    }

    public static Map<String, Object> buildAgendaSpread(String gapsJson) throws JsonProcessingException {
        List<DiscoveryGap> gaps = parseGapsJson(gapsJson);
        List<DiscoveryGap> sorted = new ArrayList<>(gaps);
        sorted.sort(GAP_COMPARATOR);
        List<DiscoveryQuestion> questions = new ArrayList<>();
        AtomicInteger q = new AtomicInteger(1);
        if (!sorted.isEmpty()) {
            questions.add(toQuestion(sorted.get(0), q.getAndIncrement()));
        }
        DiscoveryAgenda agenda =
                new DiscoveryAgenda(questions, gaps.size(), questions.isEmpty() ? "" : questions.get(0).getQuestionId(),
                        Instant.now().toString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discoveryAgendaJson", JSON.writeValueAsString(agenda));
        DiscoveryQuestion first = agenda.firstQuestion();
        if (first == null) {
            m.put("discoveryCurrentQuestionPrompt", "");
            m.put("discoveryCurrentQuestionId", "");
            m.put("discoveryApplyKind", "NONE");
            m.put("discoveryApplyArtifactId", "");
            m.put("discoveryApplySectionId", "");
            m.put("discoveryApplyFieldId", "");
            m.put("discoveryApplyMode", "replace");
        } else {
            m.put("discoveryCurrentQuestionPrompt", first.getPrompt());
            m.put("discoveryCurrentQuestionId", first.getQuestionId());
            m.put("discoveryApplyKind", first.getApplyKind());
            m.put("discoveryApplyArtifactId", first.getArtifactId());
            m.put("discoveryApplySectionId", first.getSectionId());
            m.put("discoveryApplyFieldId", first.getFieldId());
            m.put("discoveryApplyMode", first.getApplyMode());
        }
        m.putIfAbsent("discoveryBundledApplyJson", "[]");
        return m;
    }

    /**
     * Intake planning clarification: at most one question, only {@code BLOCKER}/{@code HIGH} profile/workspace gaps,
     * never bundled menus. Prompts are canonical {@link DiscoveryGap#getUserFacingDetail()} (or gated synthesis).
     */
    public static Map<String, Object> buildIntakeBlockingClarificationSpread(String gapsJson) throws JsonProcessingException {
        List<DiscoveryGap> gaps = parseGapsJson(gapsJson);
        List<DiscoveryGap> blocking = new ArrayList<>();
        for (DiscoveryGap g : gaps) {
            if (!isBlockingDiscoverySeverity(g.getSeverity())) {
                continue;
            }
            String k = g.getKind() != null ? g.getKind() : "";
            if (!"REQUIRED_FIELD".equalsIgnoreCase(k) && !"WORKSPACE".equalsIgnoreCase(k)) {
                continue;
            }
            blocking.add(g);
        }
        blocking.sort(GAP_COMPARATOR);
        if (blocking.isEmpty()) {
            return emptyDiscoveryPromptSpread();
        }
        DiscoveryGap chosen = blocking.get(0);
        String raw = discoveryPromptBody(chosen);
        String prompt = ClarificationPromptQualityGate.sanitizeBlockingQuestion(raw, chosen);
        if (prompt.isBlank()) {
            return emptyDiscoveryPromptSpread();
        }
        DiscoveryQuestion first = toQuestionWithPrompt(chosen, prompt, 1);
        List<DiscoveryQuestion> questions = List.of(first);
        DiscoveryAgenda agenda = new DiscoveryAgenda(questions, gaps.size(), first.getQuestionId(), Instant.now().toString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discoveryAgendaJson", JSON.writeValueAsString(agenda));
        m.put("discoveryCurrentQuestionPrompt", first.getPrompt());
        m.put("discoveryCurrentQuestionId", first.getQuestionId());
        m.put("discoveryApplyKind", first.getApplyKind());
        m.put("discoveryApplyArtifactId", first.getArtifactId());
        m.put("discoveryApplySectionId", first.getSectionId());
        m.put("discoveryApplyFieldId", first.getFieldId());
        m.put("discoveryApplyMode", first.getApplyMode());
        m.put("discoveryBundledApplyJson", "[]");
        return m;
    }

    private static Map<String, Object> emptyDiscoveryPromptSpread() throws JsonProcessingException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discoveryAgendaJson", JSON.writeValueAsString(new DiscoveryAgenda(List.of(), 0, "", Instant.now().toString())));
        m.put("discoveryCurrentQuestionPrompt", "");
        m.put("discoveryCurrentQuestionId", "");
        m.put("discoveryApplyKind", "NONE");
        m.put("discoveryApplyArtifactId", "");
        m.put("discoveryApplySectionId", "");
        m.put("discoveryApplyFieldId", "");
        m.put("discoveryApplyMode", "replace");
        m.put("discoveryBundledApplyJson", "[]");
        return m;
    }

    private static boolean isBlockingDiscoverySeverity(String severity) {
        String s = severity != null ? severity.toUpperCase() : "MEDIUM";
        return "BLOCKER".equals(s) || "HIGH".equals(s);
    }

    private static DiscoveryQuestion toQuestionWithPrompt(DiscoveryGap g, String prompt, int index) {
        String qid = "q-" + g.getGapId() + "-" + index;
        return switch (g.getKind() != null ? g.getKind() : "") {
            case "REQUIRED_FIELD" -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    g.getSeverity(),
                    prompt,
                    "REQUIRED_FIELD",
                    g.getArtifactId(),
                    g.getSectionId(),
                    g.getFieldId(),
                    "replace");
            case "WORKSPACE" -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    g.getSeverity(),
                    prompt,
                    "WORKSPACE",
                    "",
                    "",
                    "",
                    "replace");
            default -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    g.getSeverity(),
                    prompt,
                    "NONE",
                    "",
                    "",
                    "",
                    "replace");
        };
    }

    /** Exposed for insight/bundled discovery ordering. */
    public static final Comparator<DiscoveryGap> GAP_COMPARATOR =
            Comparator.comparingInt(StructuredDiscoverySupport::severityRank)
                    .thenComparing(DiscoveryGap::getGapId);

    private static int severityRank(DiscoveryGap g) {
        return switch (g.getSeverity() != null ? g.getSeverity().toUpperCase() : "MEDIUM") {
            case "BLOCKER" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }

    private static DiscoveryQuestion toQuestion(DiscoveryGap g, int index) {
        String qid = "q-" + g.getGapId() + "-" + index;
        return switch (g.getKind() != null ? g.getKind() : "") {
            case "REQUIRED_FIELD" -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    g.getSeverity(),
                    discoveryPromptBody(g),
                    "REQUIRED_FIELD",
                    g.getArtifactId(),
                    g.getSectionId(),
                    g.getFieldId(),
                    "replace");
            case "WORKSPACE" -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    g.getSeverity(),
                    discoveryPromptBody(g),
                    "WORKSPACE",
                    "",
                    "",
                    "",
                    "replace");
            case "OPEN_ISSUE" -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    g.getSeverity(),
                    "Discovery — " + g.getReason(),
                    "NONE",
                    "",
                    "",
                    "",
                    "replace");
            case "OPEN_ASSUMPTION" -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    g.getSeverity(),
                    "Discovery — " + g.getReason(),
                    "NONE",
                    "",
                    "",
                    "",
                    "replace");
            default -> new DiscoveryQuestion(
                    qid,
                    g.getGapId(),
                    "orchestrator",
                    "MEDIUM",
                    discoveryPromptBody(g),
                    "NONE",
                    "",
                    "",
                    "",
                    "replace");
        };
    }

    private static String discoveryPromptBody(DiscoveryGap g) {
        String detail = g.getUserFacingDetail();
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        return "Discovery — " + g.getReason();
    }

    public static FeaturePlanState projectSectionStatuses(FeaturePlanState plan, WorkProfileDefinition profile) {
        if (plan == null || profile == null) {
            return plan;
        }
        FeaturePlanState p = plan;
        if (fieldPresent(plan, "requirements_spec", "narrative", "feature_summary")) {
            p = p.withSectionStatus(PlanSectionKey.REQUIREMENTS, PlanSectionStatus.DRAFT);
        }
        if (fieldPresent(plan, "overall_plan", "outline", "plan_body")) {
            p = p.withSectionStatus(PlanSectionKey.SOLUTION_OUTLINE, PlanSectionStatus.DRAFT);
        }
        if (fieldPresent(plan, "validation_plan", "checks", "validation_notes")) {
            p = p.withSectionStatus(PlanSectionKey.VALIDATION_NOTES, PlanSectionStatus.DRAFT);
        }
        if (fieldPresent(plan, "project_context", "context", "context_summary")) {
            p = p.withSectionStatus(PlanSectionKey.PROJECT_CONTEXT, PlanSectionStatus.DRAFT);
        }
        if (!p.getIssues().isEmpty()) {
            p = p.withSectionStatus(PlanSectionKey.ISSUES, PlanSectionStatus.DRAFT);
            p = p.withSectionStatus(PlanSectionKey.SOLUTION_OUTLINE, PlanSectionStatus.DRAFT);
            p = p.withSectionStatus(PlanSectionKey.REQUIREMENTS, PlanSectionStatus.DRAFT);
        }
        if (!p.getAssumptions().isEmpty()) {
            p = p.withSectionStatus(PlanSectionKey.ASSUMPTIONS, PlanSectionStatus.DRAFT);
        }
        return p;
    }

    private static boolean fieldPresent(FeaturePlanState plan, String artifactId, String sectionId, String fieldId) {
        ArtifactState art = plan.getArtifacts().get(artifactId);
        if (art == null) {
            return false;
        }
        SectionState sec = art.getSectionsById().get(sectionId);
        if (sec == null) {
            return false;
        }
        Object v = sec.getValues().get(fieldId);
        if (v instanceof String s) {
            return !s.isBlank();
        }
        return v != null;
    }
}
