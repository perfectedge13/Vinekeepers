# How it works

# Overview

`DiscordEventSource` implements `EventSource` and delegates runtime receive/send work to `JdaDiscordGateway`. The gateway connects to Discord using the token resolved from bot config (each bot's `discordTokenEnvKey` or top-level `defaultDiscordTokenEnvKey` in `config/bots.yaml`; the actual token is read from the env var named there). It converts live Discord messages into internal `Event` objects and preserves mention metadata so routing can match bot mentions without relying only on raw text parsing.

# Flow

1. `Bootstrap` starts `DiscordEventSource` with `EventBus`.
2. `DiscordEventSource` connects the gateway and subscribes live Discord message events.
3. The gateway converts Discord messages into internal `Event` payloads with `channelId`, `authorId`, `author` (username), `messageId`, `content`, and normalized `mentions` so routing can apply `discordAuthors` by id or username.
4. Downstream routing reads both message text and the `mentions` payload field to resolve mention-based bot activation.
5. The gateway exposes `createTextChannel(guildId, channelName)` for lifecycle room provisioning; the `create_channel` workflow action delegates to it. When channel name is blank, the action derives it from workflow state: the repo segment is the **repository name only** (segment after the last `/` in project; no owner/username), plus a slug from codeChange and a uniqueness suffix. The action **normalizes** the channel name to Discord-safe format (lowercase, allowed characters) before calling the gateway. On gateway failure the action returns the sentinel `CHANNEL_CREATE_FAILED` so the workflow can branch to a failure path.

# Reply target resolution

Bootstrap registers **DiscordReplyTargetResolver** with the engine (e.g. `engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver())`). The engine uses it by connector id to resolve `Event` to **ReplyTarget** (InteractionTarget or ChannelTarget) from the Discord payload (channelId, messageId, interactionId, token, deferred). When no resolver is registered or the resolver returns empty, the engine does not deliver the reply (fail closed); when delivering, the engine uses **target.channelId()** and **target.messageId()**.

# Reply path

Reply delivery goes through **OutboundDeliveryRouter**. The router (1) **routes** outbound delivery (ReplySender) by target and lifecycle context—implements the generic **ReplySender** contract and resolves which sender (gateway + token) to use; (2) **resolves gateways** by bot/channel for connector-owned code; the **gateway** is the **connector execution surface** for channel/thread/permission and send operations.

- **Non-lifecycle channels:** The default sender is used (e.g. the primary Discord bot token).
- **Lifecycle channels:** A lifecycle context keyed by channelId yields `configuredBotId`. The router uses that bot's registered sender only. If no sender is registered for that bot (e.g. token not configured), the router **does not** silently fall back to another bot—it logs an error and does not send the message.

`getGatewayForChannel(channelId)` returns the gateway (connector execution surface) for that channel when the lifecycle context's configured bot has one registered; it returns **null** when the lifecycle bot has no gateway (no default fallback). `getSelfUserIdForBot(botId)` returns that bot's Discord user id from its gateway's `getSelfUserId()`, or null if the bot has no gateway. **OutboundGateway** is the connector execution surface with Discord-shaped API; used only by connector code (e.g. DiscordSpaceOperations, DiscordAppReplySink) and the router. The gateway exposes `getSelfUserId()` and `addPermissionOverride(channelId, guildId, targetUserId, allow, deny)` so workflow actions (e.g. `create_channel` with `lifecycleOwnerBotId`) can grant the lifecycle bot permission on the new channel.

Bootstrap registers one sender per bot when the bot's config has `discordTokenEnvKey` set and the corresponding environment variable is present, so multiple Discord identities (e.g. Luna and Arrietty) can be used for different channels. **DiscordConnectorAdapter** registers **inbound** JDA listeners when the bot is in YAML **routing** or has **`identities.discord.handlesOwnedSpaces: true`** (lifecycle space owner); otherwise the gateway is **outbound-only** (no listeners). Lifecycle owners must be inbound so **component interactions** on messages sent by that bot (e.g. thread approval buttons) are received and deferred. The engine or Cursor run monitor sends replies via the sink; `DiscordAppReplySink` uses the router for ChannelTarget delivery and **casts to DiscordGateway** for interaction-specific methods (e.g. defer, update); when `getGatewayForChannel` returns null the sink does not send and logs. The gateway replies to the original message when a `messageId` is present and falls back to a plain channel message when needed.

# Inputs and outputs

- **Inputs:** Live Discord messages with content, channel, author, and mention metadata; Discord token(s) from env vars whose names are set in `config/bots.yaml` (`discordTokenEnvKey` per bot or `defaultDiscordTokenEnvKey`).
- **Outputs:** Internal `Event` objects on `EventBus` with mention metadata available for routing, plus workflow and Cursor status replies sent back through `DiscordReplySender`.

