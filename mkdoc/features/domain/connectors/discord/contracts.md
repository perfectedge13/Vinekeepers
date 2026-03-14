# Contracts

# APIs

`EventSource.start(EventBus)` — connector registers with the bus and begins emitting events.

# Schemas

Event payload includes Discord message fields such as `content`, `channelId`, `authorId`, and optional `mentions`. Reply delivery uses `channelId`, `messageId`, and `content`.

# Interfaces

- **`ConnectorRegistry`:** `register(connectorId, adapter)`, `get(connectorId)` to store and retrieve connector adapters by id (e.g. `discord`).
- **`ConnectorAdapter`:** `registerBots(List<BotDefinition>, ConnectorContext)`; adapter performs per-bot gateway/sender/default registration only; Bootstrap retains sink and action registration.
- **`ConnectorContext`:** Generic context for adapters: EventBus, OutboundDeliveryRouter only; no Discord-named fields; Discord-specific options in DiscordConnectorConfig.
- **`DiscordConnectorConfig`:** Discord-specific config (e.g. defaultTokenEnvKey from root defaultDiscordTokenEnvKey) passed to DiscordConnectorAdapter constructor.
- **`DiscordConnectorAdapter`:** Implements ConnectorAdapter; constructor takes DiscordConnectorConfig and routedBotIds; registerBots does per-bot gateway/sender/default registration only.
- **`EventSource`:** `start(EventBus)` and lifecycle methods for publishing connector events.
- **`ReplySender`:** Generic sender contract (`send(channelId, messageId, content)`). Core uses this interface; **DiscordReplySender** extends it.
- **`OutboundGateway`:** Generic gateway contract (transitional). Core uses this interface; **DiscordGateway** extends it for receive/send and interaction operations.
- **`DiscordReplySender`:** Extends `ReplySender`; `send(channelId, messageId, content)` for routing workflow replies back to Discord. Implemented by `OutboundDeliveryRouter` for actual sender resolution.
- **`OutboundDeliveryRouter`:** Implements `ReplySender`. Resolves sender from delivery target and optional lifecycle context (by channelId → configuredBotId). `getGatewayForChannel(channelId)` returns the gateway for that channel when the lifecycle bot has one, or **null** when the lifecycle bot has no gateway (no default). `getSelfUserIdForBot(botId)` returns that bot's Discord user id from its gateway `getSelfUserId()`, or null. Registers senders per bot when `discordTokenEnvKey` is set. For lifecycle channels, uses only the configured bot's sender; no silent fallback when that sender is unavailable.
- **`DiscordGateway`:** Extends `OutboundGateway`. In addition to receive/send and interaction lifecycle, provides `createTextChannel(guildId, channelName)` (returns channel id or null on failure), `getSelfUserId()` (returns the bot's Discord user id), and `addPermissionOverride(channelId, guildId, targetUserId, allow, deny)` for lifecycle room permission overwrites. The workflow action `create_channel` delegates to createTextChannel and may call addPermissionOverride for the lifecycle owner bot. Gateways may be outbound-only (no event listeners) for bots not in routing. **DiscordAppReplySink** casts to `DiscordGateway` when calling interaction-specific methods (e.g. defer, update).

