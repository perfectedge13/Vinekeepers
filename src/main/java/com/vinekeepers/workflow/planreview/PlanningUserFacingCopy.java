package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.PlanCritiqueFinding;

import java.util.Locale;
import java.util.Optional;

/**
 * Plain-language labels for planning packet, critique, and readiness copy shown in Discord (avoid raw ids / enums).
 */
public final class PlanningUserFacingCopy {

    private PlanningUserFacingCopy() {}

    /**
     * Canonical plain-English copy for the {@code NEEDS_HUMAN_DECISION} readiness fork (Discord templates via
     * {@code planReadinessCheckpointGuide}); kept in code so behavior and wording stay aligned.
     */
    public static String readinessCheckpointGuideForDiscord() {
        return "**What we need from you**\n"
                + "This is a **checkpoint before launch approval**.\n\n"
                + "Read the review summary in this thread, then use the **buttons in the next message** to choose whether to "
                + "open the launch-approval menu or send the packet through another planning pass first.\n\n"
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
            case "SYNTHESIS_UPSERTS_NOT_APPLIED" ->
                    "A drafting step returned planning updates that did not match this room's schema.";
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
            default -> "Planning hit an unexpected issue; try again or check configuration.";
        };
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
