# Change context (for plan_change / implement)

## Scope

**Request:** Implement multi-bot feature-room lifecycle (Step 1) per `.cursor/plans/multi-bot-feature-room-lifecycle-revised.plan.md`: Slices 1–8 — participant state model and store, ProvisionRoomParticipantsAction, InitializeFeatureRoomStateAction, Router participant routing, OutboundDeliveryRouter sendAs/sendAsRole, four bots and minimal workflows, Luna handoff sequence, tests and docs.

**Key decisions:** Response policy (only Arrietty replies by default); permissions for all four via participantBotIds; exact handoff key `featureRoomParticipants`; Arrietty = Orchestrator.

**Scoped features / requirements / assets:**  
Features: FEAT-STATE, FEAT-CURSOR-GATHERING (workflow), FEAT-ROUTING, FEAT-BOT, FEAT-CONNECTORS-DISCORD, FEAT-CONFIG, FEAT-CORE, FEAT-ENGINE.  
Requirements: REQ-STATE-001, REQ-WORKFLOW-001, REQ-LUNA-001, REQ-BOT-001, REQ-CONNECTORS-DISCORD-001, REQ-CONFIG-001, REQ-CORE-002, REQ-CORE-003.  
Assets: state store, Luna state, Router, OutboundDeliveryRouter, CreateRoomRequest, DiscordSpaceOperations, PostChannelMessageAction, Bootstrap, bots.yaml, Engine; **new:** PlanningRole, RoomParticipant, FeatureRoomState, FeatureRoomStateStore, ProvisionRoomParticipantsAction, InitializeFeatureRoomStateAction.

---

## Per feature / registry

### State (state-registry.yml)

**Feature:** FEAT-STATE — State store for workflow state. Status: active. doc_path: features/domain/state/state.md. Summary: StateStore persists and loads per-bot, per-conversation workflow state.

**Requirements:** REQ-STATE-001 — State store for bot workflow state. Statement: StateStore persists and loads per-bot, per-conversation workflow state (structured state objects). Acceptance: StateStore.load(botId, conversationKey) returns state or empty; StateStore.save persists. Traceability: ASSET-STATE-STORE, ASSET-LUNA-STATE. Validation: UNIT-STATE-STORE (StateStoreTest).

**Assets:** ASSET-STATE-STORE (StateStore.java), ASSET-LUNA-STATE (GatheringState.java). **New assets to add in state-registry:** PlanningRole (enum ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE), RoomParticipant (immutable: role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator), FeatureRoomState (immutable: contextId, featureId, featureSlug, roomChannelId, intakeThreadId, repo, initialRequest, status, participants, createdBy*, createdAt), FeatureRoomStateStore (in-memory; indexes by contextId, roomChannelId, intakeThreadId, featureId). Package: state.planning.

**Doc excerpts:** state.md — minimal feature index; no decisions/contracts subpage for state feature. Rely on plan contract: featureRoomParticipants key, List<Map<String,Object>>, five keys per entry (role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator).

---

### Workflow (workflow-registry.yml)

**Feature:** FEAT-CURSOR-GATHERING — Cursor-backed gathering workflow. Status: active. doc_path: features/domain/workflow/cursor-gathering.md. Summary: Luna gathers repo/feature input, launch_cursor_run, lifecycle room Phase 1 (extended context, provisioning actions, create_channel sentinel).

**Requirements:** REQ-WORKFLOW-001 — Workflow state machine and config-driven runners. Statement: Workflow/WorkflowRunner/ConfigurableWorkflowRunner; create_thread, create_channel use request types and SpaceOperations; fail-closed when prefix null/blank. REQ-LUNA-001 — Luna bot Discord mention, luna_cursor, lifecycle room Phase 1; create_channel with lifecycleOwnerBotId/addPermissionOverride; Arrietty template, provision_bot_instance, create_lifecycle_context, create_thread, post_channel_message, launch_cursor_run. Acceptance (relevant): create_channel bind may include lifecycleOwnerBotId; addPermissionOverride for that bot; create_thread storeIn deliveryChannelId; setDeliveryTargetId on success; post_channel_message send target deliveryChannelId or channelId; lifecycleBotName from bind.

