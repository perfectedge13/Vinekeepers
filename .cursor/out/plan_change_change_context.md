# Change context (for plan_change / implement)

## Scope

**Request-derived:** Update Luna bot to use configured workflow. Remove CursorCloudGatheringRunner and `cursor_cloud_gathering` type; add `luna_cursor` workflow in YAML; register real Cursor actions (e.g. `cursor.fullRun`) in Bootstrap; set Luna to `workflow.type: configured`, `params: { workflowRef: luna_cursor }`. Delete CursorCloudGatheringRunner class and remove its use from WorkflowRunnerFactory.

**Impacted registry:** specs/core-registry.yml (change_triggers.paths: src, specs).

**Impacted features / requirements / assets:**
- **FEAT-WORKFLOW** (REQ-WORKFLOW-001): WorkflowRunnerFactory drops `cursor_cloud_gathering` branch; types become `stub`, `configured` only. Traceability: remove ASSET-CURSOR-CLOUD-GATHERING-RUNNER from requirements and FEAT-WORKFLOW asset_ids.
- **FEAT-LUNA** (REQ-LUNA-001): Luna uses configured workflow `luna_cursor`; bots.yaml workflow block becomes `type: configured`, `params: { workflowRef: luna_cursor }`. Traceability: remove ASSET-CURSOR-CLOUD-GATHERING-RUNNER from REQ-LUNA-001 and FEAT-LUNA asset_ids; retain ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW (or doc that Luna behavior is now defined by YAML workflow), ASSET-CURSOR-ADAPTER*, ASSET-ENGINE, ASSET-BOOTSTRAP, ASSET-BOTS-YAML.
- **FEAT-CONFIG** (REQ-CONFIG-001): No schema change; config already supports workflow.type and workflow.params; usage change only (Luna example becomes configured + workflowRef).

**Removal:** ASSET-CURSOR-CLOUD-GATHERING-RUNNER — delete file `src/main/java/com/vinekeepers/workflow/CursorCloudGatheringRunner.java`. **removal_or_rename: true.**

---

## Per feature

### Feature: Workflow (FEAT-WORKFLOW)

- **Feature:** title "Workflow state machine and config-driven runners", status active, doc_path mkdoc/features/domain/core/workflow.md. Summary: WorkflowRunner runs workflow per bot; WorkflowRunnerFactory creates runners from workflow.type (stub, configured; cursor_cloud_gathering removed).
- **Requirements:** REQ-WORKFLOW-001 — WorkflowRunner runs workflow for an event; WorkflowRunnerFactory creates runners from config workflow type and params (stub, configured). Acceptance: Workflow interface and WorkflowResult exist; WorkflowRunner and runner.run used by engine; WorkflowRunnerFactory creates runner by type (stub, configured); StubWorkflowRunner for default. Validation tests: UNIT-WORKFLOW-RUNNER-FACTORY (verify stub, configured; remove cursor_cloud_gathering case), others unchanged. Anti_patterns: (none on this requirement).
- **Assets (impacted):** ASSET-WORKFLOW-RUNNER-FACTORY (path: WorkflowRunnerFactory.java) — remove `cursor_cloud_gathering` case and CursorCloudGatheringRunner import/use. ASSET-CURSOR-CLOUD-GATHERING-RUNNER — **removed** (delete file). Other workflow assets unchanged.
- **Doc excerpts (mkdoc/features/domain/core/workflow):**
  - **Contracts:** WorkflowRunnerFactory create(workflowType, workflowParams, workflows, actionRegistry) → WorkflowRunner. Types: `stub`, `configured` (cursor_cloud_gathering removed). For configured, resolves WorkflowDefinition from workflowParams.workflowRef or inline steps; uses WorkflowActionRegistry for CallActionStep.
  - **Decisions:** (Add entries as needed.)
  - **Known issues:** (None.)

### Feature: Luna (FEAT-LUNA)

