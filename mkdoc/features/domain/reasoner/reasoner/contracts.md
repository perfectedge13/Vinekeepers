# Contracts

# APIs

None. `Reasoner.reason(ReasonerInput) -> ReasonerOutput`.

# Schemas

`ReasonerInput`: event, bot id, workflow context, current session state, and last normalized user message. `ReasonerOutput`: reply text, optional state patch, and zero or more `ProposedToolCall` values for engine-side execution.

# Interfaces

- **`Reasoner`:** `reason(ReasonerInput) -> ReasonerOutput`.
- **`ReasonerInput`:** normalized event-aware input model carrying workflow reply context and current persisted state.
- **`ReasonerOutput`:** reply and state mutation model returned to the engine.
- **`ProposedToolCall`:** tool name and arguments proposed by the reasoner for `ToolRunner` execution under `ToolPolicy`.

