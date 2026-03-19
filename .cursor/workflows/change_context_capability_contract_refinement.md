# Change context (for plan_change / implement)

## Scope

**Request-derived:** Capability Contract Refinement — CreateRoomRequest and CreateThreadRequest with explicit intent fields; bind/state resolution in request factory; DiscordSpaceOperations reads only request getters; no raw map rummaging in connector; preserve Discord and lifecycle behavior.

**Impacted registry slice:** connectors-registry.yml (FEAT-CONNECTORS-DISCORD, REQ-CONNECTORS-DISCORD-001); workflow-registry.yml (FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING, REQ-WORKFLOW-001, REQ-LUNA-001).

**Features:** FEAT-CONNECTORS-DISCORD, FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING.

**Requirements:** REQ-CONNECTORS-DISCORD-001, REQ-WORKFLOW-001, REQ-LUNA-001.

**Assets:** ASSET-CREATE-ROOM-REQUEST, ASSET-CREATE-THREAD-REQUEST, ASSET-DISCORD-SPACE-OPERATIONS, ASSET-SPACE-OPERATIONS, ASSET-CREATE-CHANNEL-ACTION, ASSET-CREATE-THREAD-ACTION.

---

## Per feature

### Feature: FEAT-CONNECTORS-DISCORD (discord)

- **Title:** Discord event source and reply  
- **Status:** active  
- **doc_path:** features/domain/connectors/discord.md  
- **Summary:** Discord event source and reply; a JDA-backed gateway receives live messages, preserves mention metadata for routing, and delivers workflow replies back to Discord.

**Requirements (in scope):**

- **REQ-CONNECTORS-DISCORD-001**
  - **Title:** Discord event source and reply  
  - **Statement (one line):** Discord event source and reply; generic outbound abstraction ReplySender and OutboundGateway; per-bot identity; ConnectorContext generic; adapter registerBots only; Bootstrap registers engine reply sender, sink, ReplyTargetResolver, SpaceOperations; OutboundDeliveryRouter sender resolution; DiscordAppReplySink lifecycle operations; SpaceOperations.createRoom(CreateRoomRequest) and createThread(CreateThreadRequest) take request DTOs; no default connector when source prefix null/blank — callers fail closed.
  - **Acceptance criteria (short):**
    - SpaceOperations.createRoom(CreateRoomRequest) and createThread(CreateThreadRequest) take request DTOs; no default connector when source prefix is null or blank — callers fail closed.
    - Gateway createTextChannel(guildId, channelName) creates a text channel in the guild for lifecycle room; returns channel id or null on failure.
  - **Traceability assets:** ASSET-CREATE-ROOM-REQUEST, ASSET-CREATE-THREAD-REQUEST, ASSET-DISCORD-SPACE-OPERATIONS, ASSET-SPACE-OPERATIONS, ASSET-SPACE-OPERATIONS-REGISTRY, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-DISCORD-GATEWAY, others per connectors-registry.
  - **Validation tests:** UNIT-DISCORD-SPACE-OPERATIONS (DiscordSpaceOperationsTest — createRoom guildId/name/permission override, createThread channelId/threadName/contextId/setDeliveryTargetId, normalizeChannelName); UNIT-CREATE-CHANNEL-ACTION-*; UNIT-CREATE-THREAD-ACTION (CreateThreadActionTest).
  - **Anti_patterns:** (none listed for this requirement in registry; guardrails: avoid anti_patterns on requirements/assets; do not invent new spec keys.)

**Assets (in scope):**

- **ASSET-CREATE-ROOM-REQUEST** — path: src/main/java/com/vinekeepers/connectors/CreateRoomRequest.java. Role: Request DTO for SpaceOperations.createRoom; built from event (sourceId), state, and bind. **Refinement:** Add explicit intent fields (e.g. sourceId, guildId, channelName, lifecycleOwnerBotId) with getters; request factory (from() or dedicated) performs bind/state resolution and sets these; connector does not read state/bind maps.
- **ASSET-CREATE-THREAD-REQUEST** — path: src/main/java/com/vinekeepers/connectors/CreateThreadRequest.java. Role: Request DTO for SpaceOperations.createThread; built from event (sourceId), state, and bind. **Refinement:** Add explicit intent fields (e.g. sourceId, channelId, threadName, contextId) with getters; request factory owns resolution; connector uses only getters.
- **ASSET-DISCORD-SPACE-OPERATIONS** — path: src/main/java/com/vinekeepers/connectors/DiscordSpaceOperations.java. Role: Implements SpaceOperations for Discord; createRoom (guildId from request, channelName, normalizeChannelName, createTextChannel, lifecycleOwnerBotId addPermissionOverride); createThread (channelId/threadName/contextId from request, gateway, createThreadChannel, setDeliveryTargetId). **Refinement:** Read only request getters (e.g. getGuildId(), getChannelName(), getLifecycleOwnerBotId(); getChannelId(), getThreadName(), getContextId()); no raw map access (no request.state(), request.bind(), or getString(map, key)).

**Doc excerpts (discord):**

