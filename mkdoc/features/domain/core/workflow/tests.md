# Tests

# Coverage

Unit: WorkflowRunnerFactory (stub, configured), WorkflowDefinition, ConfigurableWorkflowState, AskForInputStep, CallActionStep, DoneStep. Manual: workflow part of bot definition (compile).

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-WORKFLOW-RUNNER-FACTORY | WorkflowRunnerFactoryTest | com.vinekeepers.workflow.WorkflowRunnerFactoryTest | — | Verify factory creates runners by type (stub, configured) |
| UNIT-WORKFLOW-DEFINITION | WorkflowDefinitionTest | com.vinekeepers.workflow.WorkflowDefinitionTest | — | Verify WorkflowDefinition id and steps structure |
| UNIT-CONFIGURABLE-WORKFLOW-STATE | ConfigurableWorkflowStateTest | com.vinekeepers.workflow.ConfigurableWorkflowStateTest | — | Verify ConfigurableWorkflowState step index and key-value store |
| UNIT-ASK-FOR-INPUT-STEP | AskForInputStepTest | com.vinekeepers.workflow.steps.AskForInputStepTest | — | Verify AskForInputStep stores user input in state |
| UNIT-CALL-ACTION-STEP | CallActionStepTest | com.vinekeepers.workflow.steps.CallActionStepTest | — | Verify CallActionStep invokes registered action |
| UNIT-DONE-STEP | DoneStepTest | com.vinekeepers.workflow.steps.DoneStepTest | — | Verify DoneStep completes with message and template |
| MANUAL-WORKFLOW | Workflow interface | — | — | Verify workflow part of bot definition |
