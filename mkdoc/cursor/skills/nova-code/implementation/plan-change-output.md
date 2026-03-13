# Change context (for plan_change / implement)

## Scope

**Request:** (1) In config/bots.yaml set arrietty_room blank branch to `value: ""` so it matches empty string from CaptureFieldFromEventStep. (2) In RouterTest add/verify assertion that ownership-mismatch warning is logged (e.g. Logback ListAppender) when channel has lifecycle owner but that bot does not have handlesOwnedSpaces.

**Impacted registry:** specs/core-registry.yml (config, routing, workflow steps, Luna).

**Features / requirements / assets in scope:**
- **Features:** FEAT-CONFIG, FEAT-ROUTING, FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING
- **Requirements:** REQ-CONFIG-001, REQ-BOT-001, REQ-WORKFLOW-001, REQ-LUNA-001
- **Assets:** ASSET-BOTS-YAML (config/bots.yaml), ASSET-ROUTER (Router.java), ASSET-CAPTURE-FIELD-STEP (CaptureFieldFromEventStep.java), ASSET-BRANCH-STEP (BranchStep.java); test: RouterTest (com.vinekeepers.bot.RouterTest)

---

## Per feature / requirement

### REQ-CONFIG-001 — Load bot config from YAML
- **Statement:** ConfigLoader loads bot definitions, routing, workflows from YAML; optional handlesOwnedSpaces per bot; workflow step bind and branch config from YAML.
- **Criteria:** ConfigLoader parses workflows map and step config (including branch when/key/value); Arrietty template with workflowRef arrietty_room and handlesOwnedSpaces in config.
- **Assets:** ASSET-CONFIG-LOADER, ASSET-BOTS-YAML, ASSET-BOT-DEFINITION
- **Validation tests:** ConfigLoaderTest (buildBotsParsesHandlesOwnedSpacesTrue, buildBotsParsesHandlesOwnedSpacesAbsentAsFalse, etc.)

### REQ-BOT-001 — Route events to bots by routing rules and ownership
- **Statement:** Router matches events using filters and lifecycle ownership; when channel has lifecycle context but owner bot does not have handlesOwnedSpaces, Router logs a warning and uses filter-based routing.
- **Criteria:** When channel has lifecycle owner but that bot does not have handlesOwnedSpaces, Router logs warning; filter-based routing then applies.
- **Assets:** ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT
- **Validation tests:** RouterTest (UNIT-ROUTER, routeWithLifecycleStore_ownedChannel_returnsOnlyOwnerWhenOwnerHasHandlesOwnedSpaces, routeWithLifecycleStore_ownedChannel_usesFilterBasedWhenOwnerDoesNotHaveHandlesOwnedSpaces, routeWithLifecycleStore_ownedChannel_logsOwnershipMismatchWarningWhenOwnerDoesNotHaveHandlesOwnedSpaces, etc.)
- **Anti-patterns:** Do not hardcode bot ids (e.g. Arrietty) in Router or engine; use config-driven handlesOwnedSpaces and lifecycle context.

### REQ-WORKFLOW-001 — Workflow state machine and config-driven runners
- **Statement:** BranchStep branches by condition; value-based branch uses key/value map; capture_field with trimAndLower stores trimmed/lowercased text; empty content yields empty string.
- **Criteria:** BranchStep branch config when key/value; capture_field step stores content; CaptureFieldFromEventStep returns `content != null ? content : ""` (empty string for missing content).
- **Assets:** ASSET-BRANCH-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CONFIGURABLE-WORKFLOW-RUNNER
- **Validation tests:** BranchStepTest, CaptureFieldFromEventStepTest, ConfigurableWorkflowRunnerTest (runCaptureFieldWithTrimAndLowerStoresTrimmedAndLowercasedValue)

### REQ-LUNA-001 — Luna / Arrietty lifecycle room
- **Statement:** Arrietty template with workflowRef arrietty_room; arrietty_room workflow handles room events with message-first capture and optional trimAndLower for room name UX; branch for blank/empty input.
- **Criteria:** arrietty_room steps include capture_field (roomAction, trimAndLower) and branch with value for empty/blank matching.
- **Assets:** ASSET-BOTS-YAML, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-CAPTURE-FIELD-STEP, ASSET-BRANCH-STEP

---

## Implement tasks (from user request)

1. **config/bots.yaml — arrietty_room blank branch**  
   In workflow `arrietty_room`, the branch that handles “blank” (empty) user input must use `value: ""` so it matches the empty string produced by CaptureFieldFromEventStep when content is null or empty (`StepResult.advance(storeIn, content != null ? content : "")`). **Current state:** The file already has `when: { key: roomAction, value: "" }` at the blank branch (next: 5). If any copy or variant uses literal `value: blank`, change it to `value: ""` and leave the rest of the step/branch structure unchanged.

2. **RouterTest — ownership-mismatch warning**  
   Add or confirm an assertion that when the channel has a lifecycle owner but that bot does not have handlesOwnedSpaces, the Router logs a WARN containing the ownership-mismatch message (e.g. “does not have handlesOwnedSpaces”). Use Logback ListAppender (or equivalent) on the Router logger, trigger `route(event)` with a lifecycle context whose owner has handlesOwnedSpaces false, then assert at least one WARN log with that message. **Current state:** RouterTest already contains `routeWithLifecycleStore_ownedChannel_logsOwnershipMismatchWarningWhenOwnerDoesNotHaveHandlesOwnedSpaces` using ListAppender and asserting WARN with “does not have handlesOwnedSpaces”. Implement step should ensure this test is present, named appropriately, and passes; align assertion text with Router’s actual log message if needed.

---

## Doc excerpts (mkdoc)

**routing (features/domain/bot/routing):**
- **Decisions:** Ownership-based routing (handlesOwnedSpaces): per-bot flag from YAML; Router checks LifecycleContextStore; single-owner precedence when owner has handlesOwnedSpaces; no hardcoded bot ids. When owner does not have handlesOwnedSpaces, log warning and use filter-based routing.
- **Contracts:** Router depends on LifecycleContextStore and handlesOwnedSpaces map; when channel has lifecycle context and owner has handlesOwnedSpaces, only that bot returned; otherwise filter-based routing.

**workflow-steps (features/domain/workflow/workflow-steps):**
- **Decisions:** Step config accepts intent, choices, capture_field; NormalizedEventContext carries interaction payload; BranchStep when key/value for value-based branch.

---

## Schema constraints

- core-registry.yml: requirements use id, title, statement, acceptance.criteria, traceability.assets, validation.tests; assets use id, path, role, requires; no new requirement or asset keys without schema update.
- Do not delete or rename existing requirement ids or asset ids; do not invent new spec keys (guardrails).
