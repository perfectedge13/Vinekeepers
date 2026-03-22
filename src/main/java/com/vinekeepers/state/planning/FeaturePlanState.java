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
    private final Instant createdAt;
    private final Instant updatedAt;

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
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
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
                Instant.now());
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
                Instant.now());
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
                Instant.now());
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
                Instant.now());
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
                Instant.now());
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
                Instant.now());
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
                Instant.now());
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
                Instant.now());
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
                updated);
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
