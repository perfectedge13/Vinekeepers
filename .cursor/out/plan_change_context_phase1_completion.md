# Change context (for plan_change / implement)

## Scope

**Request-derived:** Phase 1 completion fix for Luna/lifecycle room.

- **Features:** FEAT-CURSOR-GATHERING.
- **Requirements:** REQ-LUNA-001, REQ-WORKFLOW-001, REQ-TOOLS-001.
- **Assets (impacted):** config/bots.yaml (luna_cursor workflow steps), LifecycleContext, LifecycleContextStore, CreateChannelAction, ProvisionBotInstanceAction, CreateLifecycleContextAction, LaunchCursorRunAction, CursorFullRunTool; **new:** CursorInstructionComposer (single source for Cursor run prompt including /nova-code).
- **Removal or rename:** None. Production code and config only; tests updated in a separate update_tests step.

---

## Per feature / requirement

### Feature: FEAT-CURSOR-GATHERING

- **id:** FEAT-CURSOR-GATHERING  
- **slug:** cursor-gathering  
- **title:** Cursor-backed gathering workflow  
- **status:** active  
- **doc_path:** features/domain/workflow/cursor-gathering.md  
- **summary:** Luna gathers repository and feature input over Discord, launches an official Cursor cloud agent run, and reports progress and PR results back to chat; lifecycle room Phase 1 (context, run record, provisioning actions).

### REQ-LUNA-001

- **id:** REQ-LUNA-001  
- **title:** Luna bot — Discord mention trigger, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1  
- **statement:** Luna bot is registered with id luna; Discord activation uses discordMention: luna and optional discordAuthors; workflowRef luna_cursor in bots.yaml; luna_cursor uses guided repo selection, codeChange prompt/capture, confirmation step (Launch/Edit repo/Edit request/Cancel), then **launch path** (Phase 1: create_channel → provision_bot_instance → create_lifecycle_context → post_channel_message → launch_cursor_run); cursor.fullRun is no longer the launch path for luna_cursor after rewiring. Vinekeepers stores run state, LifecycleContext, RuntimeBotInstance; workflow actions include create_channel, post_channel_message, provision_bot_instance, create_lifecycle_context, launch_cursor_run.  
- **Acceptance criteria (relevant):**
  - bots.yaml specifies workflow type configured and workflowRef luna_cursor for luna.
  - Workflow luna_cursor prompts and captures project and codeChange; after Launch follows full provisioning sequence: create_channel (storeIn channelId) → branch on channelId == CHANNEL_CREATE_FAILED to done with failure message; else provision_bot_instance → create_lifecycle_context → post_channel_message → launch_cursor_run → done. launch_cursor_run is the authoritative launch path; cursor.fullRun is not used in luna_cursor after rewiring.
  - create_channel returns sentinel "CHANNEL_CREATE_FAILED" on failure; when channelName is blank, channel name is built from state project + codeChange (repo segment + slug + short suffix), Discord-valid.
  - LifecycleContext includes configuredBotId, runtimeBotInstanceId, optional repo/requestText; CreateLifecycleContextAction accepts and stores them.
  - ProvisionBotInstanceAction uses instance id prefix from templateBotId (generic), not hardcoded "arrietty-".
- **Anti-patterns (critical for implement):**
  - Hardcoding Discord channel in workflow.
  - Storing secrets in state.
  - Do not rely on discordTrigger alone for Luna activation (mentions would be missed).
  - Do not log Cursor API key, request body, or full response body.
  - Do not assume a single Cursor API error response shape.
  - Do not serialize null fields in Cursor API request payload.

### REQ-WORKFLOW-001 (excerpt)

- **statement:** WorkflowRunner runs workflow for an event; configurable workflows support call_action (workflow actions and tools), branch (value-based e.g. channelId == CHANNEL_CREATE_FAILED), done.  
- **Assets:** CreateChannelAction, ProvisionBotInstanceAction, CreateLifecycleContextAction, PostChannelMessageAction, LaunchCursorRunAction, ConfigurableWorkflowRunner, BranchStep, DoneStep.

