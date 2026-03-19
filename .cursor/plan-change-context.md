# Change context (for plan_change / implement)

## Scope

**Request:** Implement Workflow Action Connector Capability Extraction — add SpaceOperations interface and SpaceOperationsRegistry; add DiscordSpaceOperations (move CreateChannelAction/CreateThreadAction logic into it); refactor CreateChannelAction and CreateThreadAction to delegate to capability by source prefix; Bootstrap registers Discord capability; actions no longer parse Discord sourceId.

**Impacted registry slice:** workflow-registry.yml, connectors-registry.yml, core-registry.yml.

**Features:** FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING, FEAT-CONNECTORS-DISCORD, FEAT-CORE.

**Requirements:** REQ-WORKFLOW-001, REQ-LUNA-001, REQ-CONNECTORS-DISCORD-001, REQ-CORE-002.

**Assets (existing, modified or referenced):** ASSET-CREATE-CHANNEL-ACTION, ASSET-CREATE-THREAD-ACTION, ASSET-OUTBOUND-GATEWAY, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-BOOTSTRAP, ASSET-LIFECYCLE-CONTEXT-STORE.

**New assets (to add in implement):** SpaceOperations (interface), SpaceOperationsRegistry, DiscordSpaceOperations.

**Validation tests:** CreateChannelActionTest, CreateThreadActionTest; UNIT-CREATE-CHANNEL-ACTION, UNIT-CREATE-CHANNEL-ACTION-SENTINEL, UNIT-CREATE-CHANNEL-ACTION-NORMALIZE, UNIT-CREATE-CHANNEL-ACTION-LIFECYCLE-OWNER, UNIT-CREATE-CHANNEL-ACTION-DISCORD, UNIT-CREATE-THREAD-ACTION.

---

## Per feature

### FEAT-WORKFLOW-STEPS (workflow-steps)

- **Feature:** title "Workflow step DSL and branching actions"; status active; doc_path features/domain/workflow/workflow-steps.md; summary: WorkflowDefinition and step/action primitives define the configurable workflow DSL.
- **Requirements:** REQ-WORKFLOW-001 — Workflow state machine and config-driven runners; create_thread action creates Discord thread via OutboundGateway; create_channel returns CHANNEL_CREATE_FAILED on failure; bind channelId, threadName, contextId; lifecycle owner gateway when channel has context; setDeliveryTargetId on success when contextId present.
- **Assets:** ASSET-WORKFLOW-ACTION (WorkflowAction interface), ASSET-WORKFLOW-ACTION-REGISTRY (register/resolve by name), ASSET-CREATE-CHANNEL-ACTION (CreateChannelAction.java), ASSET-CREATE-THREAD-ACTION (CreateThreadAction.java).
- **Anti-patterns (guardrails):** Do not delete requirements; do not invent new spec keys; avoid hardcoding Discord in workflow; read anti_patterns on requirements/assets.
- **Doc excerpts (workflow-steps/contracts):** WorkflowAction named action contract. create_channel returns channel id or CHANNEL_CREATE_FAILED; channel name derived from state when blank, normalized to Discord-safe; create_thread bind channelId, threadName, contextId; lifecycle owner gateway; setDeliveryTargetId on success.

### FEAT-CURSOR-GATHERING (cursor-gathering)

- **Feature:** title "Cursor-backed gathering workflow"; status active; doc_path features/domain/workflow/cursor-gathering.md; summary: Luna gather + Cursor Cloud; lifecycle room Phase 1 with create_channel/create_thread, provisioning, LifecycleContextStore.
- **Requirements:** REQ-LUNA-001 — create_channel (Discord createTextChannel, CHANNEL_CREATE_FAILED, room naming from state, normalizeChannelName); optional lifecycleOwnerBotId → addPermissionOverride for that bot; create_thread under parent channel, lifecycle owner gateway when channel has context, setDeliveryTargetId when contextId present; storeIn deliveryChannelId.
- **Assets:** ASSET-CREATE-CHANNEL-ACTION, ASSET-CREATE-THREAD-ACTION, ASSET-LIFECYCLE-CONTEXT-STORE, ASSET-BOOTSTRAP, ASSET-OUTBOUND-GATEWAY (ref).
- **Anti-patterns:** Hardcoding Discord channel in workflow; storing secrets in state; do not log Cursor API key/body.
- **Doc excerpts (cursor-gathering/contracts):** create_channel: gateway createTextChannel; optional lifecycleOwnerBotId → addPermissionOverride; returns channel id or CHANNEL_CREATE_FAILED. create_thread: bind channelId, threadName, contextId; lifecycle owner gateway; setDeliveryTargetId(contextId, threadId) on success; storeIn deliveryChannelId.

