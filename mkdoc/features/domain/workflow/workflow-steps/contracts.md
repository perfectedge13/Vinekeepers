# Contracts

# APIs

None. Workflow steps are internal runtime components loaded from YAML configuration.

# Schemas

`WorkflowDefinition` stores an id plus ordered step configs. `StepResult` carries message, stored values, next-step data, `StepOutcome`, and optional `OutboundResponse` (richReply). Step config may include `intent` (e.g. present_choices, confirm_action), `choices`, `confirmLabel`, `cancelLabel`, or `fields`; when present, the step may produce an `OutboundResponse` with a `ResponseIntent` for the connector to render.

# Interfaces

- **`WorkflowStep`:** executes one configured step against the current event and state.
- **`WorkflowAction`:** named action contract invokable from the DSL.
- **`WorkflowActionRegistry`:** resolves workflow actions by name for `call_action` steps.

