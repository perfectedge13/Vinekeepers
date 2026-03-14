# Change context (for plan_change / implement)

## Scope

**Request-derived:** Implement generic outbound delivery abstraction per `.cursor/plans/generic-outbound-delivery-abstraction.plan.md`: finish migration from DiscordReplySender to ReplySender in core-facing code, add OutboundGateway, rename getDiscordUserIdForBot → getSelfUserIdForBot, preserve all Discord behavior.

**Impacted registry slice:** core-registry.yml (REQ-CORE-002, REQ-CORE-003; ASSET-ENGINE, ASSET-BOOTSTRAP, ASSET-APP-REPLY-SINK, ASSET-OUTBOUND-RESPONSE, ASSET-REPLY-TARGET); connectors-registry.yml (REQ-CONNECTORS-DISCORD-001; ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-DISCORD-CONNECTOR-ADAPTER, ASSET-DISCORD-SOURCE); workflow-registry.yml (REQ-WORKFLOW-001, REQ-LUNA-001; ASSET-CREATE-CHANNEL-ACTION, ASSET-CREATE-THREAD-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-CURSOR-RUN-MONITOR).

**New types:** ReplySender (generic sender contract; add if absent and have DiscordReplySender extend it), OutboundGateway (new interface in connectors).

**Rename:** getDiscordUserIdForBot → getSelfUserIdForBot on OutboundDeliveryRouter; update call sites (CreateChannelAction), tests (OutboundDeliveryRouterTest), and spec/mkdoc text.

---

## Per feature / requirement / asset

### FEAT-ENGINE (core)

- **Feature:** Event-driven engine orchestration. doc_path: features/domain/core/engine.md. status: active. summary: VinekeepersEngine routes events to matched bots, executes workflow runners, applies reasoner output, emits replies.
- **REQ-CORE-003:** Event-driven engine routes events to bots. Statement: Engine receives events, routes to bots, runs workflow then reasoner, delivers replies via connector contract (AppReplySink); for Discord, OutboundDeliveryRouter resolves sender by target and lifecycle configuredBotId—no silent fallback. Criteria: Engine subscribes to bus; waiting-session routing for Discord; uses WorkflowRunner; reasoner gets workflow context and state; tool calls via ToolRunner; replies via sink registry and lifecycle methods; for Discord router resolves sender by target and lifecycle configuredBotId, no fallback when lifecycle bot has no sender. Tests: UNIT-ENGINE (VinekeepersEngineTest). Traceability: ASSET-ENGINE, ASSET-APP-REPLY-SINK, ASSET-OUTBOUND-RESPONSE, ASSET-REPLY-TARGET, etc.
- **ASSET-ENGINE:** src/main/java/com/vinekeepers/core/VinekeepersEngine.java. Role: Route events, run workflow/reasoner, deliver via connector sink; for Discord uses OutboundDeliveryRouter. **Implement:** Field and setter type ReplySender (replace DiscordReplySender).
- **ASSET-BOOTSTRAP:** src/main/java/com/vinekeepers/core/Bootstrap.java. Role: Wire engine, config, ConnectorRegistry, Discord adapter, registerBots. **Implement:** No signature change; ensure types compile (ReplySender, OutboundGateway).

**Doc excerpts (engine):** Decisions: Waiting-session routing for Discord; workflow before reasoner; ToolRunner for tool calls. How-it-works: Engine receives event → routing → WorkflowRunner → reasoner → audit → replies via connector paths.

### FEAT-CONNECTORS-DISCORD (connectors)

