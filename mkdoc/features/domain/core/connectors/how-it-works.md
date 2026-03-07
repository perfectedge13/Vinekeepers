# How it works

# Overview

DiscordEventSource and GitHubEventSource implement EventSource. On start(EventBus), they connect to external APIs and translate incoming payloads into internal Events, then publish to the bus. Connectors emit events when started and when external events occur.

# Flow

1. Bootstrap starts connectors with EventBus.
2. Each connector registers with Discord/GitHub (or equivalent).
3. Incoming external events → translated to Event → EventBus.publish.

# Inputs and outputs

- **Inputs:** External API payloads (Discord messages, GitHub webhooks). **Outputs:** Internal Events on EventBus.
