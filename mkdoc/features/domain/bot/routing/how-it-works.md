# How it works

# Overview

`Router` evaluates each incoming `Event` against configured `Routing` rules through `EventFilter` and `RoutingFilter`. `NormalizedEventContext` extracts connector-specific payload details into a shared view (including `actorId` and `actorUsername` from payload author/authorId) so filters can match fields such as actor, channel, text, mentions, repository, and labels consistently. For Discord, `discordTrigger` and `discordMention` are enforced only for message events; for Discord interaction events routing uses author, channel, and other criteria only (no trigger or mention check).

# Flow

1. A connector publishes an internal `Event`.
2. `NormalizedEventContext` derives normalized routing fields from the payload.
3. `RoutingFilter` evaluates configured criteria; for Discord message events: repositories, labels, channels, `discordTrigger`, `discordMention`, `discordAuthors`; for Discord interaction events: author, channel, and other non-trigger/mention criteria only.
4. `Router` returns the bot ids whose filters accept the event.

# Inputs and outputs

- **Inputs:** Incoming `Event`, bot routing configuration, and normalized payload fields.
- **Outputs:** Matching bot ids for downstream workflow and reasoner execution.

