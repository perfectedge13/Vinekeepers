# How it works

# Overview

`DiscordEventSource` implements `EventSource` and delegates runtime receive/send work to `JdaDiscordGateway`. The gateway connects to Discord using the token resolved from bot config (each bot's `discordTokenEnvKey` or top-level `defaultDiscordTokenEnvKey` in `config/bots.yaml`; the actual token is read from the env var named there). It converts live Discord messages into internal `Event` objects and preserves mention metadata so routing can match bot mentions without relying only on raw text parsing.

# Flow

1. `Bootstrap` starts `DiscordEventSource` with `EventBus`.
2. `DiscordEventSource` connects the gateway and subscribes live Discord message events.
3. The gateway converts Discord messages into internal `Event` payloads with `channelId`, `authorId`, `author` (username), `messageId`, `content`, and normalized `mentions` so routing can apply `discordAuthors` by id or username.
4. Downstream routing reads both message text and the `mentions` payload field to resolve mention-based bot activation.
5. The gateway exposes `createTextChannel(guildId, channelName)` for lifecycle room provisioning; the `create_channel` workflow action delegates to it. When channel name is blank, the action derives it from workflow state: the repo segment is the **repository name only** (segment after the last `/` in project; no owner/username), plus a slug from codeChange and a uniqueness suffix. The action **normalizes** the channel name to Discord-safe format (lowercase, allowed characters) before calling the gateway. On gateway failure the action returns the sentinel `CHANNEL_CREATE_FAILED` so the workflow can branch to a failure path.

# Reply path

Reply delivery goes through **OutboundDeliveryRouter**. The router implements `DiscordReplySender` and resolves which sender (gateway + token) to use:

- **Non-lifecycle channels:** The default sender is used (e.g. the primary Discord bot token).
- **Lifecycle channels:** A lifecycle context keyed by channelId yields `configuredBotId`. The router uses that bot's registered sender only. If no sender is registered for that bot (e.g. token not configured), the router **does not** silently fall back to another bot—it logs an error and does not send the message.

`getGatewayForChannel(channelId)` returns the gateway for that channel when the lifecycle context's configured bot has one registered; it returns **null** when the lifecycle bot has no gateway (no default fallback). `getDiscordUserIdForBot(botId)` returns that bot's Discord user id from its gateway's `getSelfUserId()`, or null if the bot has no gateway. The gateway contract exposes `getSelfUserId()` and `addPermissionOverride(channelId, guildId, targetUserId, allow, deny)` so workflow actions (e.g. `create_channel` with `lifecycleOwnerBotId`) can grant the lifecycle bot permission on the new channel.

Bootstrap registers one sender per bot when the bot's config has `discordTokenEnvKey` set and the corresponding environment variable is present, so multiple Discord identities (e.g. Luna and Arrietty) can be used for different channels. Bots **not** in routing (e.g. Arrietty) may have an **outbound-only** gateway: no event listeners, used only for sending and for `getGatewayForChannel`/`getDiscordUserIdForBot` when that bot owns a lifecycle channel. The engine or Cursor run monitor sends replies via the sink; `DiscordAppReplySink` uses the router for ChannelTarget delivery; when `getGatewayForChannel` returns null the sink does not send and logs. The gateway replies to the original message when a `messageId` is present and falls back to a plain channel message when needed.

# Inputs and outputs

- **Inputs:** Live Discord messages with content, channel, author, and mention metadata; Discord token(s) from env vars whose names are set in `config/bots.yaml` (`discordTokenEnvKey` per bot or `defaultDiscordTokenEnvKey`).
- **Outputs:** Internal `Event` objects on `EventBus` with mention metadata available for routing, plus workflow and Cursor status replies sent back through `DiscordReplySender`.

