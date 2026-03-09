# Contracts

# APIs

None. Routing is an internal runtime capability consumed by the engine.

# Schemas

`Routing` holds filter configuration (including optional `discordAuthors` list for Discord author filtering). For Discord, `discordTrigger` and `discordMention` apply only to message events; `discordAuthors` and `discordChannels` apply to all Discord events. `NormalizedEventContext` exposes connector-neutral fields: `actorId`, `actorUsername` (from payload author/authorId), channel, text, mentions, repo, label, etc.; and for events with `kind: interaction`, optional interaction payload: `interactionId`, `token`, `customId`, `values` for capture steps and intent handling.

# Interfaces

- **`Router`:** returns the bot ids whose routing rules match the event.
- **`EventFilter`:** contract for evaluating an event against routing criteria.
- **`RoutingFilter`:** built-in filter implementation for configured predicates.
- **`NormalizedEventContext`:** normalized view of connector payload fields used for routing and session decisions.

