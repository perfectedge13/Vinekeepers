# Workflow state machine and config-driven runners

# Status

active

# Summary

Workflow state machine and config-driven runners (REQ-WORKFLOW-001). Workflow&lt;S&gt; handle(event, context, state) returns WorkflowResult&lt;S&gt;; WorkflowRunner runs workflow for an event (load state, run, persist, return reply). WorkflowRunnerFactory creates runners from config workflow type and params: `stub` or `configured`. For `configured`, the factory builds ConfigurableWorkflowRunner from the YAML `workflows:` section (workflow.params.workflowRef or inline steps) and WorkflowActionRegistry (Bootstrap registers Cursor actions such as cursor.fullRun for CallActionStep). Luna uses configured workflow with workflowRef luna_cursor. Step types: ask_input, call_action, branch, done. Assets: WorkflowRunner, WorkflowRunnerFactory, StubWorkflowRunner, Workflow, WorkflowResult, StepResult, WorkflowStep, WorkflowAction, WorkflowActionRegistry, ConfigurableWorkflowState, WorkflowDefinition, ConfigurableWorkflowRunner, AskForInputStep, CallActionStep, BranchStep, DoneStep, StubWorkflow, StubState.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-WORKFLOW-RUNNER | Runner interface used by Engine per bot | src/main/java/com/vinekeepers/workflow/WorkflowRunner.java |
| ASSET-WORKFLOW-RUNNER-FACTORY | Creates runners from workflow.type (stub, configured) | src/main/java/com/vinekeepers/workflow/WorkflowRunnerFactory.java |
| ASSET-STUB-WORKFLOW-RUNNER | No-op runner (default when type missing or unknown) | src/main/java/com/vinekeepers/workflow/StubWorkflowRunner.java |
| ASSET-WORKFLOW | Workflow state machine interface | src/main/java/com/vinekeepers/workflow/Workflow.java |
| ASSET-WORKFLOW-RESULT | Workflow result model | src/main/java/com/vinekeepers/workflow/WorkflowResult.java |
| ASSET-STEP-RESULT | Result of a single workflow step execution | src/main/java/com/vinekeepers/workflow/StepResult.java |
| ASSET-WORKFLOW-STEP | Single step in a configurable workflow | src/main/java/com/vinekeepers/workflow/WorkflowStep.java |
| ASSET-WORKFLOW-ACTION | Action interface invokable from workflow steps | src/main/java/com/vinekeepers/workflow/WorkflowAction.java |
| ASSET-WORKFLOW-ACTION-REGISTRY | Register and resolve workflow actions by name | src/main/java/com/vinekeepers/workflow/WorkflowActionRegistry.java |
| ASSET-CONFIGURABLE-WORKFLOW-STATE | Mutable state for configurable workflow (step index, key-value store) | src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowState.java |
| ASSET-WORKFLOW-DEFINITION | Workflow definition from config (id and list of step configs) | src/main/java/com/vinekeepers/workflow/WorkflowDefinition.java |
| ASSET-CONFIGURABLE-WORKFLOW-RUNNER | Run workflow from WorkflowDefinition (steps: ask_input, call_action, branch, done) | src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java |
| ASSET-ASK-FOR-INPUT-STEP | Step that asks for user input and stores in state | src/main/java/com/vinekeepers/workflow/steps/AskForInputStep.java |
| ASSET-CALL-ACTION-STEP | Step that invokes a registered WorkflowAction | src/main/java/com/vinekeepers/workflow/steps/CallActionStep.java |
| ASSET-BRANCH-STEP | Step that branches by condition | src/main/java/com/vinekeepers/workflow/steps/BranchStep.java |
| ASSET-DONE-STEP | Step that completes workflow with optional message | src/main/java/com/vinekeepers/workflow/steps/DoneStep.java |
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
