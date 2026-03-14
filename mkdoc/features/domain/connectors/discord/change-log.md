# Change log

# Entries

## 2026-03-13

- **Generic outbound delivery abstraction:** Core migrated to generic **ReplySender** (send(channelId, messageId, content)) and **OutboundGateway** (send, getSelfUserId, createTextChannel, createThreadChannel, addPermissionOverride). New `ReplySender.java` and `OutboundGateway.java`; **Discord** implements both (**DiscordReplySender** extends ReplySender, **DiscordGateway** extends OutboundGateway). **OutboundDeliveryRouter** implements ReplySender and exposes **getSelfUserIdForBot(botId)** for workflow actions (e.g. permission overwrites). **DiscordAppReplySink** casts to **DiscordGateway** for interaction-specific methods (defer, update, follow-up). See ASSET-REPLY-SENDER, ASSET-OUTBOUND-GATEWAY in connectors-registry.
- **ConnectorRegistry and connector-scoped identity:** **ConnectorRegistry** (register(connectorId, adapter), get(connectorId)), **ConnectorAdapter** (registerBots(List&lt;BotDefinition&gt;, ConnectorContext)), and **ConnectorContext** (EventBus, OutboundDeliveryRouter only; no Discord-named fields) introduced. **DiscordConnectorConfig** holds Discord-specific options (e.g. defaultTokenEnvKey from root defaultDiscordTokenEnvKey); **DiscordConnectorAdapter** implements ConnectorAdapter and takes config + routedBotIds; constructor receives DiscordConnectorConfig. Per-bot identity via `BotDefinition.getConnectorIdentity("discord")` (tokenEnvKey, handlesOwnedSpaces); adapter registers gateway/sender per bot when token present; Bootstrap keeps sink and action registration. handlesOwnedSpaces is connector-scoped for Discord lifecycle channels (single-owner precedence).
- **Delivery target channel or thread:** OutboundDeliveryRouter resolves sender from delivery target (channel id or thread id); LifecycleContextStore getByDeliveryTargetId used for both. getGatewayForChannel(deliveryTargetId) accepts thread or channel id so Cursor run updates can be sent to a thread when deliveryChannelId is set. Gateway contract unchanged; JDA gateway used for thread delivery when create_thread stores thread id in state.

## 2026-03-12

- **Lifecycle delivery follow-up:** `getGatewayForChannel(channelId)` returns null when the lifecycle channel's configured bot has no registered gateway (no default fallback). `getSelfUserIdForBot(botId)` returns that bot's Discord user id from its gateway `getSelfUserId`, or null. Gateway contract exposes `getSelfUserId()` and `addPermissionOverride(channelId, guildId, targetUserId, allow, deny)` for lifecycle room permission overwrites. Bootstrap may register an **outbound-only** gateway for bots not in routing (e.g. lifecycle room bot Arrietty); that gateway has no event listeners and is used only for sending. OutboundDeliveryRouter and DiscordAppReplySink use the router for sender resolution; when `getGatewayForChannel` returns null the sink does not send and logs. See REQ-CONNECTORS-DISCORD-001, OutboundDeliveryRouterTest, JdaDiscordGateway.
- **OutboundDeliveryRouter and lifecycle sender resolution:** New `OutboundDeliveryRouter` routes outbound Discord delivery and resolves the reply sender from the delivery target and optional lifecycle context (`configuredBotId`). For non-lifecycle channels a default sender is used; for lifecycle channels only the configured bot's sender is used—no silent fallback when that bot's sender is unavailable (error logged, message not sent). Per-bot Discord identity is supported via optional `discordTokenEnvKey` in bot config; Bootstrap registers one sender per bot when the key is set and token is present. `DiscordAppReplySink` uses the router for ChannelTarget delivery. See REQ-CONNECTORS-DISCORD-001 and OutboundDeliveryRouterTest.

## 2026-03-10

- **Lifecycle room (Phase 1):** The `create_channel` workflow action (used by Luna lifecycle room) delegates to gateway `createTextChannel(guildId, channelName)`; the action normalizes channel name to Discord-safe format before calling the gateway and returns sentinel `CHANNEL_CREATE_FAILED` on failure. Documented in workflow-steps and cursor-gathering; gateway contract unchanged.

## 2026-03-09

- **Discord intake initial components:** Gateway contract (`DiscordGateway`), JDA-backed gateway (`JdaDiscordGateway`), and reply sink (`DiscordAppReplySink`) updates for receive/send and interaction lifecycle; author (authorId, author) in payload for routing; adapter acks or defers within platform window; optional components on channel send and follow-up/update.
- **Author in payload for routing:** JdaDiscordGateway supplies author (authorId, author) in the internal event payload so routing can apply `discordAuthors` filter and NormalizedEventContext can expose actorId/actorUsername for matching.

## 2026-03-08

- **AppReplySink and rich intents:** Discord connector implements `AppReplySink` with lifecycle operations (respondImmediately, sendFollowUp, updateMessage, openModal); adapter acks or defers interactions within the platform window and may auto-defer when sync reply is not safely possible. Intents (PresentChoices, ConfirmAction, etc.) render to Discord with fallback to text. Engine delivers replies via sink registry by sourceId prefix.

## 2026-03-07

- The Discord reply path is now documented as part of the engine workflow and reasoner loop, including replies that originate from configured conversational workflows and are delivered back through `DiscordReplySender`.
- Discord docs now note that `DiscordEventSource` carries mention metadata so routing can match bot mentions such as `@Luna` case-insensitively from connector payloads or normalized text.
- Connector docs now describe the JDA-backed receive/send path, including mention metadata propagation and reply behavior that targets the originating message when possible.

## 2026-03-06

Implementation updates: DiscordReplySender.java, DiscordEventSource.java; tests DiscordEventSourceTest modified. Engine delivers workflow replies to Discord via reply path when source is Discord.

## Luna reply path

Engine sends workflow replies (e.g. Luna bot) back to Discord via DiscordReplySender; DiscordEventSource implements the interface and is set as the engine's reply sender when Discord is enabled.

