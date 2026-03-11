# Contracts

# APIs

`EventSource.start(EventBus)` — connector registers with the bus and begins emitting events.

# Schemas

Event payload includes Discord message fields such as `content`, `channelId`, `authorId`, and optional `mentions`. Reply delivery uses `channelId`, `messageId`, and `content`.

# Interfaces

- **`EventSource`:** `start(EventBus)` and lifecycle methods for publishing connector events.
- **`DiscordReplySender`:** `send(channelId, messageId, content)` for routing workflow replies back to Discord.
- **`DiscordGateway`:** In addition to receive/send and interaction lifecycle, provides `createTextChannel(guildId, channelName)` for creating Discord text channels (e.g. lifecycle rooms). The workflow action `create_channel` delegates to this; returns channel id or null on failure.

