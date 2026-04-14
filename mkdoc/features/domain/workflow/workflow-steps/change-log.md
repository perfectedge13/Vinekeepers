# Change log

# Entries

## 2026-04-14

- **Per-step call_action model override:** `ConfigurableWorkflowRunner` now supports an optional `model` key on a `call_action` step. The runner injects this value into action bind args as `model` (unless bind already provides `model`), so actions can consume a step-specific model without changing other step types.

## 2026-03-13

- **capture_field trimAndLower:** `CaptureFieldFromEventStep` supports optional **trimAndLower** (boolean) in step config. When true, captured text from message content is trimmed and lowercased before storing in state (e.g. room name in arrietty_room). `ConfigurableWorkflowRunner` parses `trimAndLower` from capture_field step config and passes it to the step. Tests: `CaptureFieldFromEventStepTest.messageEventWithTrimAndLowerTrimsAndLowercasesContent`, `ConfigurableWorkflowRunnerTest.runCaptureFieldWithTrimAndLowerStoresTrimmedAndLowercasedValue`.
- **create_thread action:** New **CreateThreadAction** (`create_thread`): creates a Discord thread under parent channel; bind `channelId`, `threadName`; optional `storeIn` (e.g. `deliveryChannelId`) stores thread id or sentinel `THREAD_CREATE_FAILED` in state. Registered in WorkflowActionRegistry. PostChannelMessageAction send target = deliveryChannelId or channelId (bind then state); THREAD_CREATE_FAILED treated as fallback to channelId.

## 2026-03-10

- **Phase 1 correctness (docs sync):** Test list updated to include all workflow action tests from registry: CreateChannelActionTest (gateway, sentinel, normalize), PostChannelMessageActionTest (interpolation, lifecycleBotName from bind), CreateLifecycleContextActionTest (bind precedence), ProvisionBotInstanceActionTest, LaunchCursorRunActionTest (launch, ack, lifecycle room). Contracts and change-log aligned with bind precedence (bind overrides state), create_channel CHANNEL_CREATE_FAILED and normalizeChannelName, post_channel_message merged interpolation, launch_cursor_run ack with status.
- **Docs sync:** Aligned feature dossier with implement changes: CreateChannelAction, CreateLifecycleContextAction, ProvisionBotInstanceAction, LaunchCursorRunAction (LaunchCursorRunAction uses CursorInstructionComposer for prompt); LifecycleContext and LifecycleContextStore; CursorFullRunTool and config/bots.yaml. No code change in workflow-steps; documentation reflects current registry.

## 2026-03-09

- **Lifecycle room workflow actions:** Registered actions for Phase 1: `create_channel` (Discord gateway createTextChannel; returns `CHANNEL_CREATE_FAILED` on failure; room naming from state when blank), `post_channel_message`, `provision_bot_instance` (generic instance id), `create_lifecycle_context`, `launch_cursor_run` (authoritative for Luna; prompt via CursorInstructionComposer). Branch step used after create_channel to handle CHANNEL_CREATE_FAILED. CallActionStep invokes these via WorkflowActionRegistry when referenced in configured workflows (e.g. luna_cursor full provisioning sequence).
- **Edit-reprompt and step/runner fixes:** `StepResult` supports optional `clearKeys` so the runner can remove given state keys before advancing (edit-reprompt). `ConfigurableWorkflowRunner` applies `StepResult.clearKeys` to state before `setStepIndex`. `ConfigurableWorkflowState.clearKeys(keys)` removes given keys from state. `BranchStep` and runner/state behavior aligned with spec. New tests: `BranchStepTest`, `ConfigurableWorkflowRunnerTest`; `StepResultTest` covers factory methods and outcome normalization.

## 2026-03-08

- **Intent-based steps and rich replies:** `prompt_for_field` and related steps support optional `intent`, `choices`, `confirmLabel`, `cancelLabel`, and `fields` in config. When present, steps produce an `OutboundResponse` with a `ResponseIntent`; the engine delivers these via the connector sink (respondImmediately, sendFollowUp, updateMessage). `StepResult` and `WorkflowRunResult` carry optional `richReply` (`OutboundResponse`). `capture_field` reads from event payload `values` or `customId` for `kind: interaction` events.

## Prior

- Split the configurable workflow DSL into its own feature dossier separate from runner lifecycle concerns.
- Recorded the built-in step set and branching/action contracts from `specs/core-registry.yml`.

