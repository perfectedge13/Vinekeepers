# Bot definition, routing, and tool policy

# Status

active

# Summary

Route events to bots by routing rules (REQ-BOT-001), tool policy allow/deny and approval (REQ-BOT-002), bot definition and persona/model (REQ-BOT-003). Router, Routing, EventFilter, RoutingFilter, ToolPolicy, BotDefinition, Persona, ModelProfile, MemoryPolicy.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ROUTER | Match events to bots by routing rules | src/main/java/com/vinekeepers/bot/Router.java |
| ASSET-ROUTING | Routing and filter configuration | src/main/java/com/vinekeepers/bot/Routing.java |
| ASSET-EVENT-FILTER | Filter events by criteria | src/main/java/com/vinekeepers/bot/EventFilter.java |
| ASSET-ROUTING-FILTER | Apply routing filters to events | src/main/java/com/vinekeepers/bot/RoutingFilter.java |
| ASSET-TOOL-POLICY | Allow/deny and approval for tools | src/main/java/com/vinekeepers/bot/ToolPolicy.java |
| ASSET-BOT-DEFINITION | Bot composition (persona, model, workflow, tools) | src/main/java/com/vinekeepers/bot/BotDefinition.java |
| ASSET-PERSONA | Bot persona and tone | src/main/java/com/vinekeepers/bot/Persona.java |
| ASSET-MODEL-PROFILE | LLM/model settings per bot | src/main/java/com/vinekeepers/bot/ModelProfile.java |
| ASSET-MEMORY-POLICY | Session and memory policy per bot | src/main/java/com/vinekeepers/bot/MemoryPolicy.java |

# Sub-pages

- [How it works](bot/how-it-works.md)
- [Change log](bot/change-log.md)
- [Known issues](bot/known-issues.md)
- [Decisions](bot/decisions.md)
- [Contracts](bot/contracts.md)
- [Tests](bot/tests.md)
- [Diagrams](bot/diagrams.md)
