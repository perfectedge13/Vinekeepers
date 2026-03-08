# Workflow step DSL and branching actions

# Status

active

# Summary

Workflow step DSL and branching actions (REQ-WORKFLOW-001). `WorkflowDefinition` describes configured flows, while `WorkflowStep`, `StepResult`, `StepOutcome`, `WorkflowAction`, and the built-in step types implement prompt, capture, branching, tool-backed action, and completion behavior.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-STEP-RESULT | Result of a single workflow step execution with next-step, storage, and outcome metadata | src/main/java/com/vinekeepers/workflow/StepResult.java |
| ASSET-STEP-OUTCOME | Outcome enum for continue, waiting, complete, and error workflow step states | src/main/java/com/vinekeepers/workflow/StepOutcome.java |
| ASSET-WORKFLOW-STEP | Single step in a configurable workflow | src/main/java/com/vinekeepers/workflow/WorkflowStep.java |
| ASSET-WORKFLOW-ACTION | Action interface invokable from workflow steps | src/main/java/com/vinekeepers/workflow/WorkflowAction.java |
| ASSET-WORKFLOW-ACTION-REGISTRY | Register and resolve workflow actions by name | src/main/java/com/vinekeepers/workflow/WorkflowActionRegistry.java |
| ASSET-WORKFLOW-DEFINITION | Workflow definition from config (id and list of step configs) | src/main/java/com/vinekeepers/workflow/WorkflowDefinition.java |
| ASSET-ASK-FOR-INPUT-STEP | Step that asks for user input and stores in state | src/main/java/com/vinekeepers/workflow/steps/AskForInputStep.java |
| ASSET-PROMPT-FOR-FIELD-STEP | Step that prompts once for a field and pauses the workflow until a later event arrives | src/main/java/com/vinekeepers/workflow/steps/PromptForFieldStep.java |
| ASSET-CAPTURE-FIELD-STEP | Step that captures a field from the current event after a conversational prompt | src/main/java/com/vinekeepers/workflow/steps/CaptureFieldFromEventStep.java |
| ASSET-CALL-ACTION-STEP | Step that invokes a registered `WorkflowAction` or `ToolRunner`-backed tool with bound arguments | src/main/java/com/vinekeepers/workflow/steps/CallActionStep.java |
| ASSET-BRANCH-STEP | Step that branches by condition | src/main/java/com/vinekeepers/workflow/steps/BranchStep.java |
| ASSET-DONE-STEP | Step that completes workflow with optional message | src/main/java/com/vinekeepers/workflow/steps/DoneStep.java |

# Sub-pages

- [How it works](workflow-steps/how-it-works.md)
- [Change log](workflow-steps/change-log.md)
- [Known issues](workflow-steps/known-issues.md)
- [Decisions](workflow-steps/decisions.md)
- [Contracts](workflow-steps/contracts.md)
- [Tests](workflow-steps/tests.md)
- [Diagrams](workflow-steps/diagrams.md)

