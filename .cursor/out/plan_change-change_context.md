# Change context (for plan_change / implement)

## Scope

**Request:** Implement Arrietty thread-based updates — routing fix (LifecycleContextStore + OutboundDeliveryRouter), deliveryChannelId, create_thread action, PostChannelMessageAction and CursorCloudRunMonitor updates, workflow config.

**Impacted registry slice:** connectors, workflow, core, config.

**Features:** FEAT-CONNECTORS-DISCORD, FEAT-CURSOR-GATHERING, FEAT-WORKFLOW-STEPS.

**Requirements:** REQ-CONNECTORS-DISCORD-001, REQ-LUNA-001, REQ-WORKFLOW-001.

**Assets:** LifecycleContext, LifecycleContextStore, LifecycleRunRecord, OutboundDeliveryRouter, DiscordGateway, JdaDiscordGateway, CreateLifecycleContextAction, PostChannelMessageAction, LaunchCursorRunAction, CreateThreadAction (new), Bootstrap, CursorCloudRunMonitor, config/bots.yaml.

---

## Per feature

### FEAT-CONNECTORS-DISCORD (discord)

**Feature:** Discord event source and reply. Status: active. doc_path: features/domain/connectors/discord.md. Summary: Discord event source and reply; JDA-backed gateway receives live messages, preserves mention metadata for routing, delivers workflow replies; OutboundDeliveryRouter resolves sender from target and lifecycle context.

**Requirements**

- **REQ-CONNECTORS-DISCORD-001** — Discord event source and reply. Statement: DiscordEventSource implements EventSource; gateway receives/sends; OutboundDeliveryRouter resolves reply sender from delivery target and optional lifecycle context (configuredBotId); getGatewayForChannel(channelId) returns null when lifecycle bot has no gateway; getDiscordUserIdForBot(botId); createTextChannel, getSelfUserId, addPermissionOverride for lifecycle room; DiscordReplySender send(channelId, messageId, content). Acceptance: OutboundDeliveryRouter resolves sender from channel: lifecycle context (by channelId) yields configuredBotId then that bot's sender; absent context uses default sender; getGatewayForChannel returns null when lifecycle context exists but configured bot has no gateway; gateway contract exposes getSelfUserId and addPermissionOverride; createTextChannel for lifecycle room. Tests: UNIT-OUTBOUND-DELIVERY-ROUTER (OutboundDeliveryRouterTest — lifecycle context to configuredBotId sender resolution, default sender when no context, no fallback when lifecycle channel has no sender).

**Assets**

- ASSET-OUTBOUND-DELIVERY-ROUTER — path: src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java. Role: Routes outbound delivery; implements DiscordReplySender; resolves sender from delivery target and lifecycle context (configuredBotId); getGatewayForChannel; default sender for non-lifecycle; for lifecycle uses configured bot's sender only.
- ASSET-DISCORD-GATEWAY-CONTRACT — path: src/main/java/com/vinekeepers/connectors/DiscordGateway.java. Role: Gateway contract for receive/send, createTextChannel, getSelfUserId, addPermissionOverride.
- ASSET-DISCORD-GATEWAY — path: src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java. Role: JDA-backed gateway; send uses channel id (thread is channel in JDA); no createThreadChannel today.

**Doc excerpts (discord)**

- **Decisions:** Preserve Discord mentions in connector events; routing can match from payload metadata; connector tests treat mentions as contract.
- **Contracts:** DiscordReplySender send(channelId, messageId, content). OutboundDeliveryRouter resolves sender from delivery target and optional lifecycle context (by channelId → configuredBotId); getGatewayForChannel(channelId) returns gateway or null; getDiscordUserIdForBot(botId). DiscordGateway: createTextChannel(guildId, channelName), getSelfUserId(), addPermissionOverride(channelId, guildId, targetUserId, allow, deny). Thread creation not in contract yet.
- **Known issues:** (None.)

---

### FEAT-CURSOR-GATHERING (cursor-gathering)

