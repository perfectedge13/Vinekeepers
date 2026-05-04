# Workflow step DSL and branching actions

# Status

active

# Summary

Workflow step DSL and branching actions (REQ-WORKFLOW-001). `WorkflowDefinition` describes configured flows; built-in step types implement prompt, capture, branching, tool-backed action, and completion. **Intent and dynamic choices:** `prompt_for_field` may include optional `intent` (e.g. `present_choices`, `confirm_action`), `choices`, `choiceProvider` (e.g. `githubRepos`), `confirmLabel`, `cancelLabel`, or `fields`; when present the step produces an `OutboundResponse` that the engine delivers via the connector sink. Prompt text supports `{{key}}` interpolation from state. **Capture:** `capture_field` reads from message content or, for `kind: interaction`, normalizes payload `values` (Map or List) and reads selected value or `customId`. Optional **trimAndLower** (boolean) trims and lowercases captured text before storing (e.g. for room name UX). **Branch:** `branch` supports `when: else` or state-key truthy, or `when: { key, value }` for value-based routing. Blank or empty string is not truthy for state-key branches; to route on blank input use `when: { key, value: "" }` or capture a literal (e.g. "blank") and branch on that value. **LLM model per workflow step:** for YAML-configured `call_action` steps that directly invoke LLM work (`launch_cursor_run`, `cursor.fullRun`), model resolution uses step override (`model`/`modelOverride`) first, then workflow `defaultModel` (or bot model default), then `CURSOR_MODEL`; unsupported model ids and invalid model placement on non-LLM steps fail fast during runner construction. **create_thread:** Call-action step type that creates a Discord thread under a parent channel; bind `channelId`, `threadName`; optional `storeIn` (e.g. `deliveryChannelId`) stores the thread id or sentinel `THREAD_CREATE_FAILED` in state.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-STEP-RESULT | Result of a single workflow step execution with next-step, storage, outcome metadata, and optional OutboundResponse richReply | src/main/java/com/vinekeepers/workflow/StepResult.java |
| ASSET-STEP-OUTCOME | Outcome enum for continue, waiting, complete, and error workflow step states | src/main/java/com/vinekeepers/workflow/StepOutcome.java |
| ASSET-WORKFLOW-STEP | Single step in a configurable workflow | src/main/java/com/vinekeepers/workflow/WorkflowStep.java |
| ASSET-WORKFLOW-ACTION | Action interface invokable from workflow steps | src/main/java/com/vinekeepers/workflow/WorkflowAction.java |
| ASSET-WORKFLOW-ACTION-REGISTRY | Register and resolve workflow actions by name | src/main/java/com/vinekeepers/workflow/WorkflowActionRegistry.java |
| ASSET-WORKFLOW-DEFINITION | Workflow definition from config (id and list of step configs) | src/main/java/com/vinekeepers/workflow/WorkflowDefinition.java |
| ASSET-ASK-FOR-INPUT-STEP | Step that asks for user input and stores in state | src/main/java/com/vinekeepers/workflow/steps/AskForInputStep.java |
| ASSET-PROMPT-FOR-FIELD-STEP | Step that prompts once for a field and pauses until a later event; optional intent/choices/choiceProvider; interpolates {{key}} from state | src/main/java/com/vinekeepers/workflow/steps/PromptForFieldStep.java |
| ASSET-CAPTURE-FIELD-STEP | Step that captures a field from the current event; for kind interaction normalizes payload.values (Map or List) and reads selected value or customId; optional trimAndLower trims and lowercases captured text | src/main/java/com/vinekeepers/workflow/steps/CaptureFieldFromEventStep.java |
| ASSET-CALL-ACTION-STEP | Step that invokes a registered `WorkflowAction` or `ToolRunner`-backed tool with bound arguments | src/main/java/com/vinekeepers/workflow/steps/CallActionStep.java |
| ASSET-BRANCH-STEP | Step that branches by condition (state key truthy or when: { key, value } for value-based branch) | src/main/java/com/vinekeepers/workflow/steps/BranchStep.java |
| ASSET-DYNAMIC-CHOICE-PROVIDER | Interface for dynamic choice providers used by prompt_for_field when choiceProvider is set | src/main/java/com/vinekeepers/workflow/DynamicChoiceProvider.java |
| ASSET-DONE-STEP | Step that completes workflow with optional message | src/main/java/com/vinekeepers/workflow/steps/DoneStep.java |
| ASSET-CREATE-CHANNEL-ACTION | Workflow action to create a Discord text channel via gateway | src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java |
| ASSET-POST-CHANNEL-MESSAGE-ACTION | Workflow action to post a message to a Discord channel; send target = deliveryChannelId or channelId (bind then state); THREAD_CREATE_FAILED falls back to channelId | src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java |
| ASSET-PROVISION-BOT-INSTANCE-ACTION | Workflow action to provision a runtime bot instance (e.g. Arrietty) bound to a channel | src/main/java/com/vinekeepers/workflow/actions/ProvisionBotInstanceAction.java |
| ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION | Workflow action to create and store a lifecycle context for a run | src/main/java/com/vinekeepers/workflow/actions/CreateLifecycleContextAction.java |
| ASSET-CREATE-THREAD-ACTION | Workflow action to create a Discord thread under parent channel; bind channelId, threadName; storeIn (e.g. deliveryChannelId) stores thread id or THREAD_CREATE_FAILED | src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java |
| ASSET-LAUNCH-CURSOR-RUN-ACTION | Workflow action to launch a Cursor cloud run and register run record | src/main/java/com/vinekeepers/workflow/actions/LaunchCursorRunAction.java |

# Sub-pages

- [How it works](workflow-steps/how-it-works.md)
- [Change log](workflow-steps/change-log.md)
- [Known issues](workflow-steps/known-issues.md)
- [Decisions](workflow-steps/decisions.md)
- [Contracts](workflow-steps/contracts.md)
- [Tests](workflow-steps/tests.md)
- [Diagrams](workflow-steps/diagrams.md)

