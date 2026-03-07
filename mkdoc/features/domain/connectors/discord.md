# Discord event source

# Status

active

# Summary

Discord event source (REQ-CONNECTORS-DISCORD-001). DiscordEventSource implements EventSource and DiscordReplySender; it translates Discord payloads into internal events and, when used as the engine’s reply sender, delivers workflow replies (e.g. Luna) back to Discord. Assets: DiscordEventSource, DiscordReplySender.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-DISCORD-SOURCE | Discord event source | src/main/java/com/vinekeepers/connectors/DiscordEventSource.java |
| ASSET-DISCORD-REPLY | Discord reply sender interface | src/main/java/com/vinekeepers/connectors/DiscordReplySender.java |

# Sub-pages

- [How it works](discord/how-it-works.md)
- [Change log](discord/change-log.md)
- [Known issues](discord/known-issues.md)
- [Decisions](discord/decisions.md)
- [Contracts](discord/contracts.md)
- [Tests](discord/tests.md)
- [Diagrams](discord/diagrams.md)
