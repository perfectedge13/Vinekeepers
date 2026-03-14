# Contracts

# APIs

None. Workflow steps are internal runtime components loaded from YAML configuration.

# Schemas

`WorkflowDefinition` stores an id plus ordered step configs. `StepResult` carries message, stored values, next-step data, `StepOutcome`, and optional `OutboundResponse` (richReply). Step config may include `intent` (e.g. present_choices, confirm_action), `choices`, `confirmLabel`, `cancelLabel`, or `fields`; when present, the step may produce an `OutboundResponse` with a `ResponseIntent` for the connector to render.

# Interfaces

- **`WorkflowStep`:** executes one configured step against the current event and state. For **capture_field**, optional **transforms** (list: trim, lower, upper, default); **trimAndLower: true** maps to [trim, lower] and **overrides** transforms when both are present. **extract_event** step: fromEvent list of { from, storeIn, default?, transforms? }; from = payload.\<key\> or context.\<field\>; list-valued context stored as List. For **branch**, when may include operator and transform (state value only; trim, lower, upper); operators: equals, not_equals, blank, nonblank, contains, starts_with, regex (full-string), one_of.
- **`WorkflowAction`:** named action contract invokable from the DSL.
- **`WorkflowActionRegistry`:** resolves workflow actions by name for `call_action` steps. For `call_action`, **bind has precedence over state** for action inputs (explicit step bind overrides state for the same keys). Registered lifecycle room actions: `create_channel` (returns channel id or `CHANNEL_CREATE_FAILED`; channel name derived from state when blank and **normalized** to Discord-safe format before gateway create), `post_channel_message` (interpolates `content` from merged state + bind, bind overrides—e.g. `lifecycleBotName` in bind for `{{lifecycleBotName}}` in content), `provision_bot_instance` (generic instance id), `create_lifecycle_context`, `launch_cursor_run` (authoritative launch path for Luna; uses `CursorInstructionComposer` for prompt; returns acknowledgement that may include optional status e.g. "Status: launching"). Workflows can branch on `channelId == CHANNEL_CREATE_FAILED` after `create_channel`.

