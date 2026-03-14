# Discord event source and reply

# Status

active

# Summary

Discord event source (REQ-CONNECTORS-DISCORD-001). Per-bot identity via `BotDefinition.getConnectorIdentity("discord")` (tokenEnvKey, handlesOwnedSpaces). **handlesOwnedSpaces** is connector-scoped ownership for Discord lifecycle channels (single-owner precedence), not a generic bot-wide property. **ConnectorContext** is generic (EventBus, OutboundDeliveryRouter only; no Discord-named fields). Core uses generic **ReplySender** and **OutboundGateway**; **Discord implements both** (**DiscordReplySender** extends ReplySender, **DiscordGateway** extends OutboundGateway). Discord-specific options in **DiscordConnectorConfig** (e.g. defaultTokenEnvKey) passed to **DiscordConnectorAdapter** constructor. Adapter implements **ConnectorAdapter**; `registerBots()` does per-bot gateway/sender/default registration only; **action and sink registration stay in Bootstrap**. `DiscordEventSource` implements `EventSource` and `DiscordReplySender`; JDA-backed gateway; OutboundDeliveryRouter resolves sender from delivery target and lifecycle context; exposes **getSelfUserIdForBot(botId)** for workflow actions; no silent fallback for lifecycle channels. **DiscordAppReplySink** casts to **DiscordGateway** for interaction-specific methods (e.g. defer, update). Intents render to Discord with fallback to text. Assets: ConnectorContext, ConnectorAdapter, ConnectorRegistry, ReplySender, OutboundGateway, DiscordConnectorConfig, DiscordConnectorAdapter, DiscordEventSource, DiscordReplySender, DiscordGateway, JdaDiscordGateway, DiscordAppReplySink, OutboundDeliveryRouter.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-REPLY-SENDER | Generic contract for sending outbound replies; connector senders implement for engine and workflow actions | src/main/java/com/vinekeepers/connectors/ReplySender.java |
| ASSET-OUTBOUND-GATEWAY | Generic gateway for outbound delivery (send, getSelfUserId, createTextChannel, createThreadChannel, addPermissionOverride) | src/main/java/com/vinekeepers/connectors/OutboundGateway.java |
| ASSET-CONNECTOR-CONTEXT | Generic context (EventBus, OutboundDeliveryRouter); no Discord-named fields | src/main/java/com/vinekeepers/connectors/ConnectorContext.java |
| ASSET-CONNECTOR-ADAPTER | registerBots(bots, context); per-bot gateway/sender/default registration only | src/main/java/com/vinekeepers/connectors/ConnectorAdapter.java |
| ASSET-CONNECTOR-REGISTRY | register(connectorId, adapter), get(connectorId) | src/main/java/com/vinekeepers/connectors/ConnectorRegistry.java |
| ASSET-DISCORD-CONNECTOR-CONFIG | Discord-specific config (defaultTokenEnvKey) for adapter constructor | src/main/java/com/vinekeepers/connectors/DiscordConnectorConfig.java |
| ASSET-DISCORD-CONNECTOR-ADAPTER | Implements ConnectorAdapter; per-bot Discord registration; Bootstrap keeps sink/action wiring | src/main/java/com/vinekeepers/connectors/DiscordConnectorAdapter.java |
| ASSET-DISCORD-SOURCE | Discord event source and mention metadata bridge | src/main/java/com/vinekeepers/connectors/DiscordEventSource.java |
| ASSET-DISCORD-REPLY | Extends ReplySender; Discord reply sender contract | src/main/java/com/vinekeepers/connectors/DiscordReplySender.java |
| ASSET-DISCORD-GATEWAY-CONTRACT | Extends OutboundGateway; Discord gateway contract for receive/send and interaction operations | src/main/java/com/vinekeepers/connectors/DiscordGateway.java |
| ASSET-DISCORD-GATEWAY | JDA-backed Discord gateway; getSelfUserId and addPermissionOverride for lifecycle room; optional outbound-only mode for non-routed bots | src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java |
| ASSET-DISCORD-REPLY-SINK | AppReplySink implementation; uses OutboundDeliveryRouter for sender resolution; casts to DiscordGateway for interaction methods; lifecycle operations; intent rendering; fallback to text | src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java |
| ASSET-OUTBOUND-DELIVERY-ROUTER | Implements ReplySender; routes outbound delivery; resolves sender from delivery target and lifecycle context; getGatewayForChannel; getSelfUserIdForBot; no silent fallback for lifecycle | src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java |

# Sub-pages

- [How it works](discord/how-it-works.md)
- [Change log](discord/change-log.md)
- [Known issues](discord/known-issues.md)
- [Decisions](discord/decisions.md)
- [Contracts](discord/contracts.md)
- [Tests](discord/tests.md)
- [Diagrams](discord/diagrams.md)

