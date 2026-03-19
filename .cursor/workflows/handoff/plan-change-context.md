# Change context (for plan_change / implement)

## Scope

**Request-derived:** Workflow capability seam hardening and request shaping. Impacted: **FEAT-WORKFLOW-STEPS**, **FEAT-CURSOR-GATHERING**, **FEAT-CONNECTORS-DISCORD**. Requirements: **REQ-WORKFLOW-001**, **REQ-LUNA-001**, **REQ-CONNECTORS-DISCORD-001**. Assets: **ASSET-CREATE-CHANNEL-ACTION**, **ASSET-CREATE-THREAD-ACTION**, **ASSET-SPACE-OPERATIONS**, **ASSET-SPACE-OPERATIONS-REGISTRY**, **ASSET-DISCORD-SPACE-OPERATIONS**; new types **CreateRoomRequest**, **CreateThreadRequest** (for SpaceOperations); optional shared **sourcePrefix** helper.

## Per feature

### FEAT-WORKFLOW-STEPS (workflow-steps)

- **Feature:** Workflow step DSL and branching actions. Status: active. Doc: `mkdoc/features/domain/workflow/workflow-steps.md`.
- **Requirements:** REQ-WORKFLOW-001 — Workflow state machine and config-driven runners. Statement: Workflow/WorkflowRunner/WorkflowRunnerFactory; configurable workflows with session keys; CallActionStep invokes registered action or tool; create_channel and create_thread delegate to SpaceOperations via SpaceOperationsRegistry.get(prefix) where prefix from event.getSourceId(); no connector-specific logic in actions.
- **Acceptance (relevant):** create_thread action creates Discord thread via OutboundGateway; bind channelId, threadName, optional contextId; blank or missing channelId returns THREAD_CREATE_FAILED; on success when contextId present updates LifecycleContextStore setDeliveryTargetId; storeIn stores thread id or THREAD_CREATE_FAILED. create_channel returns channel id or CHANNEL_CREATE_FAILED; workflow actions delegate to SpaceOperations by source prefix; no Discord parsing in action.
- **Assets (impacted):** ASSET-CREATE-CHANNEL-ACTION (src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java), ASSET-CREATE-THREAD-ACTION (src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java), ASSET-WORKFLOW-ACTION-REGISTRY.
- **Validation tests:** UNIT-CREATE-CHANNEL-ACTION, UNIT-CREATE-CHANNEL-ACTION-SENTINEL, UNIT-CREATE-CHANNEL-ACTION-NORMALIZE, UNIT-CREATE-CHANNEL-ACTION-LIFECYCLE-OWNER, UNIT-CREATE-THREAD-ACTION, UNIT-WORKFLOW-ACTION-REGISTRY. Add explicit tests: fail-closed when source prefix null/blank (return sentinel, no default); fail-closed when registry.get(prefix) returns null (return sentinel).
- **Anti-patterns (guardrails):** Do not hardcode Discord in workflow actions; consider anti_patterns on REQ-LUNA-001 below.
- **Doc excerpts (contracts):** create_channel and create_thread delegate to SpaceOperations via SpaceOperationsRegistry.get(prefix) where prefix is derived from event.getSourceId(); no connector-specific logic in the actions. WorkflowActionRegistry resolves actions by name; bind has precedence over state.

### FEAT-CURSOR-GATHERING (cursor-gathering)

- **Feature:** Cursor-backed gathering workflow. Status: active. Doc: `mkdoc/features/domain/workflow/cursor-gathering.md`.
- **Requirements:** REQ-LUNA-001 — Luna bot Discord mention trigger, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1. create_channel (Discord createTextChannel, sentinel CHANNEL_CREATE_FAILED), create_thread (delegates to SpaceOperations by source prefix; lifecycle owner gateway when channel has context; setDeliveryTargetId on success; storeIn deliveryChannelId).
- **Acceptance (relevant):** create_channel returns CHANNEL_CREATE_FAILED on gateway failure or null; room naming normalizeChannelName; create_thread uses lifecycle owner gateway when channel has context; setDeliveryTargetId on success when contextId present; blank channelId returns THREAD_CREATE_FAILED. Arrietty template with create_thread storeIn deliveryChannelId, branch on THREAD_CREATE_FAILED.
- **Assets (impacted):** ASSET-CREATE-CHANNEL-ACTION, ASSET-CREATE-THREAD-ACTION, ASSET-SPACE-OPERATIONS, ASSET-DISCORD-SPACE-OPERATIONS, ASSET-SPACE-OPERATIONS-REGISTRY, ASSET-LIFECYCLE-CONTEXT-STORE.
- **Anti_patterns (critical for implement):** Hardcoding Discord channel in workflow. Do not rely on discordTrigger alone for Luna activation. Do not log Cursor API key or request body. Do not assume single Cursor API error shape. Do not serialize null fields in Cursor API request payload.
- **Doc excerpts (contracts):** create_channel delegates to SpaceOperations; create_thread runs after create_lifecycle_context; bind channelId, threadName, optional contextId; lifecycle owner gateway; setDeliveryTargetId on success; storeIn deliveryChannelId. Done step may use main-room redirect.

