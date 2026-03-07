# How it works

# Overview

DiscordEventSource implements EventSource. On start(EventBus), it connects to the Discord API and translates incoming payloads into internal Events, then publishes to the bus. The connector emits events when started and when Discord events occur.

# Flow

1. Bootstrap starts DiscordEventSource with EventBus.
2. Connector registers with Discord (or equivalent).
3. Incoming Discord events → translated to Event → EventBus.publish.

# Reply path

The engine can set a DiscordReplySender (DiscordEventSource implements it). When a workflow produces a reply (e.g. Luna multi-turn messages), the engine sends that reply back to Discord via `sendReply(channelId, messageId, content)`. Stub implementation logs replies until a real Discord API integration is added.

# Inputs and outputs

- **Inputs:** Discord API payloads (messages, etc.). **Outputs:** Internal Events on EventBus; workflow replies sent back to Discord via DiscordReplySender when configured.
