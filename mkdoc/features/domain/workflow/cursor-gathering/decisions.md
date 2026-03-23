# Decisions

# Selected decisions

- Luna remains config-driven in YAML; the feature documents the shipped workflow rather than hardcoded bot registration.
- Discord mention activation belongs to routing, while the gathered project/code-change sequence belongs to this workflow feature.
- Repository operations are delegated through `cursor.fullRun` and the Cursor adapter instead of being embedded directly in workflow steps.

## 2026-03-19 — Phase B structured discovery in luna_cursor

**Context:** After work-profile missing-fields, Luna needs a config-driven way to close REQUIRED_FIELD gaps and log assumptions vs blockers without hardcoding discovery in Java steps.

**Decision:** Expose Phase B as **`call_action`** steps with **storeSpread** outputs (`discoveryHasOpenGaps`, `discoveryBlockingIssueMode`, prompts, apply targets). **`StructuredDiscoverySupport`** plus planning types (**`DiscoveryGap`**, **`DiscoveryAgenda`**, etc.) own the logic; **`classify_assumption_or_issue`** routes to existing **`append_plan_issue`** / **`append_plan_assumption`** actions.

**Consequence:** YAML owns loop structure (branch + clear **`discoveryAnswerRaw`**); blocker severity ends the loop so downstream messaging can surface issues before **`launch_cursor_run`**.

## 2026-03-22 — Readiness checkpoint: plain-English prompts for operators

**Context:** Discord operators saw **`NEEDS_HUMAN_DECISION`** and ADR-style “decision” asks without a clear explanation of what action the bot wanted (approve launch vs revise plan vs answer a structured decision slot). Raw readiness enums, internal artifact paths, and jargon made the intake thread feel opaque (“this is awful”).

**Decision:** Keep workflow branches and state keys machine-readable (**`planReadinessStatus`**, **`prompt_for_field`** targets). Surface human copy only through **`PlanningUserFacingCopy`** (status labels, **`planReadinessCheckpointGuide`** when **`NEEDS_HUMAN_DECISION`**, critique severity wording) and **`PlanningPromptFormatter`** plus **`config/work-profiles.yaml`** hints so prompts explain *what you are being asked to do* and *what happens next*, without exposing internal paths or enum names in Discord text.

**Consequence:** Operators get a short status line plus an explicit guide block on the human-decision path; decision-log and field prompts use full sentences. Specs and tests trace **`ASSET-PLANNING-PROMPT-FORMATTER`**, **`ASSET-PLANNING-USER-FACING-COPY`**, and related workflow actions.

## 2026-03-22 — Planning packet and critique strings share the same humanization layer

**Context:** Even after readiness labels improved, planning packets, critique rollups, depth-gate failures, and role-thread snippets could still show machine-like fragments (paths, ids, severities) that operators could not map to “what to do next.”

**Decision:** Treat **`PlanningUserFacingCopy`** as the default formatter for Discord-visible planning packet sections, critique summary lines, depth/readiness explanations, and similar coordinator posts; keep **`planReadinessStatus`** and other spread keys stable for YAML. **`GenericReadinessEvaluator`** and **`PlanGovernanceDeriver`** use profile titles / **`userFacingDetail`** where applicable so gate and governance text stays consistent.

**Consequence:** One code path owns most operator wording; regressions are caught by **`PlanningUserFacingCopyTest`** and related Phase C tests. Internal diagnostics and logs may still use technical ids.
