# How it works

# Overview

`Workflow` still models legacy state-machine behavior through `handle(Event, BotContext, S state) -> WorkflowResult&lt;S&gt;`, but the engine executes workflows through a per-bot `WorkflowRunner`. `WorkflowRunner.runResult(event, stateStore, botId)` loads state, runs the workflow, persists updated state, and returns a structured `WorkflowRunResult`. Runners are created by `WorkflowRunnerFactory` from the bot's `workflow.type`, optional `workflow.params`, and runtime options from YAML. For type `configured`, the factory loads the YAML `workflows:` section (`workflowRef` or inline steps). When `workflowSchema` is `v2` and the workflow defines a `phases` map, the factory builds `GraphWorkflowRunner` (`WorkflowV2Loader` + `WorkflowRulesEngine`); otherwise it builds `ConfigurableWorkflowRunner` with step types `ask_input`, `prompt_for_field`, `capture_field`, `call_action`, `branch`, and `done`. `prompt_for_field` may include `control.mode` (e.g. `plain_text` forces a plain reply even when `intent` is `present_choices`). `StubWorkflow`, `StubState`, and `StubWorkflowRunner` are used when no custom workflow is configured.

# Flow

1. Config defines each bot with optional `workflow.type`, `workflow.params`, and runtime options such as `sessionKeyStrategy`.
2. `Bootstrap` builds a `WorkflowActionRegistry`, registers legacy workflow actions, wires `ToolRunner`, builds a `WorkflowRunner` per bot via `WorkflowRunnerFactory`, and registers it with the engine.
3. On event, the engine gets the runner for a bot id and calls `runResult(event, stateStore, botId)`.
4. `ConfigurableWorkflowRunner` (v1) resolves a session key, loads `ConfigurableWorkflowState`, executes steps in order, and returns `continue`, `waiting`, `completed`, or `error` state via `WorkflowRunResult`. `GraphWorkflowRunner` (v2) tracks `workflowV2Phase` and `workflowV2PipelineIndex` in the same state bag, runs ordered `call_action` steps per phase pipeline, applies optional rules for transitions, and completes when the phase id is `__v2_done`.
5. `PromptForFieldStep` pauses the workflow and advances the stored step index to the matching `capture_field` step so the next event resumes instead of re-prompting.
6. `CallActionStep` executes registered tools through `ToolRunner` when possible and only falls back to `WorkflowActionRegistry` when no tool exists for the action id.

# Inputs and outputs

- **Inputs:** Event, `BotContext`, and state for legacy workflows; runner inputs are `Event`, `StateStore`, and `botId`. **Outputs:** `WorkflowResult` for legacy `Workflow` implementations and `WorkflowRunResult` for engine-facing runners. `ConfigurableWorkflowState` stores step position, lifecycle flags, waiting metadata, and key-value data for the session.

