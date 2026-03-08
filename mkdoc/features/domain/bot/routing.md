# Event routing and normalized context

# Status

active

# Summary

Route events to bots by routing rules (REQ-BOT-001). `Router` matches events to configured bots through `Routing`, `EventFilter`, and `RoutingFilter` over `NormalizedEventContext`, including Discord mention-based activation from payload metadata or `@mention` text when configured.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ROUTER | Match events to bots by routing rules, including Discord mention filters | src/main/java/com/vinekeepers/bot/Router.java |
| ASSET-ROUTING | Routing and filter configuration | src/main/java/com/vinekeepers/bot/Routing.java |
| ASSET-EVENT-FILTER | Filter events by criteria | src/main/java/com/vinekeepers/bot/EventFilter.java |
| ASSET-ROUTING-FILTER | Apply routing filters to events, including `discordTrigger` and `discordMention` matching | src/main/java/com/vinekeepers/bot/RoutingFilter.java |
| ASSET-NORMALIZED-EVENT-CONTEXT | Normalize payloads for routing and session decisions | src/main/java/com/vinekeepers/bot/NormalizedEventContext.java |

# Sub-pages

- [How it works](routing/how-it-works.md)
- [Change log](routing/change-log.md)
- [Known issues](routing/known-issues.md)
- [Decisions](routing/decisions.md)
- [Contracts](routing/contracts.md)
- [Tests](routing/tests.md)
- [Diagrams](routing/diagrams.md)

