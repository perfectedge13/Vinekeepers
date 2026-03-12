# How it works

# Overview

`DiscordEventSource` implements `EventSource` and delegates runtime receive/send work to `JdaDiscordGateway`. The gateway connects to Discord with `DISCORD_BOT_TOKEN`, converts live Discord messages into internal `Event` objects, and preserves mention metadata so routing can match bot mentions without relying only on raw text parsing.

# Flow

1. `Bootstrap` starts `DiscordEventSource` with `EventBus`.
2. `DiscordEventSource` connects the gateway and subscribes live Discord message events.
3. The gateway converts Discord messages into internal `Event` payloads with `channelId`, `authorId`, `author` (username), `messageId`, `content`, and normalized `mentions` so routing can apply `discordAuthors` by id or username.
4. Downstream routing reads both message text and the `mentions` payload field to resolve mention-based bot activation.
5. The gateway exposes `createTextChannel(guildId, channelName)` for lifecycle room provisioning; the `create_channel` workflow action delegates to it. When channel name is blank, the action derives it from workflow state (e.g. project + codeChange). The action **normalizes** the channel name to Discord-safe format (lowercase, allowed characters) before calling the gateway. On gateway failure the action returns the sentinel `CHANNEL_CREATE_FAILED` so the workflow can branch to a failure path.

# Reply path

Reply delivery goes through **OutboundDeliveryRouter**. The router implements `DiscordReplySender` and resolves which sender (gateway + token) to use:

- **Non-lifecycle channels:** The default sender is used (e.g. the primary Discord bot token).
- **Lifecycle channels:** A lifecycle context keyed by channelId yields `configuredBotId`. The router uses that bot's registered sender only. If no sender is registered for that bot (e.g. token not configured), the router **does not** silently fall back to another bot—it logs an error and does not send the message.

Bootstrap registers one sender per bot when the bot's config has `discordTokenEnvKey` set and the corresponding environment variable is present, so multiple Discord identities (e.g. Luna and Arrietty) can be used for different channels. The engine or Cursor run monitor sends replies via the sink; `DiscordAppReplySink` uses the router for ChannelTarget delivery. The gateway replies to the original message when a `messageId` is present and falls back to a plain channel message when needed.

# Inputs and outputs

- **Inputs:** Live Discord messages with content, channel, author, and mention metadata; `DISCORD_BOT_TOKEN` for connector startup.
- **Outputs:** Internal `Event` objects on `EventBus` with mention metadata available for routing, plus workflow and Cursor status replies sent back through `DiscordReplySender`.

