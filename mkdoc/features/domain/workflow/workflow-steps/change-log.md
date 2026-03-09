# Change log

# Entries

## 2026-03-09

- **Edit-reprompt and step/runner fixes:** `StepResult` supports optional `clearKeys` so the runner can remove given state keys before advancing (edit-reprompt). `ConfigurableWorkflowRunner` applies `StepResult.clearKeys` to state before `setStepIndex`. `ConfigurableWorkflowState.clearKeys(keys)` removes given keys from state. `BranchStep` and runner/state behavior aligned with spec. New tests: `BranchStepTest`, `ConfigurableWorkflowRunnerTest`; `StepResultTest` covers factory methods and outcome normalization.

## 2026-03-08

- **Intent-based steps and rich replies:** `prompt_for_field` and related steps support optional `intent`, `choices`, `confirmLabel`, `cancelLabel`, and `fields` in config. When present, steps produce an `OutboundResponse` with a `ResponseIntent`; the engine delivers these via the connector sink (respondImmediately, sendFollowUp, updateMessage). `StepResult` and `WorkflowRunResult` carry optional `richReply` (`OutboundResponse`). `capture_field` reads from event payload `values` or `customId` for `kind: interaction` events.

## Prior

- Split the configurable workflow DSL into its own feature dossier separate from runner lifecycle concerns.
- Recorded the built-in step set and branching/action contracts from `specs/core-registry.yml`.