- **Feature:** title "Luna bot — Discord /Luna, multi-turn gather, Cursor Cloud API", status active, doc_path mkdoc/features/domain/core/luna.md. Summary: Luna id luna, trigger /Luna; uses configured workflow luna_cursor (YAML); Cursor actions (e.g. cursor.fullRun) registered in Bootstrap; engine delivers replies to Discord.
- **Requirements:** REQ-LUNA-001 — Luna bot id luna, trigger /Luna; workflow type configured with workflowRef luna_cursor in bots.yaml; state and Cursor behavior defined by luna_cursor workflow steps and registered Cursor actions; engine sends workflow reply to Discord when source is Discord. Acceptance: Bot id luna and routing discordTrigger /Luna; bots.yaml workflow type configured, params.workflowRef luna_cursor; workflow steps (e.g. ask_input, call_action) and Cursor adapter/actions used as defined in luna_cursor; Engine sends reply to Discord. Anti_patterns: Hardcoding Discord channel in workflow; storing secrets in state.
- **Assets (impacted):** ASSET-BOTS-YAML — add workflows.luna_cursor (steps as needed for Luna); Luna bot workflow: type configured, params.workflowRef luna_cursor. ASSET-BOOTSTRAP — registerCursorActions: register real Cursor actions (e.g. cursor.fullRun) in addition to or replacing cursor_cloud/echo stubs. ASSET-CURSOR-CLOUD-GATHERING-RUNNER — **removed**. ASSET-LUNA-WORKFLOW, ASSET-LUNA-STATE — may remain for backward compatibility (e.g. CursorCloudGatheringWorkflow used elsewhere) or be referenced only from docs; implement may keep or simplify per product decision.
- **Doc excerpts (mkdoc/features/domain/core/luna):**
  - **Contracts:** GatheringState: step, project, codeChangeDescription. CursorCloudAdapter: createBranch, runNovaCommit, push, createPr. (Luna behavior can be expressed via configured workflow and actions.)
  - **Decisions:** (No decision records yet.)
  - **Known issues:** (No active issues.)

### Feature: Config (FEAT-CONFIG)

- **Feature:** title "Bot config from YAML", status active, doc_path mkdoc/features/domain/core/config.md. Summary: ConfigLoader loads bot definitions; workflow.type and workflow.params parsed; Bootstrap uses WorkflowRunnerFactory per bot.
- **Requirements:** REQ-CONFIG-001 — ConfigLoader loads YAML; BotConfig; workflow.type and workflow.params in BotDefinition. No change to requirement text; example in docs changes from cursor_cloud_gathering to configured + workflowRef.
- **Assets:** ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML — no code schema change; bots.yaml content change only.

---

## Implement checklist (concise)

1. **YAML:** In config/bots.yaml, add `luna_cursor` under `workflows:` with steps (e.g. ask_input for project/change, call_action for cursor.fullRun or equivalent, done). Change Luna bot `workflow` to `type: configured`, `params: { workflowRef: luna_cursor }`.
2. **Bootstrap:** In registerCursorActions, register real Cursor actions (e.g. `cursor.fullRun`) for use by CallActionStep; keep or replace existing cursor_cloud/echo as needed.
3. **WorkflowRunnerFactory:** Remove the `case "cursor_cloud_gathering"` branch and the import/use of CursorCloudGatheringRunner. Types: stub, configured only.
4. **Delete:** CursorCloudGatheringRunner.java.
5. **Tests:** WorkflowRunnerFactoryTest — remove tests that assert create("cursor_cloud_gathering", ...) returns CursorCloudGatheringRunner; keep stub and configured cases. ConfigLoaderTest — if it asserts workflowType cursor_cloud_gathering, change to configured and workflowRef. VinekeepersEngineTest — replace registration of CursorCloudGatheringRunner for "luna" with ConfigurableWorkflowRunner (or create runner via factory with configured type and luna_cursor ref) so Luna still has a runner.
6. **Spec drift repair:** In specs/core-registry.yml: remove asset ASSET-CURSOR-CLOUD-GATHERING-RUNNER from assets list; remove from REQ-WORKFLOW-001 and REQ-LUNA-001 traceability.assets and from FEAT-WORKFLOW and FEAT-LUNA asset_ids. Update REQ-WORKFLOW-001 statement/criteria to say stub and configured only. Update REQ-LUNA-001 statement/criteria to workflow type configured, workflowRef luna_cursor.
7. **Mkdoc:** Update workflow and luna docs (change-log, how-it-works, contracts, workflow.md/luna.md key assets table) to remove CursorCloudGatheringRunner and cursor_cloud_gathering; document luna_cursor and configured Luna.

---

## Schema constraints

- Do not delete requirements; mark deprecated or adjust statement/criteria/traceability only.
- Do not add new spec keys; stay within existing req-registry schema.
- Guardrails: repair spec drift before coding; avoid anti_patterns on REQ-LUNA-001 (no hardcoding Discord channel, no secrets in state).