**Feature:** Cursor-backed gathering workflow. Status: active. doc_path: features/domain/workflow/cursor-gathering.md. Summary: Luna gather + Cursor Cloud; Phase 1 lifecycle room: create_channel → branch → provision_bot_instance → create_lifecycle_context → post_channel_message → launch_cursor_run; LifecycleContext extended; LifecycleRunRecord; arrietty_room workflow message-first.

**Requirements**

- **REQ-LUNA-001** — Luna bot — Discord mention trigger, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1. Statement: Luna workflowRef luna_cursor; lifecycle room: LifecycleRunRecord (channelId, externalRunId); LifecycleContext (channelId, configuredBotId, runtimeBotInstanceId, optional repo/requestText); LifecycleContextStore; actions create_channel (CHANNEL_CREATE_FAILED), post_channel_message, provision_bot_instance, create_lifecycle_context, launch_cursor_run. Acceptance: create_channel returns CHANNEL_CREATE_FAILED on failure; post_channel_message interpolates from merged map; create_lifecycle_context bind precedence; launch_cursor_run ack to Discord; CursorCloudRunMonitor relays updates to Discord; Arrietty template with arrietty_room, handlesOwnedSpaces. Anti_patterns: Hardcoding Discord channel in workflow; storing secrets in state; do not rely on discordTrigger alone for Luna; do not log Cursor API key or request/response body; do not assume single Cursor error shape; do not serialize null in Cursor request payload. Tests: UNIT-LIFECYCLE-CONTEXT-STORE (LifecycleContextStoreTest), UNIT-LIFECYCLE-RUN-RECORD (LifecycleRunRecordTest), UNIT-CURSOR-RUN-MONITOR (CursorCloudRunMonitorTest), UNIT-POST-CHANNEL-MESSAGE-ACTION, UNIT-CREATE-LIFECYCLE-CONTEXT-ACTION, UNIT-LAUNCH-CURSOR-RUN-ACTION.

**Assets**

- ASSET-LIFECYCLE-CONTEXT — path: src/main/java/com/vinekeepers/state/LifecycleContext.java. Role: Extended lifecycle run context (channelId, configuredBotId, runtimeBotInstanceId, optional repo/requestText). No deliveryChannelId today.
- ASSET-LIFECYCLE-CONTEXT-STORE — path: src/main/java/com/vinekeepers/state/LifecycleContextStore.java. Role: Store and resolve lifecycle contexts by key. Indexed by channelId and externalRunId only; no deliveryTargetToContextId or getByDeliveryTargetId.
- ASSET-LUNA-RUN-STATE — path: src/main/java/com/vinekeepers/core/cursor/LifecycleRunRecord.java. Role: Run record for lifecycle Cursor run; channelId, externalRunId, reply target. No deliveryChannelId today.
- ASSET-CURSOR-RUN-MONITOR — path: src/main/java/com/vinekeepers/core/cursor/CursorCloudRunMonitor.java. Role: Poll Cursor and relay status/feedback to Discord. sendUpdate uses runState.getChannelId(), runState.getReplyToMessageId().
- ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION — path: src/main/java/com/vinekeepers/workflow/actions/CreateLifecycleContextAction.java. Role: Create and store lifecycle context; bind precedence. Does not read or set deliveryChannelId.
- ASSET-POST-CHANNEL-MESSAGE-ACTION — path: src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java. Role: Post message to Discord channel; interpolation from merged map. Sends to channelId only.
- ASSET-LAUNCH-CURSOR-RUN-ACTION — path: src/main/java/com/vinekeepers/workflow/actions/LaunchCursorRunAction.java. Role: Launch Cursor run and register run record. Builds LifecycleRunRecord without deliveryChannelId.
- ASSET-BOOTSTRAP — path: src/main/java/com/vinekeepers/core/Bootstrap.java. Role: Wire engine, connectors, register lifecycle actions. No create_thread registration today.
- ASSET-BOTS-YAML — path: config/bots.yaml. Role: Bot definitions; luna_cursor workflow sequence. No create_thread step; no deliveryChannelId in flow.

**Doc excerpts (cursor-gathering)**

