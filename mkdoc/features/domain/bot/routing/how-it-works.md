# How it works

# Overview

`Router` evaluates each incoming `Event` against configured `Routing` rules through `EventFilter` and `RoutingFilter`. `NormalizedEventContext` extracts connector-specific payload details into a shared view so filters can match fields such as actor, channel, text, mentions, repository, and labels consistently.

# Flow

1. A connector publishes an internal `Event`.
2. `NormalizedEventContext` derives normalized routing fields from the payload.
3. `RoutingFilter` evaluates configured criteria such as repositories, labels, channels, `discordTrigger`, or `discordMention`.
4. `Router` returns the bot ids whose filters accept the event.

# Inputs and outputs

- **Inputs:** Incoming `Event`, bot routing configuration, and normalized payload fields.
- **Outputs:** Matching bot ids for downstream workflow and reasoner execution.

