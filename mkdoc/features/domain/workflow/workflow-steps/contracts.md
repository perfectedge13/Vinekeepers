# Contracts

# APIs

None. Workflow steps are internal runtime components loaded from YAML configuration.

# Schemas

`WorkflowDefinition` stores an id plus ordered step configs. `StepResult` carries message, stored values, next-step data, and `StepOutcome`.

# Interfaces

- **`WorkflowStep`:** executes one configured step against the current event and state.
- **`WorkflowAction`:** named action contract invokable from the DSL.
- **`WorkflowActionRegistry`:** resolves workflow actions by name for `call_action` steps.