**Assets (impacted):** ASSET-POST-CHANNEL-MESSAGE-ACTION (read asRole/asBotId from bind; call OutboundDeliveryRouter sendAs/sendAsRole when set). **New:** ASSET-PROVISION-ROOM-PARTICIPANTS-ACTION (ProvisionRoomParticipantsAction.java — provisions architect, auditor, scribe; reuses state.instanceId for arrietty; returns List<Map<String,Object>> for featureRoomParticipants; stores RuntimeBotInstance in StateStore under bot_instance:{id}). ASSET-INITIALIZE-FEATURE-ROOM-STATE-ACTION (InitializeFeatureRoomStateAction.java — reads featureRoomParticipants from state, validates shape/size 4 and five keys, builds FeatureRoomState, puts in FeatureRoomStateStore).

**Anti_patterns (workflow/REQ-LUNA-001):** Hardcoding Discord channel; storing secrets in state; do not rely on discordTrigger alone for Luna; do not log Cursor API key/body; do not assume single Cursor error shape; do not serialize null in Cursor request.

**Doc excerpts (cursor-gathering):** Contracts — Lifecycle room: LifecycleContext, LifecycleContextStore, RuntimeBotInstance; create_channel builds CreateRoomRequest, optional lifecycleOwnerBotId, addPermissionOverride; create_thread uses lifecycle owner gateway, setDeliveryTargetId. Known-issues: flow depends on external Cursor/repo; documented path is Luna-specialized; run tracking in-memory for v1.

---

### Bot (bot-registry.yml)

**Feature:** FEAT-ROUTING — Event routing and normalized context. Status: active. doc_path: features/domain/bot/routing.md.

**Requirements:** REQ-BOT-001 — Route events to bots by routing rules and ownership. Statement: Router matches by rules and ownership; when channel has lifecycle context and owner has handlesOwnedSpaces, only that owner returned; else filter-based. **Extension for multi-bot:** When FeatureRoomState exists for room (getByChannelId) or delivery target (getByDeliveryTargetId), return four participant configuredBotIds in stable order; else legacy single-owner (LifecycleContext) behavior.

**Assets:** ASSET-ROUTER — inject FeatureRoomStateStore; resolve lifecycle by getByChannelId then getByDeliveryTargetId; when FeatureRoomState present return four participant bot ids; else legacy single-owner.

**Anti_patterns:** Do not hardcode bot ids in Router or engine; use config-driven handlesOwnedSpaces and lifecycle/feature-room state.

**Doc excerpts (routing):** Decisions — Ownership-based routing (handlesOwnedSpaces); Router depends on LifecycleContextStore and handlesMap. Contracts — Router returns matching bot ids; single-owner precedence when lifecycle owner has handlesOwnedSpaces. **Implement:** Add FeatureRoomStateStore; multi-bot room returns all four participants (arrietty, architect, auditor, scribe) for room and thread.

---

### Connectors (connectors-registry.yml)

**Feature:** FEAT-CONNECTORS-DISCORD — Discord event source and reply. Status: active. doc_path: features/domain/connectors/discord.md.

**Requirements:** REQ-CONNECTORS-DISCORD-001 — Discord event source and reply; OutboundDeliveryRouter resolves sender by target and lifecycle configuredBotId; getSelfUserIdForBot(botId). **Extension:** CreateRoomRequest supports optional participantBotIds (List<String>); DiscordSpaceOperations.createRoom applies addPermissionOverride for each bot in list (resolve userId via router.getSelfUserIdForBot(botId)); fallback to lifecycleOwnerBotId when list empty. OutboundDeliveryRouter: add sendAs(asBotId), sendAsRole(role); resolve role via FeatureRoomState.participants; fallback when no feature state/role.

**Assets:** ASSET-CREATE-ROOM-REQUEST — add optional participantBotIds; from() parses list from bind/state. ASSET-DISCORD-SPACE-OPERATIONS — in createRoom(), loop participantBotIds and addPermissionOverride for each; else lifecycleOwnerBotId. ASSET-OUTBOUND-DELIVERY-ROUTER — add FeatureRoomStateStore; sendAs(botId), sendAsRole(PlanningRole); resolve participant bot’s sender for delivery.