### FEAT-CONNECTORS-DISCORD (discord)

- **Feature:** title "Discord event source and reply"; status active; doc_path features/domain/connectors/discord.md; summary: JDA gateway, ReplySender, OutboundGateway; OutboundDeliveryRouter getGatewayForChannel, getSelfUserIdForBot.
- **Requirements:** REQ-CONNECTORS-DISCORD-001 — OutboundGateway createTextChannel, createThreadChannel, addPermissionOverride; getGatewayForChannel(channelId); getSelfUserIdForBot(botId); Bootstrap keeps action registration.
- **Assets:** ASSET-OUTBOUND-GATEWAY (createTextChannel, createThreadChannel, addPermissionOverride), ASSET-OUTBOUND-DELIVERY-ROUTER (registerSender, getGatewayForChannel, getSelfUserIdForBot).
- **Doc excerpts (discord/decisions):** ReplySender and OutboundGateway introduced for connector-agnostic outbound delivery; Discord implements both; lifecycle actions use router for sender resolution and getSelfUserIdForBot for overwrites.

### FEAT-CORE (core)

- **Feature:** title "Specs governance and bootstrap"; status active; doc_path features/domain/core/core.md; summary: VinekeepersApp + Bootstrap wire runtime.
- **Requirements:** REQ-CORE-002 — Bootstrap wires engine, config, ConnectorRegistry; registerBots; engine reply sender/sink/ReplyTargetResolver by connector id; action and sink registration stay in Bootstrap.
- **Assets:** ASSET-BOOTSTRAP (path src/main/java/com/vinekeepers/core/Bootstrap.java); wires create_channel and create_thread actions in withDiscord() when default gateway present; registerLifecycleActions for provision_bot_instance, create_lifecycle_context, launch_cursor_run.
- **Validation tests:** BootstrapTest (Bootstrap wires engine, config, ConnectorRegistry, Discord adapter and registerBots).

---

## Implementation constraints (from request)

1. **SpaceOperations interface:** Contract for connector-specific "space" operations (e.g. create channel, create thread). Actions will obtain implementation by source prefix (e.g. "discord") from a registry.
2. **SpaceOperationsRegistry:** Register and resolve SpaceOperations by source prefix (connector id). Bootstrap registers Discord capability under "discord".
3. **DiscordSpaceOperations:** New class implementing SpaceOperations; contains the logic currently in CreateChannelAction (guildId, channelName, normalizeChannelName, buildChannelNameFromState, createTextChannel, addPermissionOverride for lifecycleOwnerBotId) and CreateThreadAction (channelId, threadName, gateway resolution, createThreadChannel, setDeliveryTargetId). Takes OutboundDeliveryRouter, LifecycleContextStore, OutboundGateway as needed.
4. **CreateChannelAction / CreateThreadAction refactor:** Accept SpaceOperationsRegistry (and optionally Event for sourceId). In run(): derive source prefix from event.getSourceId() (e.g. "discord" from "discord:..."); look up SpaceOperations by prefix; delegate to capability. No parsing of Discord sourceId inside the action (e.g. no guildId from sourceId substring); capability receives event/state/bind and performs connector-specific behavior.
5. **Bootstrap:** Build SpaceOperationsRegistry; register DiscordSpaceOperations for "discord" (when Discord adapter is present); pass registry into CreateChannelAction and CreateThreadAction. Keep action registration in Bootstrap (withDiscord block).
6. **Tests:** CreateChannelActionTest and CreateThreadActionTest must continue to pass. They may register a stub or real SpaceOperations for "discord" (or the test source prefix used in events). Update tests so actions receive SpaceOperationsRegistry; unit-test DiscordSpaceOperations for the moved logic where appropriate.

---

## Schema constraints

- Use existing req-registry schema; no new requirement or asset keys. New assets (SpaceOperations, SpaceOperationsRegistry, DiscordSpaceOperations) must be added to workflow-registry.yml and/or connectors-registry.yml with existing keys (id, kind, path, role, requires, feature_ids).
- Traceability: update traceability.assets for REQ-WORKFLOW-001 and REQ-LUNA-001 to include new assets; update asset requires/feature_ids as needed.

---

## Removals and renames

- **removal_or_rename:** false. No file or asset deletion; no renames. CreateChannelAction and CreateThreadAction are refactored in place; new files added for interface, registry, and DiscordSpaceOperations.
