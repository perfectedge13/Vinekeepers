# Change log

# Entries

## 2026-03-07

- `ReasonerInput` now carries workflow context, current state, and the last normalized user message so post-workflow reasoning can use conversational context. `ReasonerOutput` can return state patches and `ProposedToolCall` entries that the engine executes through `ToolRunner` under bot policy.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

