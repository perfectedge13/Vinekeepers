# Workflow step DSL and branching actions

# Status

active

# Summary

Workflow step DSL and branching actions (REQ-WORKFLOW-001). `WorkflowDefinition` describes configured flows; built-in step types implement prompt, capture, branching, tool-backed action, and completion. **Intent and dynamic choices:** `prompt_for_field` may include optional `intent` (e.g. `present_choices`, `confirm_action`), `choices`, `choiceProvider` (e.g. `githubRepos`), `confirmLabel`, `cancelLabel`, or `fields`; when present the step produces an `OutboundResponse` that the engine delivers via the connector sink. Prompt text supports `{{key}}` interpolation from state. **Capture:** `capture_field` reads from message content or, for `kind: interaction`, normalizes payload `values` (Map or List) and reads selected value or `customId`. Optional **transforms** (list: trim, lower, upper, default). **trimAndLower: true** maps to [trim, lower]; when both trimAndLower and transforms are present, **trimAndLower wins** (backward compatibility). **extract_event:** Step type with **fromEvent** list of { from, storeIn, default?, transforms? }. Source paths: **payload.\<key\>** (flat), **context.\<field\>** (NormalizedEventContext getters); list-valued context fields (mentions, labels, interactionValues) stored as List in state. Missing path → default or skip. **Branch:** `branch` supports `when: else`, state-key truthy, or `when: { key, value, operator?, transform? }`. Operators: equals, not_equals, blank, nonblank, contains, starts_with, regex (full-string match), one_of (value: YAML list). Transforms on branch apply to state value only (trim, lower, upper). Blank or empty: use `when: { key, value: "" }` or branch operator blank/nonblank. **create_thread:** Call-action step type that creates a Discord thread under a parent channel; bind `channelId`, `threadName`; optional `storeIn` (e.g. `deliveryChannelId`) stores the thread id or sentinel `THREAD_CREATE_FAILED` in state.

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
| ASSET-CAPTURE-FIELD-STEP | Step that captures a field from the current event; optional transforms list; when both trimAndLower and transforms present, trimAndLower wins | src/main/java/com/vinekeepers/workflow/steps/CaptureFieldFromEventStep.java |
| ASSET-CALL-ACTION-STEP | Step that invokes a registered `WorkflowAction` or `ToolRunner`-backed tool with bound arguments | src/main/java/com/vinekeepers/workflow/steps/CallActionStep.java |
| ASSET-BRANCH-STEP | Step that branches by condition; when key/value with optional operator and transform (state value only); operators equals, not_equals, blank, nonblank, contains, starts_with, regex, one_of | src/main/java/com/vinekeepers/workflow/steps/BranchStep.java |
| ASSET-WORKFLOW-TRANSFORMS | Shared string transforms (trim, lower, upper, default); null/blank-safe | src/main/java/com/vinekeepers/workflow/WorkflowTransforms.java |
| ASSET-WORKFLOW-CONDITION-EVALUATOR | Evaluates branch condition with operators and optional state-value transforms | src/main/java/com/vinekeepers/workflow/WorkflowConditionEvaluator.java |
| ASSET-EXTRACT-EVENT-FIELDS-STEP | Step that extracts event fields into state; fromEvent with payload.\<key\>, context.\<field\>; list-valued as List | src/main/java/com/vinekeepers/workflow/steps/ExtractEventFieldsStep.java |
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

