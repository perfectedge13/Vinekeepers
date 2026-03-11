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

The engine can set a `DiscordReplySender` (`DiscordEventSource` implements it). When a workflow or the Cursor run monitor produces a reply, the engine or monitor sends that reply back to Discord via `send(channelId, messageId, content)`. The gateway replies to the original message when a `messageId` is present and falls back to a plain channel message when needed.

# Inputs and outputs

- **Inputs:** Live Discord messages with content, channel, author, and mention metadata; `DISCORD_BOT_TOKEN` for connector startup.
- **Outputs:** Internal `Event` objects on `EventBus` with mention metadata available for routing, plus workflow and Cursor status replies sent back through `DiscordReplySender`.

