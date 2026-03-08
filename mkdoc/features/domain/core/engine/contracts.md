# Contracts

# APIs

None. The engine is an internal orchestration component.

# Schemas

`ReasonerInput` carries workflow context, current state, and the last normalized user message. `ReasonerOutput` carries reply text, optional state patches, and proposed tool calls. `WorkflowRunResult` carries workflow reply and lifecycle flags.

# Interfaces

- **`VinekeepersEngine`:** subscribes to events and coordinates bot execution.
- **`WorkflowRunner`:** engine-facing workflow execution contract.
- **`ToolRunner`:** executes approved proposed tool calls.
- **`Reasoner`:** supplies reply text, state patches, and proposed tool calls.

