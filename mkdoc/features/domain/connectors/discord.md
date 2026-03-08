# Discord event source and reply

# Status

active

# Summary

Discord event source (REQ-CONNECTORS-DISCORD-001). `DiscordEventSource` implements `EventSource` and `DiscordReplySender`; it delegates live Discord receive/send work through the `DiscordGateway` contract to the JDA-backed `JdaDiscordGateway`, preserves mention metadata in the internal event payload for downstream routing, and, when used as the engine’s reply sender, delivers workflow replies and Cursor status updates back to Discord. Assets: `DiscordEventSource`, `DiscordReplySender`, `DiscordGateway`, `JdaDiscordGateway`.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-DISCORD-SOURCE | Discord event source and mention metadata bridge | src/main/java/com/vinekeepers/connectors/DiscordEventSource.java |
| ASSET-DISCORD-REPLY | Discord reply sender interface | src/main/java/com/vinekeepers/connectors/DiscordReplySender.java |
| ASSET-DISCORD-GATEWAY-CONTRACT | Discord gateway contract for receive/send operations | src/main/java/com/vinekeepers/connectors/DiscordGateway.java |
| ASSET-DISCORD-GATEWAY | JDA-backed Discord gateway for live receive/send operations | src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java |

# Sub-pages

- [How it works](discord/how-it-works.md)
- [Change log](discord/change-log.md)
- [Known issues](discord/known-issues.md)
- [Decisions](discord/decisions.md)
- [Contracts](discord/contracts.md)
- [Tests](discord/tests.md)
- [Diagrams](discord/diagrams.md)