- **Feature:** Discord event source and reply. doc_path: features/domain/connectors/discord.md. status: active. summary: Discord event source and JDA gateway; reply delivery via OutboundDeliveryRouter.
- **REQ-CONNECTORS-DISCORD-001:** Discord event source and reply. Statement: Per-bot identity via getConnectorIdentity("discord"); ConnectorContext generic (EventBus, OutboundDeliveryRouter); OutboundDeliveryRouter resolves sender from delivery target and lifecycle configuredBotId; getGatewayForChannel; getDiscordUserIdForBot (rename to getSelfUserIdForBot); gateway createTextChannel/getSelfUserId/addPermissionOverride; DiscordReplySender; DiscordAppReplySink; lifecycle operations. Criteria: DiscordEventSource + EventSource; mention metadata; per-bot identity; router sender resolution from LifecycleContextStore/getByDeliveryTargetId then configuredBotId; no silent fallback for lifecycle; getGatewayForChannel null when lifecycle bot has no gateway; getDiscordUserIdForBot → getSelfUserIdForBot returns bot gateway getSelfUserId or null; gateway getSelfUserId and addPermissionOverride; optional outbound-only gateway; interaction ack/defer; DiscordAppReplySink lifecycle; DiscordReplySender send(channelId, messageId, content); createTextChannel for lifecycle room. Tests: UNIT-ENGINE-DISCORD-REPLY, UNIT-DISCORD-APP-REPLY-SINK, UNIT-OUTBOUND-DELIVERY-ROUTER, UNIT-CREATE-CHANNEL-ACTION-DISCORD, UNIT-CREATE-CHANNEL-ACTION-LIFECYCLE-OWNER, UNIT-DISCORD-CONNECTOR-ADAPTER, etc. Traceability: ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-CONNECTOR-CONTEXT, ASSET-DISCORD-CONNECTOR-ADAPTER, ASSET-DISCORD-SOURCE.
- **ASSET-OUTBOUND-DELIVERY-ROUTER:** src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java. Role: Routes outbound delivery; implements sender contract; resolves sender from target and lifecycle; getGatewayForChannel; getSelfUserIdForBot (renamed). **Implement:** Internal maps Map<String, ReplySender>, Map<String, OutboundGateway>; ReplySender defaultSender; OutboundGateway defaultGateway; registerSender(botId, ReplySender, OutboundGateway); setDefaultSender(ReplySender); setDefaultGateway(OutboundGateway); getGatewayForChannel → OutboundGateway; getDefaultGateway() → OutboundGateway; implement ReplySender; rename getDiscordUserIdForBot → getSelfUserIdForBot.
- **ASSET-DISCORD-REPLY:** src/main/java/com/vinekeepers/connectors/DiscordReplySender.java. Role: Interface for sending replies (channelId, messageId, content). **Implement:** Extend ReplySender so Discord implementations satisfy generic contract.
- **ASSET-DISCORD-GATEWAY-CONTRACT:** src/main/java/com/vinekeepers/connectors/DiscordGateway.java. Role: Gateway contract (send, getSelfUserId, isConnected, createTextChannel, createThreadChannel, addPermissionOverride, interaction methods). **Implement:** Extend OutboundGateway.
- **New ASSET-OUTBOUND-GATEWAY:** src/main/java/com/vinekeepers/connectors/OutboundGateway.java. Role: Small transitional generic gateway: send(channelId, messageId, content), getSelfUserId(), isConnected(), createTextChannel(guildId, channelName), createThreadChannel(parentChannelId, threadName), addPermissionOverride(channelId, guildId, targetUserId, allow, deny). DiscordGateway extends it.
- **ASSET-DISCORD-REPLY-SINK:** src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java. Role: Implements AppReplySink; uses router for sender resolution; lifecycle operations; cast to DiscordGateway where interaction-specific methods needed. **Implement:** Keep constructor(OutboundDeliveryRouter); getGatewayForChannel returns OutboundGateway; cast result to DiscordGateway at call sites for sendFollowUp, updateMessage, openModal, send with components; add comment on intentional DiscordGateway dependency (accepted debt).
- **ASSET-DISCORD-GATEWAY:** JdaDiscordGateway. Role: JDA-backed gateway. **Implement:** No class declaration change (implements DiscordGateway which extends OutboundGateway).

**Doc excerpts (discord):** Contracts: OutboundDeliveryRouter implements DiscordReplySender; getGatewayForChannel; getSelfUserIdForBot (update to getSelfUserIdForBot in docs). DiscordReplySender send(channelId, messageId, content). DiscordGateway createTextChannel, getSelfUserId, addPermissionOverride. How-it-works: Reply path via OutboundDeliveryRouter; lifecycle uses configured bot sender only; getGatewayForChannel null when lifecycle bot has no gateway; getSelfUserIdForBot for that bot's user id.

### FEAT-WORKFLOW-STEPS / FEAT-CURSOR-GATHERING (workflow)

