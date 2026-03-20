# Contracts

# APIs

None. Routing is an internal runtime capability consumed by the engine.

# Schemas

`RoutingRule` holds filter and botId; routing is an ordered policy list; when a Discord channel has a lifecycle owner with handlesOwnedSpaces, that owner is chosen regardless of filter order. Filter configuration includes optional `discordAuthors` list for Discord author filtering. For Discord, `discordTrigger` and `discordMention` apply only to message events; `discordAuthors` and `discordChannels` apply to all Discord events. Bot config may set optional **handlesOwnedSpaces** (boolean) per bot; when true, the Router uses lifecycle context for that channel and returns only that bot when it is the owner (single-owner precedence). `NormalizedEventContext` exposes connector-neutral fields: `actorId`, `actorUsername` (from payload author/authorId), channel, text, mentions, repo, label, etc.; and for events with `kind: interaction`, optional interaction payload: `interactionId`, `token`, `customId`, `values` for capture steps and intent handling.

# Interfaces

- **`Router`:** collects matching `botId`s from all routing rules, then for Discord channels applies **feature-room** overrides (intake thread **messages** → participant bot ids in role order; intake thread **interactions** → primary coordinator only; room channel → coordinator only) before **lifecycle** single-owner precedence; if no override applies, returns deduped filter matches. Depends on **LifecycleContextStore**, optional **FeatureRoomStateStore**, and a per-bot `handlesOwnedSpaces` map (from config).
- **`EventFilter`:** contract for evaluating an event against routing criteria.
- **`RoutingFilter`:** built-in filter implementation for configured predicates.
- **`NormalizedEventContext`:** normalized view of connector payload fields used for routing and session decisions.

