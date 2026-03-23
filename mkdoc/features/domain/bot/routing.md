# Event routing and normalized context

# Status

active

# Summary

Route events to bots by routing rules (REQ-BOT-001). `Router` evaluates all `RoutingRule` filters first, then for Discord may override the result: **FeatureRoomState** on the **intake/spec thread** id routes **message** and **interaction** events to the **primary coordinator** only when that id resolves (single planning ingress); if the thread has state but no coordinator, Router falls back to participant **configuredBotIds** in stable **PlanningRole** order. The **room channel** routes to the **primary coordinator** only (low-noise). If no feature-room hit, a lifecycle-owned channel with **handlesOwnedSpaces** yields **single-owner precedence**; if the owner lacks **handlesOwnedSpaces**, the Router logs a warning and uses the filter-based list. Discord mention/trigger filters apply only to **message** events; **interaction** events use author, channel, and other non-mention criteria.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ROUTER | Match events to bots by routing rules and ownership; depends on LifecycleContextStore and optional FeatureRoomStateStore; feature room: room channel → coordinator only; intake thread **messages** and **interactions** → coordinator only when coordinator resolves; else participant configuredBotIds in role order; when lifecycle context exists and owner has handlesOwnedSpaces, single-owner precedence when applicable; when owner lacks handlesOwnedSpaces, logs warning and uses filter-based routing; discordAuthors by actorId or actorUsername; structured **INFO** log when no bot matched (channelId, authorId, actorUsername, mentions, ingestBotId, truncated textPrefix) | src/main/java/com/vinekeepers/bot/Router.java |
| ASSET-ROUTING | One routing policy rule (filter + botId) | src/main/java/com/vinekeepers/bot/RoutingRule.java |
| ASSET-EVENT-FILTER | Filter events by criteria | src/main/java/com/vinekeepers/bot/EventFilter.java |
| ASSET-ROUTING-FILTER | Apply routing filters to events; for Discord, `discordTrigger` and `discordMention` apply only to message events; `discordAuthors` and `discordChannels` apply to all Discord events | src/main/java/com/vinekeepers/bot/RoutingFilter.java |
| ASSET-NORMALIZED-EVENT-CONTEXT | Normalize payloads for routing and session decisions; exposes `actorId` and `actorUsername` (e.g. from payload author/authorId); for Discord text, collects user/bot mention snowflakes only from `<@…>` / `<@!…>` with numeric ids (ignores `<@&…>` and non-snowflake bracket tokens) | src/main/java/com/vinekeepers/bot/NormalizedEventContext.java |

# Sub-pages

- [How it works](routing/how-it-works.md)
- [Change log](routing/change-log.md)
- [Known issues](routing/known-issues.md)
- [Decisions](routing/decisions.md)
- [Contracts](routing/contracts.md)
- [Tests](routing/tests.md)
- [Diagrams](routing/diagrams.md)

