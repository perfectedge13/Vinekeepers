# Contracts

# APIs

None. Routing is an internal runtime capability consumed by the engine.

# Schemas

`Routing` holds filter configuration (including optional `discordAuthors` list for Discord author filtering). For Discord, `discordTrigger` and `discordMention` apply only to message events; `discordAuthors` and `discordChannels` apply to all Discord events. Bot config may set optional **handlesOwnedSpaces** (boolean) per bot; when true, the Router uses lifecycle context for that channel and returns only that bot when it is the owner (single-owner precedence). `NormalizedEventContext` exposes connector-neutral fields: `actorId`, `actorUsername` (from payload author/authorId), channel, text, mentions, repo, label, etc.; and for events with `kind: interaction`, optional interaction payload: `interactionId`, `token`, `customId`, `values` for capture steps and intent handling.

# Interfaces

- **`Router`:** returns the bot ids whose routing rules match the event. Depends on `LifecycleContextStore` and a per-bot `handlesOwnedSpaces` map (from config). When a Discord channel has a lifecycle context and the context's owner bot has `handlesOwnedSpaces`, only that bot is returned (single-owner precedence); otherwise filter-based matching applies.
- **`EventFilter`:** contract for evaluating an event against routing criteria.
- **`RoutingFilter`:** built-in filter implementation for configured predicates.
- **`NormalizedEventContext`:** normalized view of connector payload fields used for routing and session decisions.

