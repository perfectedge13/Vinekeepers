# Decisions

# Selected decisions

- Workflow execution runs before reasoner execution so structured workflow context can shape the reasoner input.
- Proposed tool calls are executed by `ToolRunner` under policy control instead of directly by the reasoner.
- Engine orchestration is documented separately from bootstrap because startup wiring and per-event execution have different responsibilities.