- **Decisions:** Generic ReplySender and OutboundGateway; request-based SpaceOperations with CreateRoomRequest/CreateThreadRequest built from event, state, bind; fail-closed when no ops for prefix.
- **Contracts:** SpaceOperations: createRoom(CreateRoomRequest), createThread(CreateThreadRequest). Request DTOs built from event (sourceId), state, and bind. DiscordSpaceOperations implements for Discord (createTextChannel, createThreadChannel, setDeliveryTargetId, addPermissionOverride).
- **Known issues:** (None.)

---

### Feature: FEAT-WORKFLOW-STEPS / FEAT-CURSOR-GATHERING (workflow-steps, cursor-gathering)

- **Title:** Workflow step DSL and branching actions; Cursor-backed gathering workflow  
- **doc_path:** features/domain/workflow/workflow-steps.md, features/domain/workflow/cursor-gathering.md  

**Requirements (in scope):**

- **REQ-WORKFLOW-001:** create_channel and create_thread use request types (CreateRoomRequest, CreateThreadRequest) and resolve SpaceOperations by WorkflowCapabilitySupport.sourcePrefix(event); null or blank prefix returns sentinel (fail-closed).
- **REQ-LUNA-001:** create_channel returns CHANNEL_CREATE_FAILED on gateway failure or null; room naming uses normalizeChannelName; create_channel bind may include lifecycleOwnerBotId; create_thread with contextId updates LifecycleContextStore setDeliveryTargetId on success.

**Assets (in scope):**

- **ASSET-CREATE-CHANNEL-ACTION** — path: src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java. Role: Builds CreateRoomRequest from event/state/bind, resolves SpaceOperations by source prefix; fail-closed. **Refinement:** Request factory (CreateRoomRequest.from or equivalent) owns bind/state resolution; action continues to call from(event, state, bind) and pass request to SpaceOperations; request must expose explicit intent fields so connector does not touch maps.
- **ASSET-CREATE-THREAD-ACTION** — path: src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java. Role: Builds CreateThreadRequest from event/state/bind, resolves SpaceOperations; fail-closed. **Refinement:** Same pattern; factory in request layer owns resolution; action passes request; connector uses only getters.

**Doc excerpts (workflow-steps contracts):**

- create_channel and create_thread build CreateRoomRequest and CreateThreadRequest from event, state, and bind; resolve SpaceOperations via WorkflowCapabilitySupport.sourcePrefix(event); bind has precedence over state for action inputs.

---

## Implementation constraints (for implement step)

1. **CreateRoomRequest:** Replace (sourceId, state, bind) with explicit fields. Suggested getters: getSourceId(), getGuildId(), getChannelName(), getLifecycleOwnerBotId(). Resolution rules (preserve current behavior): guildId = bind then state then derived from sourceId (e.g. discord:GUILD_ID); channelName = bind then state then buildChannelNameFromState(state); lifecycleOwnerBotId = bind then state. Move buildChannelNameFromState (and helpers repoSegmentFromProject, slugFromCodeChange) into the request factory/from() so DiscordSpaceOperations does not need state map for naming.
2. **CreateThreadRequest:** Replace (sourceId, state, bind) with explicit fields. Suggested getters: getSourceId(), getChannelId(), getThreadName(), getContextId(). Resolution: channelId = bind then state; threadName = bind then state, default "Room updates"; contextId = bind then state.
3. **Request factory:** The static from(Event, state, bind) on each request type (or a single factory class) must perform all bind/state/sourceId resolution and populate the request object. Actions continue to call this factory and pass the request to SpaceOperations.
4. **DiscordSpaceOperations:** Remove all getString(request.state(), ...), getString(request.bind(), ...) and any use of request.state() or request.bind(). Use only request.getGuildId(), request.getChannelName(), request.getLifecycleOwnerBotId() for createRoom; request.getChannelId(), request.getThreadName(), request.getContextId() for createThread. Keep normalizeChannelName in DiscordSpaceOperations (or move to request if channelName is always pre-normalized in factory; spec says "normalizeChannelName" in DiscordSpaceOperations — keep it there and pass the resolved channelName from request; if channelName from factory is not yet normalized, DiscordSpaceOperations may call normalizeChannelName(request.getChannelName()) before createTextChannel).
5. **Preserve behavior:** Lifecycle owner permission override, setDeliveryTargetId on thread success, sentinels CHANNEL_CREATE_FAILED/THREAD_CREATE_FAILED, fail-closed on null/blank prefix or missing ops. All existing tests (DiscordSpaceOperationsTest, CreateChannelActionTest, CreateThreadActionTest) must remain passing; update tests to supply requests with explicit fields where the connector is under test.
6. **Schema / guardrails:** Do not delete requirements; do not invent new spec keys; repair spec drift if any; consider anti_patterns on requirements (connectors-registry has no anti_patterns for REQ-CONNECTORS-DISCORD-001). Update connectors-registry asset role text for ASSET-CREATE-ROOM-REQUEST, ASSET-CREATE-THREAD-REQUEST, ASSET-DISCORD-SPACE-OPERATIONS to state explicit intent fields and connector reads only getters.

---

## Schema constraints

- Requirement keys: id, title, statement, status, priority, type, behavior, acceptance, traceability, validation, anti_patterns (per req-registry schema).  
- Asset keys: id, kind, path, role, requires, feature_ids.  
- Do not add new top-level keys to registry YAML.
