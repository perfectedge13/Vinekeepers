# Decisions

# Selected decisions

- Luna remains config-driven in YAML; the feature documents the shipped workflow rather than hardcoded bot registration.
- Discord mention activation belongs to routing, while the gathered project/code-change sequence belongs to this workflow feature.
- Repository operations are delegated through `cursor.fullRun` and the Cursor adapter instead of being embedded directly in workflow steps.

## 2026-03-19 — Phase B structured discovery in luna_cursor

**Context:** After work-profile missing-fields, Luna needs a config-driven way to close REQUIRED_FIELD gaps and log assumptions vs blockers without hardcoding discovery in Java steps.

**Decision:** Expose Phase B as **`call_action`** steps with **storeSpread** outputs (`discoveryHasOpenGaps`, `discoveryBlockingIssueMode`, prompts, apply targets). **`StructuredDiscoverySupport`** plus planning types (**`DiscoveryGap`**, **`DiscoveryAgenda`**, etc.) own the logic; **`classify_assumption_or_issue`** routes to existing **`append_plan_issue`** / **`append_plan_assumption`** actions.

**Consequence:** YAML owns loop structure (branch + clear **`discoveryAnswerRaw`**); blocker severity ends the loop so downstream messaging can surface issues before **`launch_cursor_run`**.