- **Decisions:** Luna config-driven in YAML; repository ops via cursor.fullRun and adapter.
- **Contracts:** LifecycleContext — channelId, externalRunId, configuredBotId, runtimeBotInstanceId, optional repo/requestText; indexed by channelId and externalRunId. LifecycleContextStore — store and resolve by key. Workflow actions: create_channel (CHANNEL_CREATE_FAILED), post_channel_message, create_lifecycle_context, launch_cursor_run. CursorCloudRunMonitor turns Cursor state into Discord replies.
- **Known issues:** Flow depends on external Cursor/repo integrations; run tracking in-memory for v1 (no recovery after restart).

---

### FEAT-WORKFLOW-STEPS (workflow-steps)

**Feature:** Workflow step DSL and branching actions. Status: active. doc_path: features/domain/workflow/workflow-steps.md. Summary: WorkflowDefinition and step/action primitives; call_action invokes registered WorkflowAction or tool.

**Requirements**

- **REQ-WORKFLOW-001** — Workflow state machine and config-driven runners. Statement: WorkflowRunner runs workflow; CallActionStep invokes registered action or tool; bind and state passed to actions; interpolation uses merged map (state then bind, bind overrides). Acceptance: call_action invokes registered action; step bind and state passed to actions. Tests: UNIT-CALL-ACTION-STEP, UNIT-POST-CHANNEL-MESSAGE-INTERPOLATION, UNIT-CREATE-LIFECYCLE-CONTEXT-BIND-PRECEDENCE.

**Assets**

- **CreateThreadAction (new)** — path: src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java (to add). Role: Workflow action to create a Discord thread under parent channel; bind channelId, threadName; use default gateway; return thread id or THREAD_CREATE_FAILED; storeIn e.g. deliveryChannelId.
- ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION — see FEAT-CURSOR-GATHERING.

**Doc excerpts (workflow-steps)**

- **Decisions:** CallActionStep resolves registered workflow action or ToolRunner-backed tool; intent-based steps and connector sink.
- **Contracts:** WorkflowActionRegistry resolves actions by name. Registered actions: create_channel, post_channel_message, provision_bot_instance, create_lifecycle_context, launch_cursor_run. create_thread to be added.

---

## Implementation plan summary (from .cursor/plans/arrietty-thread-updates-implementation.plan.md)

**Order:** (1) Routing fix: LifecycleContext + deliveryChannelId; LifecycleContextStore + deliveryTargetToContextId + getByDeliveryTargetId; OutboundDeliveryRouter send/getGatewayForChannel use getByDeliveryTargetId. (2) DiscordGateway + JdaDiscordGateway createThreadChannel(parentChannelId, threadName). (3) CreateThreadAction + Bootstrap register create_thread. (4) CreateLifecycleContextAction read deliveryChannelId from bind/state (exclude THREAD_CREATE_FAILED); LifecycleRunRecord + deliveryChannelId; LaunchCursorRunAction pass deliveryChannelId into record. (5) PostChannelMessageAction send target = deliveryChannelId or channelId, sentinel fallback; CursorCloudRunMonitor send target from runState.getDeliveryChannelId(), messageId = null when sending to thread. (6) config/bots.yaml: create_thread step (storeIn: deliveryChannelId), optional branch on THREAD_CREATE_FAILED, then existing sequence. (7) Tests, docs, specs.

**Sentinel:** THREAD_CREATE_FAILED — only workflow branch, PostChannelMessageAction, and CursorCloudRunMonitor interpret it; never store in deliveryTargetToContextId; never pass to router.send or getGatewayForChannel; callers fall back to channelId when sendTarget is sentinel.

**replyToMessageId:** When monitor send target is thread (deliveryChannelId set), pass null for messageId to sender.send; when target is room channel, use runState.getReplyToMessageId().

---

## Schema constraints

- Requirement keys: id, title, statement, status, priority, type, behavior, acceptance, traceability, validation, anti_patterns (optional).
- Asset keys: id, kind, path, role, requires, feature_ids. No new spec keys; extend existing assets/requirements in place.
