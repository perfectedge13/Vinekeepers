# Contracts

# APIs

Discord API, GitHub API (or webhooks). Connectors translate to internal Event format.

# Schemas

Event: sourceId, kind, payload. Payload shape per connector (e.g. message content, PR data).

# Interfaces

EventSource: start(EventBus). DiscordEventSource, GitHubEventSource implement EventSource.
