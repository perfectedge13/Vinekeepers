package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.workflow.planning.ClarificationProjection;

import java.util.Locale;
import java.util.Optional;

/**
 * Plain-language labels for planning packet, critique, and readiness copy shown in Discord (avoid raw ids / enums).
 */
public final class PlanningUserFacingCopy {

    private PlanningUserFacingCopy() {}

    /**
     * Canonical plain-English copy for the review checkpoint shown before approval is opened.
     */
    public static String readinessCheckpointGuideForDiscord() {
        return "**What we need from you**\n"
                + "This is a **planning review checkpoint** before launch approval opens.\n\n"
                + "Read the review summary in this thread, then choose whether to keep revising the packet or continue toward "
                + "approval evaluation. If the action menu does not render, reply with `continue` or `revise`.\n\n"
                + "**Not asking for:** a merge, a production deploy, or a Cursor run — only how to route the workflow.";
    }

    public static String humanizeCritiqueSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "Note";
        }
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "MUST_FIX" -> "Must fix";
            case "BLOCKER" -> "Blocking";
            case "WARN" -> "Warning";
            case "INFO" -> "Note";
            default -> titleCaseToken(severity.trim());
        };
    }

    public static String humanizeReadinessStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "BLOCKED" -> "Blocked";
            case "NOT_READY" -> "Not ready";
            case "REVIEWABLE" -> "Ready for review";
            case "NEEDS_REVISION" -> "Needs revision";
            case "NEEDS_HUMAN_DECISION" -> "Needs your decision";
            case "READY" -> "Ready";
            case "CONDITIONALLY_READY" -> "Conditionally ready";
            default -> titleCaseToken(status.trim().replace('_', ' '));
        };
    }

    public static String humanizeAssumptionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Open";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN" -> "Open";
            case "CONFIRMED" -> "Confirmed";
            case "REJECTED" -> "Rejected";
            case "ACCEPTED_RISK" -> "Accepted risk";
            default -> titleCaseToken(status.trim().replace('_', ' '));
        };
    }

    public static String humanizeIssueStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Open";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN" -> "Open";
            case "BLOCKING" -> "Blocking";
            case "RESOLVED" -> "Resolved";
            case "WAIVED" -> "Waived";
            default -> titleCaseToken(status.trim().replace('_', ' '));
        };
    }

    public static String humanizeRiskDecisionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Open";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN" -> "Open";
            case "RESOLVED" -> "Resolved";
            case "WAIVED" -> "Waived";
            default -> titleCaseToken(status.trim().replace('_', ' '));
        };
    }

    public static String humanizeGovernanceSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "Medium";
        }
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> "High";
            case "MEDIUM" -> "Medium";
            case "LOW" -> "Low";
            default -> titleCaseToken(severity.trim().toLowerCase(Locale.ROOT));
        };
    }

    /**
     * Plain-language line for coordinator cycle errors surfaced in Discord (machine tokens like {@code NO_PLAN},
     * {@code MISSING_DEPS}, or {@code DEPTH_FAIL_AFTER_RETRIES: …}).
     */
    public static String humanizePlanningRoomCycleErrorLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim();
        String up = t.toUpperCase(Locale.ROOT);
        if (up.startsWith("DEPTH_FAIL_AFTER_RETRIES:")) {
            return "After several drafting passes the plan still needs more concrete detail before review.";
        }
        return humanizePlanningRoomCycleErrorCode(t);
    }

    /** Machine-only coordinator cycle error codes from {@link com.vinekeepers.workflow.planning.PlanningCyclePipeline}. */
    public static String humanizePlanningRoomCycleErrorCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String c = code.trim();
        return switch (c.toUpperCase(Locale.ROOT)) {
            case "MISSING_DEPS" ->
                    "Something this step needs (workspace setup, API access, or profile data) is not available.";
            case "NO_CONTEXT" -> "No planning context is linked to this thread.";
            case "NO_PLAN" -> "No planning draft is loaded for this session yet.";
            case "PROFILE_NOT_V2" ->
                    "This work profile does not enable the full coordinator drafting loop for this room.";
            case "SYNTHESIS_TRANSPORT_ERROR" ->
                    "A drafting step could not reach the model service cleanly; the current draft is preserved.";
            case "SYNTHESIS_UPSERTS_NOT_APPLIED" ->
                    "A drafting step produced no valid planning updates we could apply this pass.";
            case "SYNTHESIS_JSON_INVALID" ->
                    "A drafting step returned structured output we could not read; your existing draft is preserved.";
            case "SYNTHESIS_REPAIR_EXHAUSTED" ->
                    "A drafting step still returned unreadable structured output after repair; your existing draft is preserved.";
            case "SYNTHESIS_UPSERT_REJECTED" ->
                    "A drafting step proposed updates that did not match this room's schema; the prior draft is unchanged.";
            case "SYNTHESIS_EMPTY_NOOP" ->
                    "A drafting step made no applicable updates this pass.";
            case "REPO_GROUNDING_UNAVAILABLE" ->
                    "Repo grounding was not available for this drafting pass; continuing with limited workspace context.";
            case "WORKSPACE_BLOCKED" ->
                    "Workspace setup is blocking planning; fix the workspace issue shown above before coordinator drafting continues.";
            case "PLANNING_REQUIRES_CANONICAL_V1" ->
                    "This room requires canonical coordinator clarification mode; check the work profile configuration.";
            case "CLARIFICATION_COMPOSER_FAILED" ->
                    "The coordinator could not produce a safe clarification question this cycle; try again or adjust profile prompts.";
            default -> "Planning hit an unexpected issue; try again or check configuration.";
        };
    }

    /**
     * Coordinator cycle summary for Discord threads (presentation only; callers supply facts from spread / projection).
     * Parameter shape matches the former {@code PlanningCyclePipeline.buildOrchestratorSummary} for stable tests.
     */
    public static String planningOrchestratorRoundSummary(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            ClarificationProjection ranked,
            int cycleIteration,
            boolean readyToPostPacket,
            boolean coordinatorLegacyBoolean,
            String workspaceSummaryLine,
            boolean surfaceWaitingForUserDetail,
            boolean structuredParseFailed) {
        if (structuredParseFailed) {
            return "**Planning update**\n\n"
                    + "The latest structured coordinator pass returned output that **could not be applied cleanly**. "
                    + "Keeping your saved draft; we'll try another pass unless you reply with a constraint.";
        }
        if (readyToPostPacket) {
            return "**Planning update**\n\nNext I'll post the packet and run readiness checks.";
        }
        if (ranked != null
                && ranked.userInputRequired()
                && ranked.questionText() != null
                && !ranked.questionText().isBlank()) {
            String q = ranked.questionText().trim();
            String depthBit =
                    depthOk ? "Depth check passed for this cycle." : "Still tightening structure before review.";
            return "**Need from you**\n"
                    + q
                    + "\n\n**What I'm tracking**\n"
                    + depthBit
                    + " (cycle "
                    + cycleIteration
                    + ").\n\n"
                    + (ranked.useStructuredChoices()
                            ? "Use the action menu if shown, or reply with one clear answer."
                            : "Reply in **plain text** with one concrete answer.");
        }
        String ws = workspaceSummaryLine != null ? workspaceSummaryLine.trim() : "";
        if (!ws.isBlank()) {
            return ws;
        }
        if (surfaceWaitingForUserDetail) {
            return "**Planning update**\n\nPaused until the detail below is answered — reply when you can.";
        }
        String title = plan != null && plan.getTitle() != null ? plan.getTitle().trim() : "";
        String head = title.isBlank() ? "Planning" : title;
        String dr = depthReason != null ? depthReason.trim() : "";
        boolean legacy = coordinatorLegacyBoolean;
        return "**"
                + head
                + "**\n\n"
                + (depthOk
                        ? "In good shape — continuing."
                        : "Working through drafting checks"
                                + (dr.isBlank() ? "." : ": " + dr)
                                + (legacy ? " (coordinator follow-up)." : ""));
    }

    /**
     * User-safe text for {@code planningClarificationMergeError} in Discord (never raw exception / stack tokens).
     */
    public static String humanizePlanningClarificationMergeError(String codeOrMessage) {
        if (codeOrMessage == null || codeOrMessage.isBlank()) {
            return "";
        }
        String raw = codeOrMessage.trim();
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "MISSING_DEPS" ->
                    "A required dependency (plan store or profile registry) is not available.";
            case "NO_CONTEXT" -> "No planning context is linked to this thread.";
            case "NO_CHOICE" -> "Pick an option or send a text reply first.";
            case "NO_PLAN" -> "No planning draft is loaded for this session yet.";
            case "MERGE_TARGET_MISSING" ->
                    "This clarification is missing a merge target in metadata; the coordinator cannot apply your reply safely.";
            case "MERGE_TARGET_INVALID" ->
                    "The merge target path is not valid for this profile; reply was not written into the plan.";
            case "AMBIGUOUS_REPLY" ->
                    "Your reply did not match a single clear option; try again with one concrete answer.";
            case "MERGE_TARGET_MISMATCH" ->
                    "The clarification metadata does not match this gap's merge contract; nothing was written.";
            case "PARTIAL_MERGE" ->
                    "Only part of your answer was recorded; you can clarify further on the next question if needed.";
            case "CONTRADICTION_RECORDED" ->
                    "Your answer conflicts with the current draft; we recorded it and lowered confidence until the next pass.";
            default -> "That reply could not be applied; try again.";
        };
    }

    /** Strips model / transport noise from strings merged into {@code planningCycleUserVisibleFailure}. */
    public static String humanizePlanningCycleFailureFragment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.replace("\r\n", " ").replace('\n', ' ').trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("json") && (lower.contains("parse") || lower.contains("invalid"))) {
            return "A drafting step returned output we could not apply.";
        }
        if (lower.contains("fieldid")
                || lower.contains("does not match this planning profile")
                || lower.contains("unsupported field ids")) {
            return "A drafting step proposed updates that did not match this room's schema.";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "A drafting step timed out while contacting the model service.";
        }
        if (lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("429")) {
            return "The model service asked us to slow down during a drafting step.";
        }
        if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized") || lower.contains("forbidden")) {
            return "The model service rejected this drafting request.";
        }
        if (lower.contains("connect")
                || lower.contains("connection")
                || lower.contains("refused")
                || lower.contains("reset")
                || lower.contains("503")
                || lower.contains("502")
                || lower.contains("bad gateway")
                || lower.contains("service unavailable")
                || lower.contains("upstream")) {
            return "The model service was unavailable during a drafting step.";
        }
        if (lower.contains("interrupted")) {
            return "A drafting step was interrupted.";
        }
        if (lower.startsWith("ERROR:")) {
            t = t.substring(6).trim();
        }
        if (t.length() > 140) {
            return t.substring(0, 139) + "…";
        }
        return t;
    }

    /**
     * After the planning packet is posted in-thread, critique blocks should not repeat repo grounding that already
     * appears under {@code **Repo / workspace (grounding)**} in the packet.
     */
    public static String repoGroundingPointerAfterPacket() {
        return "_Workspace/repo signals are in the **Repo / workspace (grounding)** section of the planning packet above._";
    }

    /**
     * Readable label for artifact / section / field, using work profile titles and field labels when available.
     */
    public static String describePlanningFieldPath(
            WorkProfileDefinition profile, String artifactId, String sectionId, String fieldId) {
        String a = artifactId != null ? artifactId.trim() : "";
        if (a.isBlank()) {
            return "";
        }
        String s = sectionId != null ? sectionId.trim() : "";
        String f = fieldId != null ? fieldId.trim() : "";
        if (profile != null) {
            Optional<ArtifactDefinition> art = profile.getArtifact(a);
            String artTitle = art.map(ArtifactDefinition::getTitle).orElse("").trim();
            String artLabel = !artTitle.isBlank() ? artTitle : titleCaseSnake(a);
            if (s.isBlank()) {
                return artLabel;
            }
            Optional<SectionDefinition> secOpt = profile.findSection(a, s);
            String secTitle = secOpt.map(SectionDefinition::getTitle).orElse("").trim();
            String secLabel = !secTitle.isBlank() ? secTitle : titleCaseSnake(s);
            if (f.isBlank()) {
                return artLabel + " — " + secLabel;
            }
            Optional<String> fieldLabel =
                    secOpt.flatMap(
                            sec -> sec.getFields().stream()
                                    .filter(fd -> fd.getFieldId().equals(f))
                                    .findFirst()
                                    .map(FieldDefinition::getLabel)
                                    .filter(l -> l != null && !l.isBlank()));
            String fl = fieldLabel.orElseGet(() -> titleCaseSnake(f));
            return artLabel + " — " + secLabel + " — " + fl;
        }
        if (s.isBlank()) {
            return titleCaseSnake(a);
        }
        if (f.isBlank()) {
            return titleCaseSnake(a) + " — " + titleCaseSnake(s);
        }
        return titleCaseSnake(a) + " — " + titleCaseSnake(s) + " — " + titleCaseSnake(f);
    }

    /**
     * Parses {@code ref} values stored on findings (e.g. {@code initialRequest}, {@code overall_plan.outline},
     * {@code requirements_spec.narrative.feature_summary}).
     */
    public static String describePlanningFieldRef(WorkProfileDefinition profile, String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        String r = ref.trim();
        if ("initialRequest".equals(r)) {
            return "Initial request summary";
        }
        String[] parts = r.split("\\.");
        if (parts.length == 1) {
            return titleCaseSnake(parts[0]);
        }
        if (parts.length == 2) {
            return describePlanningFieldPath(profile, parts[0], parts[1], "");
        }
        return describePlanningFieldPath(profile, parts[0], parts[1], parts[2]);
    }

    public static String defaultDiscoveryGapMessage(String kind) {
        if (kind == null || kind.isBlank()) {
            return "A planning discovery item is still unresolved.";
        }
        return switch (kind.trim().toUpperCase(Locale.ROOT)) {
            case "WORKSPACE" -> "The repo workspace must be ready before this plan can advance.";
            case "REQUIRED_FIELD" -> "A required planning field is still empty or needs clearer detail.";
            default -> "A planning discovery item is still unresolved.";
        };
    }

    /** Fallback critique line when {@link PlanCritiqueFinding#getMessage()} is empty. */
    public static String defaultMessageForCritiqueCode(String code) {
        if (code == null || code.isBlank()) {
            return "See the planning thread for more context.";
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (c.startsWith("DISCOVERY_GAP_")) {
            return defaultDiscoveryGapMessage(c.substring("DISCOVERY_GAP_".length()));
        }
        return switch (c) {
            case "INTAKE_DISCOVERY_INCOMPLETE" ->
                    "Finish the first planning questions in this thread before the plan can move toward approval.";
            case "OPEN_ISSUES" -> "Open issues are recorded on the plan; resolve or waive them before approval.";
            case "OPEN_ASSUMPTIONS" -> "Open assumptions are recorded; confirm or adjust them before implementation.";
            case "MISSING_PROFILE" -> "This plan has no work profile selected.";
            case "UNKNOWN_PROFILE" -> "The work profile for this plan could not be found in configuration.";
            case "PLACEHOLDER_PLANNING_FIELD" -> "A planning field still looks like a template; add concrete detail.";
            default -> englishFromUnderscoreCode(c);
        };
    }

    public static String formatCritiqueFindingBullet(PlanCritiqueFinding f, WorkProfileDefinition profile) {
        String sev = humanizeCritiqueSeverity(f.getSeverity());
        String msg = f.getMessage() != null ? f.getMessage().trim() : "";
        if (msg.isBlank()) {
            msg = defaultMessageForCritiqueCode(f.getCode());
        }
        String refPlain = describePlanningFieldRef(profile, f.getRef());
        String refRaw = f.getRef() != null ? f.getRef().trim() : "";
        if (!refPlain.isBlank() && !refRaw.isBlank() && msg.contains(refRaw) && !refRaw.equals(refPlain)) {
            msg = msg.replace(refRaw, refPlain);
        }
        String line = "• **" + sev + ":** " + msg;
        if (!refPlain.isBlank() && !msg.toLowerCase(Locale.ROOT).contains(refPlain.toLowerCase(Locale.ROOT))) {
            line += " _(Area: " + refPlain + ")_";
        }
        return line;
    }

    private static String englishFromUnderscoreCode(String code) {
        String[] parts = code.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p.toLowerCase(Locale.ROOT));
        }
        if (sb.isEmpty()) {
            return "Planning check required.";
        }
        String s = sb.toString();
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1) + ".";
    }

    private static String titleCaseToken(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String titleCaseSnake(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(titleCaseToken(p));
        }
        return sb.toString();
    }
}
