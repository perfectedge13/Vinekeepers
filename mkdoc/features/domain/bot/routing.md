# Event routing and normalized context

# Status

active

# Summary

Route events to bots by routing rules (REQ-BOT-001). `Router` matches events to configured bots through `Routing`, `EventFilter`, and `RoutingFilter` over `NormalizedEventContext`, including Discord mention-based activation from payload metadata or `@mention` text when configured, and optional Discord author filtering (`discordAuthors`) by actor id or normalized actor username. When a Discord channel has a lifecycle context and the owner bot has **handlesOwnedSpaces**, the Router applies **single-owner precedence** (only that bot is returned). If the channel has a lifecycle owner but that bot does *not* have **handlesOwnedSpaces**, the Router logs a warning and uses filter-based routing. Discord trigger and mention filters apply only to message events; for Discord interaction events routing uses author, channel, and other criteria only (no trigger/mention check).

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ROUTER | Match events to bots by routing rules and ownership; when lifecycle context exists for a channel and owner bot has handlesOwnedSpaces, single-owner precedence; when owner lacks handlesOwnedSpaces, logs warning and uses filter-based routing; discordAuthors by actorId (numeric) or actorUsername (case-insensitive); DEBUG log when no bot matched | src/main/java/com/vinekeepers/bot/Router.java |
| ASSET-ROUTING | Routing and filter configuration | src/main/java/com/vinekeepers/bot/Routing.java |
| ASSET-EVENT-FILTER | Filter events by criteria | src/main/java/com/vinekeepers/bot/EventFilter.java |
| ASSET-ROUTING-FILTER | Apply routing filters to events; for Discord, `discordTrigger` and `discordMention` apply only to message events; `discordAuthors` and `discordChannels` apply to all Discord events | src/main/java/com/vinekeepers/bot/RoutingFilter.java |
| ASSET-NORMALIZED-EVENT-CONTEXT | Normalize payloads for routing and session decisions; exposes `actorId` and `actorUsername` (e.g. from payload author/authorId) | src/main/java/com/vinekeepers/bot/NormalizedEventContext.java |

# Sub-pages

- [How it works](routing/how-it-works.md)
- [Change log](routing/change-log.md)
- [Known issues](routing/known-issues.md)
- [Decisions](routing/decisions.md)
- [Contracts](routing/contracts.md)
- [Tests](routing/tests.md)
- [Diagrams](routing/diagrams.md)

