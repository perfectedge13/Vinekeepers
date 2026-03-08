# Change log

# Entries

## 2026-03-07

- `ToolRunner` is now part of both workflow and reasoner execution. Configured workflows can resolve registered tools directly from `call_action`, and reasoners can emit `ProposedToolCall` entries that the engine executes under `ToolPolicy`. Shared tools now include the legacy `cursor.fullRun` wrapper and the `echo` helper used in workflow tests.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

