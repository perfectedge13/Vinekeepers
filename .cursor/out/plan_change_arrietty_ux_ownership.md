# Change context (for plan_change / implement)

## Scope

**Request-derived:** Arrietty room UX and ownership warning. Features/requirements/assets in scope:

- **Features:** FEAT-WORKFLOW-STEPS, FEAT-ROUTING, FEAT-CURSOR-GATHERING
- **Requirements:** REQ-WORKFLOW-001, REQ-BOT-001, REQ-LUNA-001
- **Assets:** ASSET-CAPTURE-FIELD-STEP (CaptureFieldFromEventStep), ASSET-CONFIGURABLE-WORKFLOW-RUNNER (ConfigurableWorkflowRunner), ASSET-BRANCH-STEP (BranchStep), ASSET-ROUTER (Router), ASSET-BOTS-YAML (config/bots.yaml), ASSET-LIFECYCLE-CONTEXT-STORE (LifecycleContextStore), ASSET-BOOTSTRAP (Bootstrap)

---

## Per feature / requirement / asset

### FEAT-WORKFLOW-STEPS (workflow-steps)

- **Feature:** Workflow step DSL and branching actions. Status: active. Doc: mkdoc/features/domain/workflow/workflow-steps.md. Summary: WorkflowDefinition and step/action primitives; capture_field reads from message or interaction (values/customId).
- **REQ-WORKFLOW-001:** Workflow state machine and config-driven runners. Statement: WorkflowRunner runs workflow for event; configurable workflows support prompt_for_field and capture_field for pause/resume; BranchStep branches by state key/value; StepResult clearKeys; bind/state precedence for actions. Acceptance: capture_field and branch steps; session keys; CallActionStep; StepResult clearKeys. Validation tests: UNIT-CAPTURE-FIELD-STEP (CaptureFieldFromEventStepTest), UNIT-BRANCH-STEP (BranchStepTest), UNIT-CONFIGURABLE-WORKFLOW-RUNNER-*.
- **ASSET-CAPTURE-FIELD-STEP:** path: src/main/java/com/vinekeepers/workflow/steps/CaptureFieldFromEventStep.java. Role: Captures field from current event; for interaction normalizes payload.values (Map/List) and customId. Requires REQ-WORKFLOW-001. **Change:** Add optional trimAndLower (e.g. constructor or config flag); when true, trim and lowercase stored value before state put.
- **ASSET-CONFIGURABLE-WORKFLOW-RUNNER:** path: src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java. Role: Runs workflow from WorkflowDefinition; buildSteps parses step config. **Change:** Parse optional trimAndLower for capture_field step and pass to CaptureFieldFromEventStep.
- **ASSET-BRANCH-STEP:** path: src/main/java/com/vinekeepers/workflow/steps/BranchStep.java. Role: Branches by when (key/value map or "else"). Value comparison uses String.valueOf(state.get(key)) and expectVal.toString(). No code change required; arrietty_room YAML will branch on roomAction (status/retry/close/blank/else).
- **Doc excerpts (workflow-steps):** Decisions: Intent-based steps and connector sink; StepOutcome explicit; CallActionStep resolves action or tool. Contracts: Step config may include intent, choices, confirmLabel, cancelLabel, fields; capture_field reads content or interaction values/customId. Known-issues: DSL limited to built-in step types; tool-backed actions depend on runner context.

### FEAT-ROUTING (routing)

- **Feature:** Event routing and normalized context. Status: active. Doc: mkdoc/features/domain/bot/routing.md. Summary: Router matches events to bots; when channel has lifecycle context and owner has handlesOwnedSpaces, single-owner precedence.
- **REQ-BOT-001:** Route events to bots by routing rules and ownership. Statement: Router uses LifecycleContextStore and handlesOwnedSpaces map; when channel has lifecycle context and owner has handlesOwnedSpaces, only that owner returned; else filter-based. Acceptance: setHandlesOwnedSpacesByBotId; single-owner when owner has handles; filter-based otherwise. Anti_patterns: Do not hardcode bot ids in Router.
- **ASSET-ROUTER:** path: src/main/java/com/vinekeepers/bot/Router.java. Role: Match events to bots; lifecycle + handlesOwnedSpaces → single-owner. **Change:** When channel has lifecycle context (ctx present) but owner bot does NOT have handlesOwnedSpaces (handlesOwnedSpacesByBotId.get(ownerBotId) not TRUE), log a warning (e.g. lifecycle owner bot X does not have handlesOwnedSpaces; routing may be inconsistent). After the existing block that returns List.of(ownerBotId), add a branch: if ctx present and owner has no handlesOwnedSpaces, log warning then fall through to filter-based.
- **Doc excerpts (routing):** Decisions: Ownership-based routing (handlesOwnedSpaces); Bootstrap passes store and map to Router; no hardcoded bot ids. Known-issues: Routing fields depend on connector; filter surface not fully data-driven.

