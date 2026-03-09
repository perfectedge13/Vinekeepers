# Change context (for plan_change / implement)

## Scope

**Request-derived:** Edit-flow fix already implemented. Validate no further code changes needed; update specs/mkdoc if acceptance or traceability need alignment.

- **Features:** FEAT-WORKFLOW, FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING
- **Requirements:** REQ-WORKFLOW-001, REQ-LUNA-001
- **Assets:** ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-BRANCH-STEP, ASSET-STEP-RESULT, ASSET-BOTS-YAML, ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW (and related workflow/Luna assets)

## Implement step guidance

- **Code:** Fix is already in place. `config/bots.yaml` has `edit_repo` branch with `clear: [project, codeChange, confirmAction]` and `edit_request` branch with `clear: [codeChange, confirmAction]`. `ConfigurableWorkflowRunner` applies `result.getClearKeys()` to state before `setStepIndex`; `BranchStep` parses `clear` from branch config and returns `StepResult.goTo(next, clearKeys)`. **Implement may no-op for code** unless drift is found.
- **Specs/mkdoc alignment (optional):** REQ-WORKFLOW-001 already has acceptance for StepResult clearKeys and BranchStep `clear` config. REQ-LUNA-001 does not explicitly state that edit_request clears codeChange and confirmAction and edit_repo clears project, codeChange, confirmAction. If acceptance or traceability alignment is desired: add one acceptance criterion under REQ-LUNA-001 (e.g. luna_cursor confirmation branch edit_request clears codeChange and confirmAction; edit_repo clears project, codeChange, confirmAction). mkdoc cursor-gathering change-log already has a 2026-03-09 entry for edit-reprompt; workflow-steps summary could mention optional `clear` on branch steps for discoverability.

## Per feature

### FEAT-WORKFLOW (workflow)

- **Feature:** Workflow runners and session lifecycle. Status: active. doc_path: features/domain/workflow/workflow.md.
- **Requirements:** REQ-WORKFLOW-001 — Workflow state machine and config-driven runners. Statement: WorkflowRunner runs workflow; StepResult may carry clearKeys; runner applies clearKeys to state before setStepIndex (edit-reprompt). BranchStep branch config may include clear (list of state keys); StepResult carries clearKeys for runner to apply.
- **Acceptance (excerpt):** StepResult may carry clearKeys; runner applies clearKeys before setStepIndex; BranchStep clear config; returned StepResult carries clearKeys.
- **Assets:** ASSET-CONFIGURABLE-WORKFLOW-RUNNER (applies clearKeys to state before setStepIndex), ASSET-CONFIGURABLE-WORKFLOW-STATE (clearKeys(keys)), ASSET-STEP-RESULT (clearKeys), ASSET-BRANCH-STEP (parses clear, returns goTo with clearKeys).

### FEAT-WORKFLOW-STEPS (workflow-steps)

- **Feature:** Workflow step DSL and branching. doc_path: features/domain/workflow/workflow-steps.md.
- **Requirements:** REQ-WORKFLOW-001 (same as above).
- **Assets:** ASSET-BRANCH-STEP, ASSET-STEP-RESULT. Branch step supports optional `clear` list in branch config for edit-reprompt flows.

### FEAT-CURSOR-GATHERING (cursor-gathering)

- **Feature:** Cursor-backed gathering workflow. doc_path: features/domain/workflow/cursor-gathering.md.
- **Requirements:** REQ-LUNA-001 — Luna bot; confirmation step (Launch/Edit repo/Edit request/Cancel); luna_cursor prompts and captures project and codeChange.
- **Acceptance (excerpt):** Workflow luna_cursor prompts and captures project and codeChange; confirmation with choices. (No explicit criterion yet for edit_request vs edit_repo clear sets.)
- **Anti_patterns:** Hardcoding Discord channel; storing secrets in state; do not rely on discordTrigger alone for Luna activation.
- **Assets:** ASSET-BOTS-YAML (luna_cursor branch config with clear for edit_repo and edit_request), ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-BRANCH-STEP, ASSET-STEP-RESULT.
- **Doc excerpts (cursor-gathering):** Summary mentions confirmation step (Launch / Edit repo / Edit request / Cancel). how-it-works: confirmation step with summary; Edit repo/Edit request reprompt behavior not explicitly spelled out. change-log 2026-03-09: "Edit-reprompt and config alignment; StepResult.clearKeys supports edit-reprompt; config/bots.yaml and workflow runner/state assets updated for consistency."

## Schema constraints

- Requirement keys: id, title, statement, status, acceptance, traceability, validation, anti_patterns (existing).
- Asset keys: id, kind, path, role, requires, feature_ids (existing). No new keys.
