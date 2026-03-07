# Change log

# Entries

## 2026-03-07

- Step types and configurable workflow: StepResult, WorkflowStep, WorkflowAction, WorkflowActionRegistry, ConfigurableWorkflowState, WorkflowDefinition, ConfigurableWorkflowRunner. Step implementations: AskForInputStep, CallActionStep, BranchStep, DoneStep. WorkflowRunnerFactory supports type `configured`; ConfigLoader and BotConfig load workflow block and top-level workflows section; Bootstrap builds WorkflowActionRegistry and registers Cursor actions. Tests added for step types, ConfigurableWorkflowRunner, WorkflowDefinition, ConfigurableWorkflowState, StepResult, WorkflowActionRegistry; WorkflowRunnerFactoryTest and ConfigLoaderTest updated. README and traceability (REQ-WORKFLOW-001) updated.

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
