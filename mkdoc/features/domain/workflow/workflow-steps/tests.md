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
| UNIT-CALL-ACTION-STEP-LLM-MODEL | CallActionStepTest | com.vinekeepers.workflow.steps.CallActionStepTest | executeLlmBackedAddsResolvedModelToActionBindArgs | Verify LLM-backed `call_action` injects resolved per-step model into action args |
| UNIT-CONFIGURABLE-WORKFLOW-RUNNER-STEP-MODEL | ConfigurableWorkflowRunnerTest | com.vinekeepers.workflow.ConfigurableWorkflowRunnerTest | callActionWithStepModelInjectsModelForLlmBackedAction | Verify per-step `model` on LLM-backed step overrides bot default at runtime |
| UNIT-CONFIGURABLE-WORKFLOW-RUNNER-MODEL-FALLBACK | ConfigurableWorkflowRunnerTest | com.vinekeepers.workflow.ConfigurableWorkflowRunnerTest | callActionWithoutStepModelFallsBackToBotDefaultModel | Verify LLM-backed step without `model` falls back to bot default model |
| UNIT-CONFIGURABLE-WORKFLOW-RUNNER-MODEL-VALIDATION | ConfigurableWorkflowRunnerTest | com.vinekeepers.workflow.ConfigurableWorkflowRunnerTest | nonLlmStepWithModelFailsValidationAtConstruction | Verify non-LLM step with `model` fails during workflow initialization |
| UNIT-LAUNCH-CURSOR-RUN-ACTION-STEP-MODEL | LaunchCursorRunActionTest | com.vinekeepers.workflow.actions.LaunchCursorRunActionTest | runUsesPerStepModelOverrideWhenProvided | Verify launch action uses step-level model override in Cursor launch request |
| UNIT-BRANCH-STEP | BranchStepTest | com.vinekeepers.workflow.steps.BranchStepTest | — | Verify `branch` step condition and value-based routing |
| UNIT-CONFIGURABLE-WORKFLOW-RUNNER | ConfigurableWorkflowRunnerTest | com.vinekeepers.workflow.ConfigurableWorkflowRunnerTest | — | Verify ConfigurableWorkflowRunner executes steps and applies clearKeys for edit-reprompt |
| UNIT-DONE-STEP | DoneStepTest | com.vinekeepers.workflow.steps.DoneStepTest | — | Verify `done` completes with message and template support |
| UNIT-POST-CHANNEL-MESSAGE-INTERPOLATION | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | runInterpolatesContentFromBindState | Verify post_channel_message interpolates content from merged map (state then bind, bind overrides) |
| UNIT-CREATE-LIFECYCLE-CONTEXT-BIND-PRECEDENCE | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | runPrefersBindOverStateForNewFields | Verify create_lifecycle_context uses bind precedence (bind overrides state) |
| UNIT-CREATE-CHANNEL-ACTION | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | — | Verify create_channel action uses gateway createTextChannel for lifecycle room |
| UNIT-CREATE-CHANNEL-ACTION-SENTINEL | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | runReturnsChannelCreateFailedWhenCreateTextChannelReturnsNull | Verify create_channel returns CHANNEL_CREATE_FAILED on gateway failure or null |
| UNIT-CREATE-CHANNEL-ACTION-NORMALIZE | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | runNormalizesChannelNameFromBindToLowercaseDiscordSafe | Verify create_channel normalizes channel name to Discord-safe lowercase |
| UNIT-POST-CHANNEL-MESSAGE-ACTION | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | — | Verify post_channel_message action posts to Discord channel |
| UNIT-POST-CHANNEL-MESSAGE-LIFECYCLE-BOT-NAME | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | runInterpolatesLifecycleBotNameFromMergedMapBindWins | Verify post_channel_message interpolates lifecycleBotName from merged map (bind wins) |
| UNIT-PROVISION-BOT-INSTANCE-ACTION | ProvisionBotInstanceActionTest | com.vinekeepers.workflow.actions.ProvisionBotInstanceActionTest | — | Verify provision_bot_instance action provisions runtime bot instance |
| UNIT-CREATE-LIFECYCLE-CONTEXT-ACTION | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | — | Verify create_lifecycle_context action creates and stores lifecycle context |
| UNIT-CREATE-LIFECYCLE-CONTEXT-BIND-WINS | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | runConfiguredBotIdBindPrecedenceBindWinsOverState | Verify create_lifecycle_context bind precedence (bind overrides state for configuredBotId) |
| UNIT-LAUNCH-CURSOR-RUN-ACTION | LaunchCursorRunActionTest | com.vinekeepers.workflow.actions.LaunchCursorRunActionTest | runLaunchesAndStoresRecordAndBindsContext | Verify launch_cursor_run action launches run, registers run record, and acknowledges to Discord with status (e.g. launching) and lifecycle room |

