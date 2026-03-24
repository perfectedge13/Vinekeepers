package com.vinekeepers.state.planning;

import com.vinekeepers.profile.ArtifactState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical structured planning state for a feature room. Source of truth for plan sections;
 * distinct from {@link FeatureRoomState} (room/runtime metadata).
 */
public final class FeaturePlanState {

    private final String contextId;
    private final String featureId;
    private final String featureSlug;
    private final String roomChannelId;
    private final String intakeThreadId;
    private final String repoRef;
    private final String title;
    private final String initialRequest;
    private final String planStatus;
    private final List<RequirementEntry> requirements;
    private final List<PlanAssumption> assumptions;
    private final List<PlanIssue> issues;
    private final List<ValidationEntry> validationNotes;
    private final SolutionOutline solutionOutline;
    private final TraceabilityPlaceholder traceability;
    private final ProjectContextSummary projectContext;
    private final Map<PlanSectionKey, PlanSectionStatus> sectionStatuses;
    private final PlanConfidence planConfidence;
    private final PlanApproval planApproval;
    private final PlanCritiqueSnapshot planCritiqueSnapshot;
    private final String repoWorkspaceId;
    private final String repoWorkspaceStatus;
    private final String repoLocalPath;
    private final String repoAccessNotes;
    private final String profileId;
    private final Map<String, ArtifactState> artifacts;
    private final List<PlanRisk> risks;
    private final List<PlanDecision> decisions;
    private final List<String> unresolvedQuestions;
    private final String critiqueLifecycleStatus;
    private final Instant packetPostedAt;
    private final String packetMessageRef;
    private final String packetPostedFingerprint;
    private final int packetPostedChunkCount;
    private final PlanningIntakeStage planningIntakeStage;
    private final Instant planningIntakeStageEnteredAt;
    private final int intakeKickoffPostedVersion;
    private final String intakeKickoffPostedFingerprint;
    private final String planningOrchestrationFailureReason;
    /** Canonical flag: at least one autonomous planning room cycle completed successfully for this plan. */
    private final boolean autonomousPlanningPassCompleted;
    /** Count of coordinator clarification questions surfaced (canonical engine). */
    private final int clarificationTurnsCompleted;
    /** Short audit lines for clarification engine outcomes (budget, assume, defer). */
    private final List<String> clarificationOutcomeHistory;
    /** Serialized coordinator clarification ledger JSON (canonical engine). */
    private final String clarificationCoordinatorLedgerJson;
    /** Durable coordinator bot id for routing when {@link FeatureRoomState} is evicted. */
    private final String coordinatorConfiguredBotId;
    private final String planningFailureCategory;
    private final String planningFailurePhase;
    private final boolean planningRecoverableDraftAvailable;
    private final String planningLastRecoveryHint;
    private final String planningCanonicalDecisionJson;
    private final String planningLastPostedPacketDecisionId;
    private final String planningLastAskedQuestionDecisionId;
    private final String planningLastMaterialStateChangeFingerprint;
    /** JSON map: gapId → surfaced ask count (canonical clarification). */
    private final String planningGapAskCountsJson;
    /** JSON snapshot of {@link com.vinekeepers.workflow.planning.PlanningConfidenceBreakdown} factors. */
    private final String planningConfidenceBreakdownJson;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Legacy constructor shape; intake orchestration fields default to {@link PlanningIntakeStage#GATHERING_CONTEXT}.
     */
    public FeaturePlanState(
            String contextId,
            String featureId,
            String featureSlug,
            String roomChannelId,
            String intakeThreadId,
            String repoRef,
            String title,
            String initialRequest,
            String planStatus,
            List<RequirementEntry> requirements,
            List<PlanAssumption> assumptions,
            List<PlanIssue> issues,
            List<ValidationEntry> validationNotes,
            SolutionOutline solutionOutline,
            TraceabilityPlaceholder traceability,
            ProjectContextSummary projectContext,
            Map<PlanSectionKey, PlanSectionStatus> sectionStatuses,
            PlanConfidence planConfidence,
            PlanApproval planApproval,
            PlanCritiqueSnapshot planCritiqueSnapshot,
            String repoWorkspaceId,
            String repoWorkspaceStatus,
            String repoLocalPath,
            String repoAccessNotes,
            String profileId,
            Map<String, ArtifactState> artifacts,
            List<PlanRisk> risks,
            List<PlanDecision> decisions,
            List<String> unresolvedQuestions,
            String critiqueLifecycleStatus,
            Instant packetPostedAt,
            String packetMessageRef,
            String packetPostedFingerprint,
            Integer packetPostedChunkCount,
            Instant createdAt,
            Instant updatedAt) {
        this(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                updatedAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Boolean.FALSE,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public FeaturePlanState(
            String contextId,
            String featureId,
            String featureSlug,
            String roomChannelId,
            String intakeThreadId,
            String repoRef,
            String title,
            String initialRequest,
            String planStatus,
            List<RequirementEntry> requirements,
            List<PlanAssumption> assumptions,
            List<PlanIssue> issues,
            List<ValidationEntry> validationNotes,
            SolutionOutline solutionOutline,
            TraceabilityPlaceholder traceability,
            ProjectContextSummary projectContext,
            Map<PlanSectionKey, PlanSectionStatus> sectionStatuses,
            PlanConfidence planConfidence,
            PlanApproval planApproval,
            PlanCritiqueSnapshot planCritiqueSnapshot,
            String repoWorkspaceId,
            String repoWorkspaceStatus,
            String repoLocalPath,
            String repoAccessNotes,
            String profileId,
            Map<String, ArtifactState> artifacts,
            List<PlanRisk> risks,
            List<PlanDecision> decisions,
            List<String> unresolvedQuestions,
            String critiqueLifecycleStatus,
            Instant packetPostedAt,
            String packetMessageRef,
            String packetPostedFingerprint,
            Integer packetPostedChunkCount,
            Instant createdAt,
            Instant updatedAt,
            PlanningIntakeStage planningIntakeStage,
            Instant planningIntakeStageEnteredAt,
            Integer intakeKickoffPostedVersion,
            String intakeKickoffPostedFingerprint,
            String planningOrchestrationFailureReason,
            Boolean autonomousPlanningPassCompleted,
            Integer clarificationTurnsCompleted,
            List<String> clarificationOutcomeHistory,
            String clarificationCoordinatorLedgerJson,
            String coordinatorConfiguredBotId,
            String planningFailureCategory,
            String planningFailurePhase,
            String planningLastRecoveryHint,
            Boolean planningRecoverableDraftAvailable) {
        this(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                updatedAt,
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public FeaturePlanState(
            String contextId,
            String featureId,
            String featureSlug,
            String roomChannelId,
            String intakeThreadId,
            String repoRef,
            String title,
            String initialRequest,
            String planStatus,
            List<RequirementEntry> requirements,
            List<PlanAssumption> assumptions,
            List<PlanIssue> issues,
            List<ValidationEntry> validationNotes,
            SolutionOutline solutionOutline,
            TraceabilityPlaceholder traceability,
            ProjectContextSummary projectContext,
            Map<PlanSectionKey, PlanSectionStatus> sectionStatuses,
            PlanConfidence planConfidence,
            PlanApproval planApproval,
            PlanCritiqueSnapshot planCritiqueSnapshot,
            String repoWorkspaceId,
            String repoWorkspaceStatus,
            String repoLocalPath,
            String repoAccessNotes,
            String profileId,
            Map<String, ArtifactState> artifacts,
            List<PlanRisk> risks,
            List<PlanDecision> decisions,
            List<String> unresolvedQuestions,
            String critiqueLifecycleStatus,
            Instant packetPostedAt,
            String packetMessageRef,
            String packetPostedFingerprint,
            Integer packetPostedChunkCount,
            Instant createdAt,
            Instant updatedAt,
            PlanningIntakeStage planningIntakeStage,
            Instant planningIntakeStageEnteredAt,
            Integer intakeKickoffPostedVersion,
            String intakeKickoffPostedFingerprint,
            String planningOrchestrationFailureReason,
            Boolean autonomousPlanningPassCompleted,
            Integer clarificationTurnsCompleted,
            List<String> clarificationOutcomeHistory,
            String clarificationCoordinatorLedgerJson,
            String coordinatorConfiguredBotId,
            String planningFailureCategory,
            String planningFailurePhase,
            String planningLastRecoveryHint,
            Boolean planningRecoverableDraftAvailable,
            String planningCanonicalDecisionJson,
            String planningLastPostedPacketDecisionId,
            String planningLastAskedQuestionDecisionId,
            String planningLastMaterialStateChangeFingerprint,
            String planningGapAskCountsJson,
            String planningConfidenceBreakdownJson) {
        this.contextId = Objects.requireNonNull(contextId, "contextId");
        this.featureId = featureId;
        this.featureSlug = featureSlug;
        this.roomChannelId = Objects.requireNonNull(roomChannelId, "roomChannelId");
        this.intakeThreadId = intakeThreadId;
        this.repoRef = repoRef;
        this.title = title;
        this.initialRequest = initialRequest;
        this.planStatus = planStatus != null && !planStatus.isBlank() ? planStatus : "PLANNING";
        this.requirements = requirements != null ? List.copyOf(requirements) : List.of();
        this.assumptions = assumptions != null ? List.copyOf(assumptions) : List.of();
        this.issues = issues != null ? List.copyOf(issues) : List.of();
        this.validationNotes = validationNotes != null ? List.copyOf(validationNotes) : List.of();
        this.solutionOutline = solutionOutline;
        this.traceability = traceability != null ? traceability : new TraceabilityPlaceholder("");
        this.projectContext = projectContext != null ? projectContext : new ProjectContextSummary("");
        this.sectionStatuses = copySectionMap(sectionStatuses);
        this.planConfidence = planConfidence;
        this.planApproval = planApproval;
        this.planCritiqueSnapshot = planCritiqueSnapshot;
        this.repoWorkspaceId = repoWorkspaceId;
        this.repoWorkspaceStatus = repoWorkspaceStatus;
        this.repoLocalPath = repoLocalPath;
        this.repoAccessNotes = repoAccessNotes;
        this.profileId = profileId;
        this.artifacts = copyArtifacts(artifacts);
        this.risks = risks != null ? List.copyOf(risks) : List.of();
        this.decisions = decisions != null ? List.copyOf(decisions) : List.of();
        this.unresolvedQuestions = unresolvedQuestions != null ? List.copyOf(unresolvedQuestions) : List.of();
        this.critiqueLifecycleStatus =
                critiqueLifecycleStatus != null && !critiqueLifecycleStatus.isBlank()
                        ? critiqueLifecycleStatus
                        : PlanCritiqueLifecycleStatus.NONE;
        this.packetPostedAt = packetPostedAt;
        this.packetMessageRef = packetMessageRef != null ? packetMessageRef : "";
        this.packetPostedFingerprint = packetPostedFingerprint != null ? packetPostedFingerprint : "";
        this.packetPostedChunkCount = packetPostedChunkCount != null ? packetPostedChunkCount : 0;
        Instant effectiveCreated = createdAt != null ? createdAt : Instant.now();
        this.planningIntakeStage =
                planningIntakeStage != null ? planningIntakeStage : PlanningIntakeStage.GATHERING_CONTEXT;
        this.planningIntakeStageEnteredAt =
                planningIntakeStageEnteredAt != null ? planningIntakeStageEnteredAt : effectiveCreated;
        this.intakeKickoffPostedVersion = intakeKickoffPostedVersion != null ? intakeKickoffPostedVersion : 0;
        this.intakeKickoffPostedFingerprint =
                intakeKickoffPostedFingerprint != null ? intakeKickoffPostedFingerprint : "";
        this.planningOrchestrationFailureReason =
                planningOrchestrationFailureReason != null ? planningOrchestrationFailureReason : "";
        this.autonomousPlanningPassCompleted = Boolean.TRUE.equals(autonomousPlanningPassCompleted);
        this.clarificationTurnsCompleted =
                clarificationTurnsCompleted != null && clarificationTurnsCompleted >= 0 ? clarificationTurnsCompleted : 0;
        this.clarificationOutcomeHistory =
                clarificationOutcomeHistory != null ? List.copyOf(clarificationOutcomeHistory) : List.of();
        this.clarificationCoordinatorLedgerJson =
                clarificationCoordinatorLedgerJson != null && !clarificationCoordinatorLedgerJson.isBlank()
                        ? clarificationCoordinatorLedgerJson
                        : "{}";
        this.coordinatorConfiguredBotId =
                coordinatorConfiguredBotId != null && !coordinatorConfiguredBotId.isBlank()
                        ? coordinatorConfiguredBotId.trim()
                        : "";
        this.planningFailureCategory =
                planningFailureCategory != null && !planningFailureCategory.isBlank()
                        ? planningFailureCategory.trim()
                        : "";
        this.planningFailurePhase = planningFailurePhase != null ? planningFailurePhase.trim() : "";
        this.planningLastRecoveryHint =
                planningLastRecoveryHint != null && !planningLastRecoveryHint.isBlank()
                        ? planningLastRecoveryHint.trim()
                        : "";
        this.planningRecoverableDraftAvailable = Boolean.TRUE.equals(planningRecoverableDraftAvailable);
        this.planningCanonicalDecisionJson =
                planningCanonicalDecisionJson != null && !planningCanonicalDecisionJson.isBlank()
                        ? planningCanonicalDecisionJson
                        : "{}";
        this.planningLastPostedPacketDecisionId =
                planningLastPostedPacketDecisionId != null ? planningLastPostedPacketDecisionId.trim() : "";
        this.planningLastAskedQuestionDecisionId =
                planningLastAskedQuestionDecisionId != null ? planningLastAskedQuestionDecisionId.trim() : "";
        this.planningLastMaterialStateChangeFingerprint =
                planningLastMaterialStateChangeFingerprint != null
                        ? planningLastMaterialStateChangeFingerprint.trim()
                        : "";
        this.planningGapAskCountsJson = normalizeJsonObject(planningGapAskCountsJson);
        this.planningConfidenceBreakdownJson = normalizeJsonObject(planningConfidenceBreakdownJson);
        this.createdAt = effectiveCreated;
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    private static String normalizeJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        return raw.trim();
    }

    private static Map<String, ArtifactState> copyArtifacts(Map<String, ArtifactState> in) {
        if (in == null || in.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(in));
    }

    private static Map<PlanSectionKey, PlanSectionStatus> copySectionMap(Map<PlanSectionKey, PlanSectionStatus> in) {
        EnumMap<PlanSectionKey, PlanSectionStatus> m = new EnumMap<>(PlanSectionKey.class);
        if (in != null) {
            m.putAll(in);
        }
        for (PlanSectionKey k : PlanSectionKey.values()) {
            m.putIfAbsent(k, PlanSectionStatus.EMPTY);
        }
        return Map.copyOf(m);
    }

    public static Map<PlanSectionKey, PlanSectionStatus> initialSectionStatuses() {
        EnumMap<PlanSectionKey, PlanSectionStatus> m = new EnumMap<>(PlanSectionKey.class);
        for (PlanSectionKey k : PlanSectionKey.values()) {
            m.put(k, PlanSectionStatus.EMPTY);
        }
        return Map.copyOf(m);
    }

    public FeaturePlanState withAppendedRequirement(RequirementEntry e) {
        List<RequirementEntry> n = new ArrayList<>(requirements);
        n.add(Objects.requireNonNull(e));
        EnumMap<PlanSectionKey, PlanSectionStatus> sm = new EnumMap<>(sectionStatuses);
        sm.put(PlanSectionKey.REQUIREMENTS, PlanSectionStatus.DRAFT);
        return copy(sm, n, assumptions, issues, validationNotes, solutionOutline, Instant.now());
    }

    public FeaturePlanState withAppendedAssumption(PlanAssumption e) {
        List<PlanAssumption> n = new ArrayList<>(assumptions);
        n.add(Objects.requireNonNull(e));
        EnumMap<PlanSectionKey, PlanSectionStatus> sm = new EnumMap<>(sectionStatuses);
        sm.put(PlanSectionKey.ASSUMPTIONS, PlanSectionStatus.DRAFT);
        return copy(sm, requirements, n, issues, validationNotes, solutionOutline, Instant.now());
    }

    public FeaturePlanState withAppendedIssue(PlanIssue e) {
        List<PlanIssue> n = new ArrayList<>(issues);
        n.add(Objects.requireNonNull(e));
        EnumMap<PlanSectionKey, PlanSectionStatus> sm = new EnumMap<>(sectionStatuses);
        sm.put(PlanSectionKey.ISSUES, PlanSectionStatus.DRAFT);
        return copy(sm, requirements, assumptions, n, validationNotes, solutionOutline, Instant.now());
    }

    public FeaturePlanState withAppendedValidationNote(ValidationEntry e) {
        List<ValidationEntry> n = new ArrayList<>(validationNotes);
        n.add(Objects.requireNonNull(e));
        EnumMap<PlanSectionKey, PlanSectionStatus> sm = new EnumMap<>(sectionStatuses);
        sm.put(PlanSectionKey.VALIDATION_NOTES, PlanSectionStatus.DRAFT);
        return copy(sm, requirements, assumptions, issues, n, solutionOutline, Instant.now());
    }

    public FeaturePlanState withSectionStatus(PlanSectionKey key, PlanSectionStatus status) {
        EnumMap<PlanSectionKey, PlanSectionStatus> sm = new EnumMap<>(sectionStatuses);
        sm.put(Objects.requireNonNull(key), Objects.requireNonNull(status));
        return copy(sm, requirements, assumptions, issues, validationNotes, solutionOutline, Instant.now());
    }

    public FeaturePlanState withSolutionOutline(SolutionOutline outline) {
        EnumMap<PlanSectionKey, PlanSectionStatus> sm = new EnumMap<>(sectionStatuses);
        if (outline != null && !outline.getSummary().isBlank()) {
            sm.put(PlanSectionKey.SOLUTION_OUTLINE, PlanSectionStatus.DRAFT);
        }
        return copy(sm, requirements, assumptions, issues, validationNotes, outline, Instant.now());
    }

    public FeaturePlanState withWorkspaceLinkage(
            String workspaceId,
            String workspaceStatusName,
            String localPath,
            String accessNotes) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                workspaceId,
                workspaceStatusName,
                localPath,
                accessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                planningCanonicalDecisionJson,
                planningLastPostedPacketDecisionId,
                planningLastAskedQuestionDecisionId,
                planningLastMaterialStateChangeFingerprint,
                planningGapAskCountsJson,
                planningConfidenceBreakdownJson);
    }

    /**
     * Replace generic artifact map (e.g. after profile-scoped section upsert).
     */
    public FeaturePlanState withArtifacts(Map<String, ArtifactState> newArtifacts) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                newArtifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withPlanConfidence(PlanConfidence confidence) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                confidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withPlanApproval(PlanApproval approval) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                approval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withPlanCritiqueSnapshot(PlanCritiqueSnapshot snapshot) {
        String nextCritique =
                snapshot != null ? PlanCritiqueLifecycleStatus.COMPLETE : critiqueLifecycleStatus;
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                snapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                nextCritique,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withCritiqueLifecycleStatus(String status) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                status,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withPacketPosted(
            Instant postedAt,
            String messageRef,
            String fingerprint,
            int chunkCount) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                postedAt,
                messageRef != null ? messageRef : "",
                fingerprint != null ? fingerprint : "",
                chunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                planningCanonicalDecisionJson,
                planningLastPostedPacketDecisionId,
                planningLastAskedQuestionDecisionId,
                planningLastMaterialStateChangeFingerprint,
                planningGapAskCountsJson,
                planningConfidenceBreakdownJson);
    }

    /**
     * Replace governance lists after deterministic merge (e.g. {@link com.vinekeepers.workflow.planning.PlanGovernanceDeriver}).
     */
    public FeaturePlanState withGovernanceRecords(
            List<PlanAssumption> nextAssumptions,
            List<PlanIssue> nextIssues,
            List<PlanRisk> nextRisks,
            List<PlanDecision> nextDecisions,
            List<String> nextUnresolvedQuestions) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                nextAssumptions,
                nextIssues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                nextRisks,
                nextDecisions,
                nextUnresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    private FeaturePlanState copy(
            EnumMap<PlanSectionKey, PlanSectionStatus> sm,
            List<RequirementEntry> req,
            List<PlanAssumption> asm,
            List<PlanIssue> iss,
            List<ValidationEntry> val,
            SolutionOutline outline,
            Instant updated) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                req,
                asm,
                iss,
                val,
                outline,
                traceability,
                projectContext,
                Map.copyOf(sm),
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                updated,
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public String getContextId() {
        return contextId;
    }

    public String getFeatureId() {
        return featureId;
    }

    public String getFeatureSlug() {
        return featureSlug;
    }

    public String getRoomChannelId() {
        return roomChannelId;
    }

    public String getIntakeThreadId() {
        return intakeThreadId;
    }

    public String getRepoRef() {
        return repoRef;
    }

    public String getTitle() {
        return title;
    }

    public String getInitialRequest() {
        return initialRequest;
    }

    public String getPlanStatus() {
        return planStatus;
    }

    public List<RequirementEntry> getRequirements() {
        return requirements;
    }

    public List<PlanAssumption> getAssumptions() {
        return assumptions;
    }

    public List<PlanIssue> getIssues() {
        return issues;
    }

    public List<ValidationEntry> getValidationNotes() {
        return validationNotes;
    }

    public SolutionOutline getSolutionOutline() {
        return solutionOutline;
    }

    public TraceabilityPlaceholder getTraceability() {
        return traceability;
    }

    public ProjectContextSummary getProjectContext() {
        return projectContext;
    }

    public Map<PlanSectionKey, PlanSectionStatus> getSectionStatuses() {
        return sectionStatuses;
    }

    public PlanConfidence getPlanConfidence() {
        return planConfidence;
    }

    public PlanApproval getPlanApproval() {
        return planApproval;
    }

    public PlanCritiqueSnapshot getPlanCritiqueSnapshot() {
        return planCritiqueSnapshot;
    }

    public String getRepoWorkspaceId() {
        return repoWorkspaceId;
    }

    public String getRepoWorkspaceStatus() {
        return repoWorkspaceStatus;
    }

    public String getRepoLocalPath() {
        return repoLocalPath;
    }

    public String getRepoAccessNotes() {
        return repoAccessNotes;
    }

    public String getProfileId() {
        return profileId;
    }

    public Map<String, ArtifactState> getArtifacts() {
        return artifacts;
    }

    public List<PlanRisk> getRisks() {
        return risks;
    }

    public List<PlanDecision> getDecisions() {
        return decisions;
    }

    public List<String> getUnresolvedQuestions() {
        return unresolvedQuestions;
    }

    public String getCritiqueLifecycleStatus() {
        return critiqueLifecycleStatus;
    }

    public Instant getPacketPostedAt() {
        return packetPostedAt;
    }

    public String getPacketMessageRef() {
        return packetMessageRef;
    }

    public String getPacketPostedFingerprint() {
        return packetPostedFingerprint;
    }

    public int getPacketPostedChunkCount() {
        return packetPostedChunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public PlanningIntakeStage getPlanningIntakeStage() {
        return planningIntakeStage;
    }

    public Instant getPlanningIntakeStageEnteredAt() {
        return planningIntakeStageEnteredAt;
    }

    public int getIntakeKickoffPostedVersion() {
        return intakeKickoffPostedVersion;
    }

    public String getIntakeKickoffPostedFingerprint() {
        return intakeKickoffPostedFingerprint;
    }

    public String getPlanningOrchestrationFailureReason() {
        return planningOrchestrationFailureReason;
    }

    public boolean isAutonomousPlanningPassCompleted() {
        return autonomousPlanningPassCompleted;
    }

    public int getClarificationTurnsCompleted() {
        return clarificationTurnsCompleted;
    }

    public List<String> getClarificationOutcomeHistory() {
        return clarificationOutcomeHistory;
    }

    public String getClarificationCoordinatorLedgerJson() {
        return clarificationCoordinatorLedgerJson;
    }

    public String getCoordinatorConfiguredBotId() {
        return coordinatorConfiguredBotId;
    }

    public String getPlanningFailureCategory() {
        return planningFailureCategory;
    }

    public String getPlanningFailurePhase() {
        return planningFailurePhase;
    }

    public boolean isPlanningRecoverableDraftAvailable() {
        return planningRecoverableDraftAvailable;
    }

    public String getPlanningLastRecoveryHint() {
        return planningLastRecoveryHint;
    }

    public String getPlanningCanonicalDecisionJson() {
        return planningCanonicalDecisionJson;
    }

    public PlanningCanonicalDecision getPlanningCanonicalDecision() {
        return PlanningCanonicalDecision.fromJson(planningCanonicalDecisionJson);
    }

    public String getPlanningLastPostedPacketDecisionId() {
        return planningLastPostedPacketDecisionId;
    }

    public String getPlanningLastAskedQuestionDecisionId() {
        return planningLastAskedQuestionDecisionId;
    }

    public String getPlanningLastMaterialStateChangeFingerprint() {
        return planningLastMaterialStateChangeFingerprint;
    }

    public String getPlanningGapAskCountsJson() {
        return planningGapAskCountsJson;
    }

    public String getPlanningConfidenceBreakdownJson() {
        return planningConfidenceBreakdownJson;
    }

    public FeaturePlanState withPlanningGapAskCountsJson(String json) {
        String j = normalizeJsonObject(json);
        if (j.equals(planningGapAskCountsJson)) {
            return this;
        }
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                planningCanonicalDecisionJson,
                planningLastPostedPacketDecisionId,
                planningLastAskedQuestionDecisionId,
                planningLastMaterialStateChangeFingerprint,
                j,
                planningConfidenceBreakdownJson);
    }

    public FeaturePlanState withPlanningConfidenceBreakdownJson(String json) {
        String j = normalizeJsonObject(json);
        if (j.equals(planningConfidenceBreakdownJson)) {
            return this;
        }
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                planningCanonicalDecisionJson,
                planningLastPostedPacketDecisionId,
                planningLastAskedQuestionDecisionId,
                planningLastMaterialStateChangeFingerprint,
                planningGapAskCountsJson,
                j);
    }

    public FeaturePlanState withCoordinatorConfiguredBotId(String configuredBotId) {
        String id = configuredBotId != null ? configuredBotId.trim() : "";
        if (id.equals(coordinatorConfiguredBotId)) {
            return this;
        }
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                id,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withPlannerRecoveryFields(
            PlanningFailureCategory category,
            String phase,
            boolean recoverableDraft,
            String lastRecoveryHint) {
        String cat =
                category != null && category != PlanningFailureCategory.NONE ? category.name() : "";
        String ph = phase != null ? phase.trim() : "";
        String hint = lastRecoveryHint != null ? lastRecoveryHint.trim() : "";
        if (cat.equals(planningFailureCategory)
                && ph.equals(planningFailurePhase)
                && recoverableDraft == planningRecoverableDraftAvailable
                && hint.equals(planningLastRecoveryHint)) {
            return this;
        }
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                cat,
                ph,
                hint,
                recoverableDraft);
    }

    /**
     * Replace the JSON clarification assessment ledger (see {@link com.vinekeepers.workflow.planning.ClarificationCoordinatorLedger}).
     */
    public FeaturePlanState withClarificationCoordinatorLedgerJson(String json) {
        String j = json != null && !json.isBlank() ? json : "{}";
        if (j.equals(clarificationCoordinatorLedgerJson)) {
            return this;
        }
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                j,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withAutonomousPlanningPassCompleted(boolean completed) {
        if (completed == this.autonomousPlanningPassCompleted) {
            return this;
        }
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                completed,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    /**
     * Records one surfaced clarification question for budget accounting (canonical engine).
     */
    public FeaturePlanState withClarificationQuestionSurfaced(String auditLine) {
        int next = clarificationTurnsCompleted + 1;
        List<String> hist = appendHistory(clarificationOutcomeHistory, auditLine);
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                next,
                hist,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withClarificationEngineNote(String auditLine) {
        if (auditLine == null || auditLine.isBlank()) {
            return this;
        }
        List<String> hist = appendHistory(clarificationOutcomeHistory, auditLine.trim());
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                hist,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withPlanningCanonicalDecision(
            PlanningCanonicalDecision decision,
            String lastMaterialStateChangeFingerprint,
            String lastAskedQuestionDecisionId) {
        String nextJson = decision != null ? decision.toJson() : "{}";
        String nextFingerprint =
                lastMaterialStateChangeFingerprint != null ? lastMaterialStateChangeFingerprint.trim() : "";
        String nextAsked = lastAskedQuestionDecisionId != null ? lastAskedQuestionDecisionId.trim() : "";
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                nextJson,
                planningLastPostedPacketDecisionId,
                nextAsked,
                nextFingerprint,
                planningGapAskCountsJson,
                planningConfidenceBreakdownJson);
    }

    public FeaturePlanState withLastPostedPacketDecisionId(String decisionId) {
        String next = decisionId != null ? decisionId.trim() : "";
        if (next.equals(planningLastPostedPacketDecisionId)) {
            return this;
        }
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                planningCanonicalDecisionJson,
                next,
                planningLastAskedQuestionDecisionId,
                planningLastMaterialStateChangeFingerprint,
                planningGapAskCountsJson,
                planningConfidenceBreakdownJson);
    }

    private static List<String> appendHistory(List<String> prior, String line) {
        List<String> n = new ArrayList<>(prior != null ? prior : List.of());
        n.add(line);
        int cap = 32;
        if (n.size() > cap) {
            n = new ArrayList<>(n.subList(n.size() - cap, n.size()));
        }
        return List.copyOf(n);
    }

    /**
     * Move to a new intake stage (updates {@code planningIntakeStageEnteredAt} when {@code enteredAt} is non-null).
     */
    public FeaturePlanState withPlanningIntakeStage(PlanningIntakeStage stage, Instant enteredAt) {
        Objects.requireNonNull(stage, "stage");
        Instant at = enteredAt != null ? enteredAt : Instant.now();
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                stage,
                at,
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable,
                planningCanonicalDecisionJson,
                planningLastPostedPacketDecisionId,
                planningLastAskedQuestionDecisionId,
                planningLastMaterialStateChangeFingerprint,
                planningGapAskCountsJson,
                planningConfidenceBreakdownJson);
    }

    public FeaturePlanState withIntakeKickoffPosted(int version, String fingerprint) {
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                planningIntakeStage,
                planningIntakeStageEnteredAt,
                version,
                fingerprint != null ? fingerprint : "",
                planningOrchestrationFailureReason,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                planningFailureCategory,
                planningFailurePhase,
                planningLastRecoveryHint,
                planningRecoverableDraftAvailable);
    }

    public FeaturePlanState withPlanningOrchestrationFailure(String reason) {
        return withPlanningOrchestrationFailure(reason, PlanningFailureCategory.NONE, "", false, "");
    }

    public FeaturePlanState withPlanningOrchestrationFailure(
            String reason,
            PlanningFailureCategory category,
            String phase,
            boolean recoverableDraft,
            String recoveryHint) {
        String r = reason != null ? reason : "";
        String cat =
                category != null && category != PlanningFailureCategory.NONE ? category.name() : "";
        String ph = phase != null ? phase.trim() : "";
        String hint = recoveryHint != null ? recoveryHint.trim() : "";
        return new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                planStatus,
                requirements,
                assumptions,
                issues,
                validationNotes,
                solutionOutline,
                traceability,
                projectContext,
                sectionStatuses,
                planConfidence,
                planApproval,
                planCritiqueSnapshot,
                repoWorkspaceId,
                repoWorkspaceStatus,
                repoLocalPath,
                repoAccessNotes,
                profileId,
                artifacts,
                risks,
                decisions,
                unresolvedQuestions,
                critiqueLifecycleStatus,
                packetPostedAt,
                packetMessageRef,
                packetPostedFingerprint,
                packetPostedChunkCount,
                createdAt,
                Instant.now(),
                PlanningIntakeStage.FAILED,
                Instant.now(),
                intakeKickoffPostedVersion,
                intakeKickoffPostedFingerprint,
                r,
                autonomousPlanningPassCompleted,
                clarificationTurnsCompleted,
                clarificationOutcomeHistory,
                clarificationCoordinatorLedgerJson,
                coordinatorConfiguredBotId,
                cat,
                ph,
                hint,
                recoverableDraft);
    }

    public int countBlockingIssues() {
        int n = 0;
        for (PlanIssue i : issues) {
            if (i.isBlocking()) {
                n++;
            }
        }
        return n;
    }
}