### FEAT-CONNECTORS-DISCORD (discord)

- **Feature:** Discord event source and reply. Status: active. Doc: `mkdoc/features/domain/connectors/discord.md`.
- **Requirements:** REQ-CONNECTORS-DISCORD-001 — Discord event source and reply; OutboundGateway createTextChannel, createThreadChannel, addPermissionOverride; SpaceOperationsRegistry register/get by connector id; Bootstrap registers Discord SpaceOperations.
- **Acceptance (relevant):** Gateway createTextChannel(guildId, channelName) for lifecycle room; SpaceOperations and SpaceOperationsRegistry; DiscordSpaceOperations implements createRoom and createThread.
- **Assets (impacted):** ASSET-SPACE-OPERATIONS (interface: createRoom, createThread — add overloads or replace with CreateRoomRequest/CreateThreadRequest), ASSET-SPACE-OPERATIONS-REGISTRY (get returns null for unknown prefix; no change), ASSET-DISCORD-SPACE-OPERATIONS (implement createRoom(CreateRoomRequest), createThread(CreateThreadRequest); preserve Discord behavior).
- **Validation tests:** UNIT-DISCORD-SPACE-OPERATIONS, UNIT-CREATE-CHANNEL-ACTION-DISCORD, UNIT-CREATE-CHANNEL-ACTION-LIFECYCLE-OWNER. Add explicit tests for capability lookup: unregistered prefix returns sentinel; null/blank prefix returns sentinel (fail-closed).
- **Doc excerpts (decisions):** Generic ReplySender and OutboundGateway; connector-agnostic core; lifecycle uses router and getSelfUserIdForBot. Preserve Discord mentions in connector events.

## Implementation checklist (from request)

1. **Remove "discord" default** from CreateChannelAction and CreateThreadAction: when source prefix is null or blank, do not default to "discord"; return CHANNEL_CREATE_FAILED / THREAD_CREATE_FAILED (fail-closed).
2. **Fail-closed capability lookup:** If registry.get(prefix) returns null, return the appropriate sentinel. Add explicit unit tests for: null/blank prefix → sentinel; unregistered prefix → sentinel; Discord source (e.g. "discord:123") still resolves and behaves as today.
3. **Introduce CreateRoomRequest and CreateThreadRequest:** New request DTOs for SpaceOperations. SpaceOperations interface: createRoom(CreateRoomRequest), createThread(CreateThreadRequest). Actions build request from event, state, bind and call ops.createRoom(req) / ops.createThread(req). DiscordSpaceOperations implements using request fields (guildId, channelName, channelId, threadName, contextId, lifecycleOwnerBotId, etc.); preserve existing Discord behavior.
4. **Optional shared sourcePrefix helper:** Extract source prefix logic (event.getSourceId() → prefix before colon, or full id) into a shared helper used by both actions (e.g. workflow util or connectors util) to avoid duplication; optional so implementer may keep in-action private methods if preferred.
5. **Preserve Discord behavior:** When event.getSourceId() is "discord:guildId" or similar, prefix is "discord"; registry.get("discord") returns DiscordSpaceOperations; createRoom/createThread behavior unchanged for Discord (channel naming, permission override, setDeliveryTargetId, etc.). Config and Bootstrap continue to register "discord" → DiscordSpaceOperations.

## Schema constraints

- **workflow-registry / connectors-registry:** Use existing asset and requirement keys. New asset ids for CreateRoomRequest and CreateThreadRequest may be added (e.g. ASSET-CREATE-ROOM-REQUEST, ASSET-CREATE-THREAD-REQUEST) in connectors-registry if these are first-class; otherwise document as DTOs used by SpaceOperations. Do not delete requirements or invent new spec schema keys (guardrails).
