# How it works

# Overview

Configured workflows are defined by `WorkflowDefinition` and executed as ordered `WorkflowStep` instances. Each step returns a `StepResult` with a `StepOutcome`, optional stored values, and next-step information so the runner can continue, wait, branch, complete, or surface an error.

# Flow

1. Config loads a `WorkflowDefinition` with ordered step configs.
2. `ConfigurableWorkflowRunner` resolves each config into a concrete step implementation.
3. A step executes and returns `StepResult` describing the next outcome.
4. The runner stores state updates, advances, waits, branches, or completes based on `StepOutcome`. For `branch` steps, blank or empty string values are not truthy; use value-based `when: { key, value: "" }` to route on blank input.

# Inputs and outputs

- **Inputs:** Workflow definition, current configurable workflow state, event payload (including interaction payload: interactionId, token, customId, values when kind is interaction), and optional registered actions or tools.
- **Outputs:** Step outcomes, stored state values, next-step selection, optional reply text, and optional `OutboundResponse` (richReply with `ResponseIntent`) for the engine to deliver via the connector sink.