### Assets (implementation targets)

| Asset id | Path | Role / change |
|----------|------|----------------|
| ASSET-BOTS-YAML | config/bots.yaml | Rewire luna_cursor steps: after confirm launch → create_channel (storeIn channelId) → branch channelId == CHANNEL_CREATE_FAILED → done failure; else provision_bot_instance → create_lifecycle_context → post_channel_message → launch_cursor_run → done. Remove cursor.fullRun step from luna_cursor. |
| ASSET-CREATE-CHANNEL-ACTION | …/workflow/actions/CreateChannelAction.java | When channelName blank: build name from state project + codeChange (repo segment + slug + short suffix), Discord-valid. On gateway failure return sentinel "CHANNEL_CREATE_FAILED". |
| ASSET-PROVISION-BOT-INSTANCE-ACTION | …/workflow/actions/ProvisionBotInstanceAction.java | Instance id prefix from templateBotId (e.g. "arrietty" → "arrietty-..."), not hardcoded "arrietty-". |
| ASSET-LIFECYCLE-CONTEXT | …/state/LifecycleContext.java | Extend with configuredBotId, runtimeBotInstanceId, optional repo, requestText. |
| ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION | …/workflow/actions/CreateLifecycleContextAction.java | Accept bind/state: channelId, configuredBotId, runtimeBotInstanceId, optional repo, requestText; pass to LifecycleContext and store. |
| ASSET-LAUNCH-CURSOR-RUN-ACTION | …/workflow/actions/LaunchCursorRunAction.java | Use CursorInstructionComposer for prompt (single source; include /nova-code). |
| ASSET-CURSOR-FULL-RUN-TOOL | …/tools/CursorFullRunTool.java | Use CursorInstructionComposer for prompt (single source; include /nova-code). cursor.fullRun remains registered for non-luna_cursor use; luna_cursor does not call it after rewiring. |
| **New** CursorInstructionComposer | …/core/cursor/CursorInstructionComposer.java or …/tools/CursorInstructionComposer.java | Single source of truth for Cursor run instruction text: build prompt from repo, baseBranch, change; include /nova-code and spec-driven workflow requirements. Used by CursorFullRunTool and LaunchCursorRunAction. |

### Doc excerpts (cursor-gathering)

**Decisions:** Luna remains config-driven in YAML. Discord mention activation belongs to routing; gathered project/code-change sequence belongs to this workflow. Repository operations delegated through launch path (launch_cursor_run as authoritative for luna_cursor) and Cursor adapter.

**Contracts:** LifecycleContext: run context (channelId, guildId, run record id; extended: configuredBotId, runtimeBotInstanceId, optional repo/requestText). LifecycleContextStore: store and resolve by key. Workflow actions: create_channel (Discord createTextChannel; returns channel id or "CHANNEL_CREATE_FAILED"), post_channel_message, provision_bot_instance (instance id prefix from templateBotId), create_lifecycle_context, launch_cursor_run. CursorInstructionComposer: single source for prompt used by LaunchCursorRunAction and CursorFullRunTool; includes /nova-code.

**Known issues:** Flow depends on external Cursor Cloud and repo integrations; run tracking in-memory for v1 (no recovery after restart).

---

## Schema constraints

- Do not add new requirement or asset schema keys; stay within existing req-registry (core-registry.yml). New asset (CursorInstructionComposer) can be added with existing keys: id, kind, path, role, requires, feature_ids.
- REQ-LUNA-001 statement/criteria may be updated to state that luna_cursor uses launch_cursor_run as the authoritative launch path and that cursor.fullRun is not used in luna_cursor after rewiring; create_channel failure sentinel; LifecycleContext fields; ProvisionBotInstanceAction prefix from templateBotId.

---

## Implementation checklist (from guardrails)

- Do not delete requirements; update wording if needed.
- Do not remove required functionality without permission.
- Do not change schema files or invent new spec keys.
- Repair spec drift before coding (e.g. REQ-LUNA-001 criteria vs config).
- Consider and avoid anti_patterns listed above.
- Feature slugs are not bot ids.
