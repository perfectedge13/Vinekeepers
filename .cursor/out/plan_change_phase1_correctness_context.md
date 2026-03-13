# Change context (for plan_change / implement)

## Scope

**Request:** Implement the Phase 1 correctness fix pass. Changes already applied: CreateLifecycleContextAction (bind precedence comment + test), PostChannelMessageAction (merged interpolation, lifecycleBotName), CreateChannelAction (normalizeChannelName, try/catch CHANNEL_CREATE_FAILED), LaunchCursorRunAction (ack/status), config/bots.yaml (lifecycleBotName in bind/content).

**Scoped features:** FEAT-CURSOR-GATHERING, FEAT-CONFIG, FEAT-WORKFLOW-STEPS.

**Scoped requirements:** REQ-LUNA-001, REQ-CONFIG-001, REQ-WORKFLOW-001.

**Scoped assets:** ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-CREATE-CHANNEL-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION, ASSET-BOTS-YAML.

---

## Per feature

### FEAT-CURSOR-GATHERING

**Feature:** id FEAT-CURSOR-GATHERING, slug cursor-gathering, title "Cursor-backed gathering workflow", status active, doc_path features/domain/workflow/cursor-gathering.md. Summary: Luna gathers repository and feature input over Discord, launches an official Cursor cloud agent run via launch_cursor_run (CursorInstructionComposer prompt source); lifecycle room Phase 1 (extended context, run record, provisioning actions, create_channel sentinel and room naming).

**Requirements (REQ-LUNA-001):** Luna bot — Discord mention trigger, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1. Statement (one line): Luna is registered with id luna; Discord activation uses discordMention: luna and optional discordAuthors; workflowRef luna_cursor; luna_cursor uses guided repo selection, codeChange prompt/capture, confirmation step, then launch_cursor_run as authoritative launch path; CursorInstructionComposer is single prompt source including /nova-code; Phase 1 lifecycle room includes LifecycleRunRecord, LifecycleContext, LifecycleContextStore, RuntimeBotInstance, workflow actions create_channel (sentinel CHANNEL_CREATE_FAILED, room naming from state or fallback), post_channel_message, provision_bot_instance, create_lifecycle_context, launch_cursor_run. Acceptance (short): Bot luna and routing discordMention luna; optional discordAuthors; bots.yaml workflowRef luna_cursor; launch_cursor_run resolves repo, uses CursorInstructionComposer, launches API, stores LifecycleRunRecord, acknowledges to Discord; create_channel returns channel id or CHANNEL_CREATE_FAILED; confirmation branch clear for edit_request/edit_repo. Validation tests: UNIT-CREATE-CHANNEL-ACTION, UNIT-POST-CHANNEL-MESSAGE-ACTION, UNIT-CREATE-LIFECYCLE-CONTEXT-ACTION, UNIT-LAUNCH-CURSOR-RUN-ACTION, plus Cursor adapter and Luna workflow tests. **Anti-patterns:** Hardcoding Discord channel in workflow; storing secrets in state; do not rely on discordTrigger alone for Luna activation; do not log Cursor API key, request body, or full response body; do not assume a single Cursor API error response shape; do not serialize null fields in Cursor API request payload.

**Assets (impacted):** ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION (path src/main/java/com/vinekeepers/workflow/actions/CreateLifecycleContextAction.java, role: create and store lifecycle context; bind precedence: state then bind, bind overrides), ASSET-POST-CHANNEL-MESSAGE-ACTION (PostChannelMessageAction.java, post message to Discord; interpolation uses merged map state then bind, lifecycleBotName from bind wins), ASSET-CREATE-CHANNEL-ACTION (CreateChannelAction.java, create Discord channel; normalizeChannelName for room naming; try/catch returns CHANNEL_CREATE_FAILED on failure), ASSET-LAUNCH-CURSOR-RUN-ACTION (LaunchCursorRunAction.java, launch run and register record; ack/status to Discord), ASSET-BOTS-YAML (config/bots.yaml, lifecycleBotName in bind/content for luna_cursor steps).

**Doc excerpts (mkdoc/):**

- **contracts.md:** LifecycleContext run context with channelId, configuredBotId, runtimeBotInstanceId, optional repo/requestText. create_channel returns channel id or CHANNEL_CREATE_FAILED; channel name derived from state when blank. post_channel_message, create_lifecycle_context, launch_cursor_run. CursorInstructionComposer single source for prompt; used by LaunchCursorRunAction and CursorFullRunTool.
- **decisions.md:** Luna remains config-driven in YAML; Discord mention activation in routing; repository operations via cursor adapter.
- **known-issues.md:** Flow depends on external Cursor/repo integrations; run tracking in-memory for v1.
- **change-log.md:** Phase 1 completion documents create_channel sentinel, room naming from state, LifecycleContext extension, provisioning sequence. Phase 1 correctness: bind precedence for create_lifecycle_context; merged interpolation and lifecycleBotName for post_channel_message; normalizeChannelName and CHANNEL_CREATE_FAILED handling for create_channel; ack/status for launch_cursor_run; lifecycleBotName in config/bots.yaml bind/content.

### FEAT-CONFIG

**Feature:** FEAT-CONFIG, slug config, title "Bot config from YAML", status active, doc_path features/domain/config/config.md. Summary: ConfigLoader loads bot definitions from YAML; BotConfig models the structure.

**Relevant requirement:** REQ-CONFIG-001. Assets: ASSET-BOTS-YAML (config/bots.yaml). Config must support workflow step bind keys such as lifecycleBotName and content with {{lifecycleBotName}} interpolation.

### FEAT-WORKFLOW-STEPS

**Feature:** FEAT-WORKFLOW-STEPS, slug workflow-steps, title "Workflow step DSL and branching actions", status active, doc_path features/domain/workflow/workflow-steps.md. Summary: WorkflowDefinition and step/action primitives; CallActionStep invokes registered actions with bound arguments.

**Relevant requirement:** REQ-WORKFLOW-001. Assets: ASSET-CREATE-CHANNEL-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION. Step bind and state are passed to actions; interpolation and precedence (state then bind, bind overrides) apply in actions that render content.

---

## Schema constraints

Requirement/asset keys follow req-registry schema (id, title, statement, status, acceptance.criteria, traceability.assets, validation.tests). No new spec keys; no deletion of active requirements. Guardrails: repair spec drift before coding; avoid anti-patterns on REQ-LUNA-001; do not delete requirements or invent new registry keys.