### FEAT-CURSOR-GATHERING (cursor-gathering)

- **Feature:** Cursor-backed gathering and lifecycle room. Status: active. Doc: mkdoc/features/domain/workflow/cursor-gathering.md. Summary: Luna luna_cursor workflow; Arrietty template with workflowRef arrietty_room and handlesOwnedSpaces for lifecycle room; arrietty_room handles inbound room events.
- **REQ-LUNA-001:** Luna bot and Phase 1 lifecycle room. Statement: Arrietty template with workflowRef arrietty_room and handlesOwnedSpaces true; arrietty_room workflow handles inbound room events (messages, interactions). Acceptance: Arrietty in bots.yaml with arrietty_room and handlesOwnedSpaces; no hardcoded bot ids.
- **ASSET-BOTS-YAML:** path: config/bots.yaml. Role: Bot definitions and workflows DSL; arrietty_room workflow. **Change:** Make arrietty_room message-first: (1) First step capture_field storeIn: roomAction with trimAndLower (optional key in step config). (2) Second step branch on roomAction: status→step index for status done, retry→retry done, close→close done, blank→clarification (e.g. step index for a clarification done/prompt), else→echo (done with {{roomAction}}). Remove or reorder the initial prompt_for_field so the first step is capture_field (message-first: any message in room is captured then branched). Step indices in branch next: must match the updated step list (capture_field at 0, branch at 1, then done steps for status/retry/close/clarification/echo).

---

## Implementation checklist (from guardrails)

- Do not delete requirements; do not remove required functionality.
- Do not change schema files or invent new spec keys; stay within existing schema (e.g. step config is already key-value; trimAndLower as optional boolean in capture_field step is a new step-level key—acceptable as workflow step param).
- Consider anti_patterns: REQ-BOT-001 — do not hardcode bot ids in Router; REQ-LUNA-001 — no hardcoding Arrietty in engine/Router.
- Repair spec drift before coding if any.

---

## Schema constraints

- core-registry.yml: requirements have id, title, statement, acceptance.criteria, traceability.assets, validation.tests; assets have id, path, role, requires. Step config in workflows is free-form key-value; adding trimAndLower under capture_field step is consistent with existing params (storeIn, contentKey).

---

## Tests to add/update

1. **Workflow message-first:** ConfigurableWorkflowRunnerTest or new test: arrietty_room-style flow where first step is capture_field (with trimAndLower); event provides content; then branch step routes by stored value (e.g. status/retry/close/blank/else); assert outcome per branch. Optionally CaptureFieldFromEventStepTest: when trimAndLower true, stored value is trimmed and lowercased.
2. **Ownership mismatch warning:** RouterTest: when channel has lifecycle context and owner bot does not have handlesOwnedSpaces, Router logs warning and returns filter-based bot ids (does not return only owner). Use LifecycleContextStore stub and setHandlesOwnedSpacesByBotId so owner is false; assert warning logged and return list is filter-based.

---

## Summary of code edits

| Location | Edit |
|----------|------|
| CaptureFieldFromEventStep | Add optional trimAndLower (constructor or builder); in execute(), if trimAndLower apply trim and toLowerCase to content before advance(storeIn, content). |
| ConfigurableWorkflowRunner.buildSteps | For type "capture_field", read optional trimAndLower (Boolean); pass to CaptureFieldFromEventStep. |
| config/bots.yaml workflows.arrietty_room | Message-first: step 0 capture_field storeIn: roomAction, trimAndLower: true. Step 1 branch: status→2, retry→3, close→4, blank→5 (clarification), else→6 (echo). Steps 2–6: done with appropriate messages. |
| Router.route() | After the block that finds lifecycle context for channel: if ctx present and ownerBotId non-blank and handlesOwnedSpacesByBotId.get(ownerBotId) != TRUE, log.warn("Channel has lifecycle owner bot {} but that bot does not have handlesOwnedSpaces; using filter-based routing", ownerBotId). Then fall through to dedupe(filterBotIds). |
| CaptureFieldFromEventStepTest | Add test: trimAndLower true yields trimmed and lowercased stored value (e.g. content " Status " → "status"). |
| RouterTest | Add test: lifecycle context present for channel, owner bot has handlesOwnedSpaces false → warning logged and returned list is filter-based (not single owner). |
