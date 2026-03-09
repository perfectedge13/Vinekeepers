# Tests

# Coverage

Unit tests cover workflow definition parsing, step result helpers, workflow action registry behavior, and the shipped step implementations used by configured workflows.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-WORKFLOW-DEFINITION | WorkflowDefinitionTest | com.vinekeepers.workflow.WorkflowDefinitionTest | — | Verify `WorkflowDefinition` id and steps structure |
| UNIT-STEP-RESULT | StepResultTest | com.vinekeepers.workflow.StepResultTest | — | Verify `StepResult` factory methods and outcome normalization |
| UNIT-WORKFLOW-ACTION-REGISTRY | WorkflowActionRegistryTest | com.vinekeepers.workflow.WorkflowActionRegistryTest | — | Verify action registration, resolution, execution, and safe failure handling |
| UNIT-ASK-FOR-INPUT-STEP | AskForInputStepTest | com.vinekeepers.workflow.steps.AskForInputStepTest | — | Verify `ask_input` stores user input in state |
| UNIT-CALL-ACTION-STEP | CallActionStepTest | com.vinekeepers.workflow.steps.CallActionStepTest | — | Verify `call_action` invokes the registered action |
| UNIT-BRANCH-STEP | BranchStepTest | com.vinekeepers.workflow.steps.BranchStepTest | — | Verify `branch` step condition and value-based routing |
| UNIT-CONFIGURABLE-WORKFLOW-RUNNER | ConfigurableWorkflowRunnerTest | com.vinekeepers.workflow.ConfigurableWorkflowRunnerTest | — | Verify ConfigurableWorkflowRunner executes steps and applies clearKeys for edit-reprompt |
| UNIT-DONE-STEP | DoneStepTest | com.vinekeepers.workflow.steps.DoneStepTest | — | Verify `done` completes with message and template support |

