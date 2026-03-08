# Decisions

# Selected decisions

- Step execution uses explicit `StepOutcome` values so waiting, completion, and errors are first-class states.
- `CallActionStep` can resolve either a registered workflow action or a `ToolRunner`-backed tool, which keeps the DSL small.
- Runner lifecycle and session persistence are documented separately because the same steps can run under different session-key strategies.

