# Decisions

# Entries

## 2026-03-12 — Ownership-based routing (handlesOwnedSpaces)

**Context:** Lifecycle rooms (e.g. created by Luna with Arrietty as owner) need inbound events (messages, button clicks, etc.) to be handled only by the room owner bot, not by Luna or other bots that might match by mention or channel. **Decision:** Add optional per-bot `handlesOwnedSpaces` in YAML; when true, Router checks `LifecycleContextStore` for the event's channel and, if the context's `configuredBotId` is that bot, returns only that bot (single-owner precedence). Bootstrap passes the store and a map of botId → handlesOwnedSpaces from ConfigLoader to Router. **Consequence:** No hardcoded bot ids in Router; Arrietty sets `handlesOwnedSpaces: true` and `workflowRef: arrietty_room` so it exclusively handles room events; docs and specs updated for Router, BotDefinition, ConfigLoader, Bootstrap, and config.

## 2026-03-09 — Discord interaction routing (no trigger/mention for interactions)

**Context:** Discord interaction events (button clicks, modal submits) were being evaluated against `discordTrigger`/`discordMention`, which are message-only concepts; interactions from allowed users failed to route. **Decision:** Apply `discordTrigger` and `discordMention` only to Discord message events; for Discord interaction events, routing uses author, channel, and other criteria only (e.g. `discordAuthors`, `discordChannels`). **Consequence:** Button and modal responses from allowed authors route to the bot without requiring an @mention; README and core-registry updated for routing sentence.

## 2026-03-09 — Discord filter by user (discordAuthors)

**Context:** Luna (and other bots) needed a way to restrict activation to specific Discord users when using mention-based routing. **Decision:** Add optional `discordAuthors` to routing filter in YAML; NormalizedEventContext exposes `actorId` and `actorUsername` from payload author/authorId; RoutingFilter matches when the event author is in the list. **Consequence:** Luna can be configured with e.g. `discordAuthors: [novawilde13_72571]` so only that user triggers the bot when mentioning @Luna; gateway must supply author in payload.

# Selected decisions (prior)

- Routing is evaluated over a normalized event view so Discord and GitHub payloads can share one filter model.
- Discord mention activation is treated as routing input and may come from payload metadata or parsed `@mention` text.
- Bot runtime definition and tool policy are documented separately because routing selects bots before execution policy applies.

