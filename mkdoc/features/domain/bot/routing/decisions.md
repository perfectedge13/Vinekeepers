# Decisions

# Entries

## 2026-03-09 — Discord filter by user (discordAuthors)

**Context:** Luna (and other bots) needed a way to restrict activation to specific Discord users when using mention-based routing. **Decision:** Add optional `discordAuthors` to routing filter in YAML; NormalizedEventContext exposes `actorId` and `actorUsername` from payload author/authorId; RoutingFilter matches when the event author is in the list. **Consequence:** Luna can be configured with e.g. `discordAuthors: [novawilde13_72571]` so only that user triggers the bot when mentioning @Luna; gateway must supply author in payload.

# Selected decisions (prior)

- Routing is evaluated over a normalized event view so Discord and GitHub payloads can share one filter model.
- Discord mention activation is treated as routing input and may come from payload metadata or parsed `@mention` text.
- Bot runtime definition and tool policy are documented separately because routing selects bots before execution policy applies.

