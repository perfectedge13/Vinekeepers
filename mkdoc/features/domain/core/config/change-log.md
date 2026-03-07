# Change log

# Entries

## 2026-03-07

ConfigLoader parses workflow.type and workflow.params from YAML into BotDefinition; loads top-level **workflows:** section (id → steps: ask_input, call_action, branch, done) for type `configured`. Bootstrap uses WorkflowRunnerFactory to create runner per bot (stub or configured from workflowRef/inline steps). Luna uses configured workflowRef luna_cursor.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).