**Doc excerpts (discord):** Contracts — CreateRoomRequest: sourceId, guildId, channelName, lifecycleOwnerBotId, project, codeChange; from(Event, state, bind). OutboundDeliveryRouter: sender resolution by target and lifecycle configuredBotId; getSelfUserIdForBot(botId). Decisions — ReplySender/OutboundGateway; no connector literals in core.

---

### Config (config-registry.yml)

**Feature:** FEAT-CONFIG — Bot config from YAML. Status: active. doc_path: features/domain/config/config.md.

**Requirements:** REQ-CONFIG-001 — ConfigLoader loads bots, routing, workflows from YAML; workflow step bind keys and interpolation supported.

**Assets:** ASSET-BOTS-YAML — Add bots: architect, auditor, scribe (minimal workflows architect_intro, auditor_intro, scribe_intro — one-step done, no reply). Luna luna_cursor: exact sequence create_channel (participantBotIds: [arrietty, architect, auditor, scribe]), branch, provision_bot_instance (arrietty), create_lifecycle_context, create_thread (threadName: intake-spec), provision_room_participants (storeIn: featureRoomParticipants), initialize_feature_room_state, post_channel_message (asRole: orchestrator), post_channel_message (thread, asRole: scribe), launch_cursor_run, done. Arrietty remains Orchestrator (workflow may reply); architect/auditor/scribe minimal workflows do not reply.

---

### Core (core-registry.yml)

**Feature:** FEAT-CORE, FEAT-ENGINE — Bootstrap and engine. Status: active.

**Requirements:** REQ-CORE-002 — Bootstrap wires engine, config, connectors; registers reply sender/sink by connector id; action registration in Bootstrap. REQ-CORE-003 — Engine routes events, runs workflow/reasoner, delivers via sink/sender; ReplyTargetResolver by connector id; fail closed when no resolver or empty.

**Assets:** ASSET-BOOTSTRAP — Create FeatureRoomStateStore; pass to Router and OutboundDeliveryRouter; register actions provision_room_participants, initialize_feature_room_state. No Engine change for response policy (engine already only delivers when buildOutboundResponse returns non-null; minimal workflows return no reply).

---

## Schema constraints

- Use existing req-registry keys only: requirements (id, title, statement, status, acceptance, traceability, validation, anti_patterns), assets (id, path, role, requires, feature_ids). Do not invent new top-level keys. New assets/requirements may be added; do not delete existing requirements.
- Feature slugs must not equal bot ids (guardrails).

---

## Plan contract summary (featureRoomParticipants)

- **Key:** `featureRoomParticipants` (workflow state).
- **Type:** List<Map<String, Object>>.
- **Per entry (required keys):** role (ORCHESTRATOR|ARCHITECT|AUDITOR|SCRIBE), configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator (boolean).
- **Produced by:** ProvisionRoomParticipantsAction (storeIn: featureRoomParticipants); runner puts return value in state.
- **Consumed by:** InitializeFeatureRoomStateAction; validates non-null List size 4, each element Map with five keys; role enum; non-blank configuredBotId/runtimeBotInstanceId.
- **RuntimeBotInstance:** Also stored in StateStore under bot_instance:{runtimeBotInstanceId} by ProvisionRoomParticipantsAction.

---

## Response policy (implement note)

All four bots receive the event (Router returns four ids). Only Arrietty (Orchestrator) workflow may produce a public reply by default. Architect, Auditor, Scribe: minimal workflows (e.g. one-step done, no message) so buildOutboundResponse stays null. Document in Router/Engine or feature-room doc: “Feature room response policy — only Orchestrator replies by default; others reply when invoked via action (e.g. post_channel_message asRole).”

---

## Arrietty = Orchestrator

Bot id remains **arrietty**; PlanningRole for that participant is ORCHESTRATOR. Config: participantBotIds and feature room participants include arrietty with role ORCHESTRATOR; create_lifecycle_context configuredBotId remains arrietty. Docs/specs: “Arrietty (Orchestrator)” as primary coordinator; three new bots architect, auditor, scribe. Backward compatibility: rooms without FeatureRoomState keep single-owner (LifecycleContext) behavior.
