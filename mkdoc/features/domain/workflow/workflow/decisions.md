# Decisions

# Entries

## 2026-03-07 — Persist configurable workflows by resolved session key

Context: Configured workflows now support pause and resume across separate events instead of treating each event as a fresh execution.

Decision: `WorkflowRunnerFactory` passes bot runtime conversation settings into `ConfigurableWorkflowRunner`, which resolves a session key strategy and persists `ConfigurableWorkflowState` between events.

Consequence: Multi-turn flows can safely resume in the correct channel, thread, or channel-user session, but bot configuration must pick a strategy that matches the connector event shape.

