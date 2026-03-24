# Change log

# Entries

## 2026-03-23

- **Coordinator planning stabilization:** **`PlanningCyclePipeline`**, **`PlanningEvaluationService`**, **`RunLlmPlanningSynthesisAction`**, **`HydratePlanningSessionAction`**, **`StartCoordinatorPlanningAction`**, **`InitializeFeaturePlanStateAction`**, and **`PlanningUserFacingCopy`** align typed synthesis failure handling, evaluation-backed routing, and hydrate spreads with **`FeaturePlanState`** planner recovery fields and Router/engine coordinator-exclusive intake behavior. Specs: **workflow-registry**, **state-registry**, **core-registry**, **bot-registry**; tests include **RunLlmPlanningSynthesisActionJsonTest**.
- **Planning evaluation crash fix:** **`PlanningCyclePipeline`** now normalizes blank synthesis categories before immediate-failure checks, so schema-mismatch recovery paths no longer throw on `.isBlank()`. **`RunLlmPlanningSynthesisAction`** also presents artifact/section-shaped synthesis context plus a wrong-vs-right section-id example so rejected upserts fail visibly without nudging the model toward invalid `sectionId` values.

## 2026-03-07

- Configurable workflow execution now supports multi-turn conversational sessions with `prompt_for_field` and `capture_field`, bot-level `sessionKeyStrategy`, and explicit `StepOutcome` plus `WorkflowRunResult` lifecycle flags. `CallActionStep` can invoke registered tools through `ToolRunner` before falling back to legacy workflow actions, and tests now cover pause and resume behavior plus per-session isolation.

## Config-driven workflow (configured type, DSL, ActionRegistry)

- Added workflow.type **configured**: WorkflowRunnerFactory creates ConfigurableWorkflowRunner from workflow.params.workflowRef (into YAML workflows section) or inline steps.
- YAML **workflows:** section: id → steps (ask_input, call_action, branch, done). ConfigLoader loads and passes to factory.
- **WorkflowActionRegistry**: register/resolve actions; Bootstrap registers Cursor actions (cursor_cloud, echo). CallActionStep invokes by id.
- Step types: AskForInputStep, CallActionStep, BranchStep, DoneStep; ConfigurableWorkflowState (map + step index); WorkflowDefinition.

## 2026-03-07

- Introduced WorkflowRunner interface and WorkflowRunnerFactory; Engine uses registered runners (runner.run(event, stateStore, botId)) instead of invoking Workflow directly.
- Added StubWorkflowRunner; factory creates runner by type (stub, configured) from bot config. Luna uses configured workflow luna_cursor; CursorCloudGatheringRunner removed.
- Renamed LunaGatheringWorkflow → CursorCloudGatheringWorkflow, LunaConversationState → GatheringState (spec and docs).

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

