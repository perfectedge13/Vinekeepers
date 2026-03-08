# How it works

# Overview

GitHubEventSource implements EventSource. On start(EventBus), it connects to the GitHub API and translates incoming payloads (e.g. webhooks) into internal Events, then publishes to the bus. The connector emits events when started and when GitHub events occur.

# Flow

1. Bootstrap starts GitHubEventSource with EventBus.
2. Connector registers with GitHub (or equivalent).
3. Incoming GitHub events → translated to Event → EventBus.publish.

# Inputs and outputs

- **Inputs:** GitHub API payloads (webhooks, etc.). **Outputs:** Internal Events on EventBus.