- **REQ-WORKFLOW-001:** Workflow runners and steps; create_thread uses gateway; create_channel uses gateway createTextChannel and addPermissionOverride. REQ-LUNA-001: create_channel lifecycleOwnerBotId uses gateway getSelfUserId and addPermissionOverride for that bot (resolved via router getSelfUserIdForBot after rename).
- **ASSET-CREATE-CHANNEL-ACTION:** src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java. Role: create_channel via gateway; optional lifecycleOwnerBotId → addPermissionOverride for that bot. **Implement:** Use OutboundGateway from router (getDefaultGateway/getGatewayForChannel); call router.getSelfUserIdForBot(botId) (renamed).
- **ASSET-CREATE-THREAD-ACTION:** src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java. Role: create_thread via gateway; lifecycle owner gateway when channel has context. **Implement:** Use OutboundGateway from router.
- **ASSET-POST-CHANNEL-MESSAGE-ACTION:** src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java. Role: Post message to channel via sender. **Implement:** Constructor and field type ReplySender (replace DiscordReplySender).
- **ASSET-CURSOR-RUN-MONITOR:** src/main/java/com/vinekeepers/core/cursor/CursorCloudRunMonitor.java. Role: Poll Cursor runs, relay to Discord. **Implement:** Field and setter type ReplySender.

**Doc excerpts (cursor-gathering):** create_channel with lifecycleOwnerBotId calls addPermissionOverride for that bot's user (resolved via router getSelfUserIdForBot → getSelfUserIdForBot). create_thread uses lifecycle owner gateway. Contracts: create_channel returns CHANNEL_CREATE_FAILED on failure; addPermissionOverride for lifecycle owner bot.

---

## Plan summary (implement steps)

1. **ReplySender:** Add interface ReplySender with send(String channelId, String messageId, String content) if not present. DiscordReplySender extends ReplySender.
2. **OutboundGateway:** New interface with send, getSelfUserId, isConnected, createTextChannel, createThreadChannel, addPermissionOverride (same signatures as DiscordGateway for those six).
3. **DiscordGateway extends OutboundGateway.** JdaDiscordGateway unchanged.
4. **OutboundDeliveryRouter:** Maps and fields ReplySender/OutboundGateway; registerSender(botId, ReplySender, OutboundGateway); setDefaultSender(ReplySender); setDefaultGateway(OutboundGateway); getGatewayForChannel/getDefaultGateway return OutboundGateway; implement ReplySender; rename getDiscordUserIdForBot → getSelfUserIdForBot.
5. **VinekeepersEngine, CursorCloudRunMonitor, PostChannelMessageAction:** Use ReplySender for replySender field and setter/constructor.
6. **CreateChannelAction, CreateThreadAction:** Use OutboundGateway from router; CreateChannelAction calls getSelfUserIdForBot(botId).
7. **DiscordAppReplySink:** Cast router.getGatewayForChannel(…) to DiscordGateway where sendFollowUp/updateMessage/openModal/components needed; document intentional debt.
8. **DiscordConnectorAdapter, Bootstrap:** No behavioral change; ensure compilation with new types.
9. **Tests:** OutboundDeliveryRouterTest use ReplySender in RecordingSender (or DiscordReplySender extending ReplySender); stub gateway implements OutboundGateway; assert getSelfUserIdForBot. VinekeepersEngineTest, CursorCloudRunMonitorTest, PostChannelMessageActionTest use ReplySender in mocks. CreateChannelActionTest, CreateThreadActionTest stubs remain DiscordGateway (extends OutboundGateway). Rename test methods getDiscordUserIdForBot_* → getSelfUserIdForBot_* and assertions.
10. **Specs/docs:** Update connectors-registry and workflow-registry text: getSelfUserIdForBot; ReplySender and OutboundGateway in asset/requirement text. Update mkdoc discord and cursor-gathering: getSelfUserIdForBot; outbound delivery uses ReplySender and OutboundGateway; Discord implements both; sink documents cast to DiscordGateway.

---

## Guardrails (anti-patterns)

- Do not delete requirements; do not remove required functionality; do not invent new spec keys; repair drift before coding.
- Avoid anti_patterns on REQ-LUNA-001 (workflow-registry): no hardcoded Discord channel; no secrets in state; do not rely on discordTrigger alone for Luna; no Cursor API key/body logging; support multiple error shapes; NON_NULL serialization for Cursor requests.
- Feature slugs are not bot ids.

---

## Schema constraints

- Registries use existing req-registry schema: requirements (id, title, statement, status, acceptance.criteria, traceability.assets, validation.tests), assets (id, path, role, requires, feature_ids). No new top-level keys; update existing requirement statements and asset role text only (getSelfUserIdForBot, ReplySender, OutboundGateway).
