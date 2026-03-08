# Contracts

# APIs

None. `Workflow.handle(event, context, state) -> WorkflowResult` remains the legacy contract, while `WorkflowRunner.runResult(event, stateStore, botId)` is the engine-facing entrypoint for stateful workflow execution.

# Schemas

`WorkflowResult&lt;S&gt;`: legacy next-state plus actions and done flag. `WorkflowRunResult`: reply text plus waiting, completed, error, and waiting-field metadata. `ConfigurableWorkflowState`: current step, stored values, waiting metadata, and completion or error lifecycle data. `StepResult`: next-step and storage directives plus a `StepOutcome` of continue, waiting, complete, or error.

# Interfaces

- **`Workflow&lt;S&gt;`:** `handle(Event, BotContext, S) -> WorkflowResult&lt;S&gt;`.
- **`WorkflowRunner`:** `runResult(Event, StateStore, String botId) -> WorkflowRunResult`. Loads and saves state around a single event.
- **`WorkflowRunnerFactory`:** creates `stub` and `configured` runners from bot workflow type, params, and runtime conversation settings.
- **`SessionKeyStrategy`:** resolves the persistence key for a conversation. **`SessionKeyStrategies`:** built-in strategies such as `channel`, `channel_user`, and `thread`.
- **`ConfigurableWorkflowRunner`:** executes `WorkflowDefinition` steps in order, pauses for later events when required, and resumes from persisted `ConfigurableWorkflowState`.
- **`WorkflowActionRegistry`:** registers and resolves legacy workflow actions when a `call_action` id is not handled by `ToolRunner`.
- **`WorkflowStep`:** interface for step implementations. **`StepResult`:** per-step result object. **`StepOutcome`:** enum describing continue, waiting, complete, or error outcomes.
- **Step types:** `AskForInputStep`, `PromptForFieldStep`, `CaptureFieldFromEventStep`, `CallActionStep`, `BranchStep`, and `DoneStep`.

