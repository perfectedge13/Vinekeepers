# Workflow runners and session lifecycle

# Status

active

# Summary

Workflow state machine and config-driven runners (REQ-WORKFLOW-001). `Workflow<S>` remains the legacy state-machine contract, while `WorkflowRunner` is the engine-facing interface that loads session state, runs a configured or stub workflow, and returns `WorkflowRunResult`. `WorkflowRunnerFactory`, `ConfigurableWorkflowRunner`, `ConfigurableWorkflowState`, and session key strategies handle pause/resume lifecycle across conversational sessions. The step DSL is documented separately in the Workflow Steps feature.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-WORKFLOW | Workflow state machine interface | src/main/java/com/vinekeepers/workflow/Workflow.java |
| ASSET-WORKFLOW-RESULT | Workflow result model | src/main/java/com/vinekeepers/workflow/WorkflowResult.java |
| ASSET-WORKFLOW-RUNNER | Interface to run workflow for an event and return a structured workflow result | src/main/java/com/vinekeepers/workflow/WorkflowRunner.java |
| ASSET-WORKFLOW-RUN-RESULT | Structured workflow result with reply, waiting/completed flags, and error details | src/main/java/com/vinekeepers/workflow/WorkflowRunResult.java |
| ASSET-STUB-WORKFLOW-RUNNER | Stub workflow runner implementation | src/main/java/com/vinekeepers/workflow/StubWorkflowRunner.java |
| ASSET-WORKFLOW-RUNNER-FACTORY | Create `WorkflowRunner` from workflow type, params, and bot runtime options from config | src/main/java/com/vinekeepers/workflow/WorkflowRunnerFactory.java |
| ASSET-SESSION-KEY-STRATEGY | Contract for resolving per-bot workflow session keys from events | src/main/java/com/vinekeepers/workflow/SessionKeyStrategy.java |
| ASSET-SESSION-KEY-STRATEGIES | Built-in session key strategies for channel, channel_user, and thread conversations | src/main/java/com/vinekeepers/workflow/SessionKeyStrategies.java |
| ASSET-CONFIGURABLE-WORKFLOW-STATE | Mutable state for configurable workflow including waiting, completed, and error lifecycle data | src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowState.java |
| ASSET-CONFIGURABLE-WORKFLOW-RUNNER | Run workflow from `WorkflowDefinition` with conversational pause/resume, session keys, and tool-backed actions | src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java |
| ASSET-STUB-WORKFLOW | Stub workflow implementation | src/main/java/com/vinekeepers/workflow/StubWorkflow.java |
| ASSET-STUB-STATE | Stub workflow state | src/main/java/com/vinekeepers/workflow/StubState.java |

# Sub-pages

- [How it works](workflow/how-it-works.md)
- [Change log](workflow/change-log.md)
- [Known issues](workflow/known-issues.md)
- [Decisions](workflow/decisions.md)
- [Contracts](workflow/contracts.md)
- [Tests](workflow/tests.md)
- [Diagrams](workflow/diagrams.md)

