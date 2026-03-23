# Discord event source and reply

# Status

active

# Summary

Discord event source (REQ-CONNECTORS-DISCORD-001). Per-bot identity via `BotDefinition.getConnectorIdentity("discord")` (tokenEnvKey, handlesOwnedSpaces). **handlesOwnedSpaces** is connector-scoped ownership for Discord lifecycle channels (single-owner precedence), not a generic bot-wide property. **ConnectorContext** is generic (EventBus, OutboundDeliveryRouter only; no Discord-named fields). Core uses **ReplySender** and **ReplyTargetResolver** for delivery; **OutboundGateway** is the **connector execution surface** (Discord-shaped API); used only by connector-owned code and **OutboundDeliveryRouter**; gateways are resolved and used via the router. **Discord** implements **ReplyTargetResolver** (**DiscordReplyTargetResolver**) and sender/gateway (**DiscordReplySender** extends ReplySender, **DiscordGateway** extends OutboundGateway). Reply senders and **ReplyTargetResolver** are **registered per connector id**; the engine obtains ReplyTarget only via the connector-owned resolver by connector id (fail closed when no resolver or resolver returns empty); **Bootstrap** registers the Discord sender, the **Discord ReplyTargetResolver**, and the **Discord SpaceOperations** capability (e.g. `engine.registerReplyTargetResolver("discord", ...)`; **SpaceOperationsRegistry** register `"discord"`, **DiscordSpaceOperations**). **OutboundDeliveryRouter** does routing (sender resolution by target and lifecycle context) and gateway resolution; the gateway is the execution surface for channel/thread/permission and send operations. Workflow actions **create_channel** and **create_thread** resolve **SpaceOperations** via **WorkflowCapabilitySupport.sourcePrefix(event)**; null/blank prefix or unregistered prefix returns sentinel (**fail-closed**, no Discord default). **SpaceOperations** returns **typed results** (**CreateRoomResult**, **CreateThreadResult**—success with id or failure with reason); **DiscordSpaceOperations** implements this contract. Workflow actions **create_channel** and **create_thread** translate results to id or sentinel for `run()` (storeIn and branch use id/sentinel). SpaceOperations uses a **request-based API** (**CreateRoomRequest**, **CreateThreadRequest**) with explicit intent fields; a request factory performs bind/state resolution; **DiscordSpaceOperations** uses only request getters. The engine has no connector-specific literals for delivery fallback (fallback uses source prefix; reply-sender fallback uses target.channelId()/messageId()). Discord-specific options in **DiscordConnectorConfig** (e.g. defaultTokenEnvKey) passed to **DiscordConnectorAdapter** constructor. Adapter implements **ConnectorAdapter**; `registerBots()` does per-bot gateway/sender/default registration only; **action and sink registration stay in Bootstrap**. `DiscordEventSource` implements `EventSource` and `DiscordReplySender`; JDA-backed gateway; OutboundDeliveryRouter resolves sender from delivery target and lifecycle context; exposes **getSelfUserIdForBot(botId)** for workflow actions; no silent fallback for lifecycle channels. **DiscordAppReplySink** casts to **DiscordGateway** for interaction-specific methods (e.g. defer, update), and deferred interaction follow-ups stay pinned to the ingesting bot gateway when `ingestBotId` is present so the correct JDA hook handles the reply. Intents render to Discord with fallback to text. Assets: ConnectorContext, ConnectorAdapter, ConnectorRegistry, ReplySender, OutboundGateway, DiscordConnectorConfig, DiscordConnectorAdapter, DiscordEventSource, DiscordReplySender, DiscordGateway, JdaDiscordGateway, DiscordAppReplySink, OutboundDeliveryRouter.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-REPLY-SENDER | Generic contract for sending outbound replies; connector senders implement for engine and workflow actions | src/main/java/com/vinekeepers/connectors/ReplySender.java |
| ASSET-OUTBOUND-GATEWAY | Connector execution surface (Discord-shaped); send, getSelfUserId, createTextChannel, createThreadChannel, addPermissionOverride; used only by connector code and router | src/main/java/com/vinekeepers/connectors/OutboundGateway.java |
| ASSET-CONNECTOR-CONTEXT | Generic context (EventBus, OutboundDeliveryRouter); no Discord-named fields | src/main/java/com/vinekeepers/connectors/ConnectorContext.java |
| ASSET-CONNECTOR-ADAPTER | registerBots(bots, context); per-bot gateway/sender/default registration only | src/main/java/com/vinekeepers/connectors/ConnectorAdapter.java |
| ASSET-CONNECTOR-REGISTRY | register(connectorId, adapter), get(connectorId) | src/main/java/com/vinekeepers/connectors/ConnectorRegistry.java |
| ASSET-DISCORD-CONNECTOR-CONFIG | Discord-specific config (defaultTokenEnvKey) for adapter constructor | src/main/java/com/vinekeepers/connectors/DiscordConnectorConfig.java |
| ASSET-DISCORD-CONNECTOR-ADAPTER | Implements ConnectorAdapter; per-bot Discord registration; Bootstrap keeps sink/action wiring | src/main/java/com/vinekeepers/connectors/DiscordConnectorAdapter.java |
| ASSET-DISCORD-SOURCE | Discord event source and mention metadata bridge | src/main/java/com/vinekeepers/connectors/DiscordEventSource.java |
| ASSET-DISCORD-REPLY | Extends ReplySender; Discord reply sender contract | src/main/java/com/vinekeepers/connectors/DiscordReplySender.java |
| ASSET-DISCORD-GATEWAY-CONTRACT | Extends OutboundGateway; Discord gateway contract for receive/send and interaction operations | src/main/java/com/vinekeepers/connectors/DiscordGateway.java |
| ASSET-DISCORD-GATEWAY | JDA-backed Discord gateway; getSelfUserId and addPermissionOverride for lifecycle room; outbound-only when bot is neither routed nor Discord handlesOwnedSpaces lifecycle owner | src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java |
| ASSET-DISCORD-REPLY-SINK | AppReplySink implementation; uses OutboundDeliveryRouter for sender resolution; casts to DiscordGateway for interaction methods; lifecycle operations; intent rendering; fallback to text | src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java |
| ASSET-OUTBOUND-DELIVERY-ROUTER | Routing + gateway resolution; implements ReplySender; FeatureRoomStateStore for sendAs/sendAsRole and strict sendAsExplicit/sendAsRoleExplicit for workflow; registerSender(botId, ReplySender, OutboundGateway); getGatewayForChannel; getSelfUserIdForBot; used by DiscordSpaceOperations and DiscordAppReplySink; no silent fallback for lifecycle | src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java |
| ASSET-SPACE-OPERATIONS | Interface for connector-specific space operations: createRoom(CreateRoomRequest), createThread(CreateThreadRequest); resolved by source prefix from SpaceOperationsRegistry; fail-closed (no default) | src/main/java/com/vinekeepers/connectors/SpaceOperations.java |
| ASSET-SPACE-OPERATIONS-REGISTRY | Thread-safe registry of SpaceOperations by connector id (source prefix); register(connectorId, ops), get(connectorId) | src/main/java/com/vinekeepers/connectors/SpaceOperationsRegistry.java |
| ASSET-DISCORD-SPACE-OPERATIONS | Implements SpaceOperations for Discord; uses only request getters; createRoom/createThread; when CreateRoomRequest has non-empty participantBotIds, addPermissionOverride for each bot (multi-bot feature room) | src/main/java/com/vinekeepers/connectors/DiscordSpaceOperations.java |
| ASSET-CREATE-ROOM-REQUEST | Request DTO for SpaceOperations.createRoom; explicit intent fields (sourceId, guildId, channelName, lifecycleOwnerBotId, participantBotIds, project, codeChange); from(event, state, bind) with bind precedence; optional participantBotIds for multi-bot feature room permission overrides | src/main/java/com/vinekeepers/connectors/CreateRoomRequest.java |
| ASSET-CREATE-THREAD-REQUEST | Request DTO for SpaceOperations.createThread; explicit intent fields; request factory performs bind/state resolution | src/main/java/com/vinekeepers/connectors/CreateThreadRequest.java |
| ASSET-CREATE-ROOM-RESULT | Typed result of createRoom: success (channel id) or failure (reason enum); actions translate to id or CHANNEL_CREATE_FAILED for state | src/main/java/com/vinekeepers/connectors/CreateRoomResult.java |
| ASSET-CREATE-THREAD-RESULT | Typed result of createThread: success (thread id) or failure (reason enum); actions translate to id or THREAD_CREATE_FAILED for state | src/main/java/com/vinekeepers/connectors/CreateThreadResult.java |
| ASSET-CREATE-ROOM-FAILURE-REASON | Enum of reasons for create-room (create channel) failure from SpaceOperations | src/main/java/com/vinekeepers/connectors/CreateRoomFailureReason.java |
| ASSET-CREATE-THREAD-FAILURE-REASON | Enum of reasons for create-thread failure from SpaceOperations | src/main/java/com/vinekeepers/connectors/CreateThreadFailureReason.java |
| ASSET-REPLY-TARGET-RESOLVER | Interface: resolve(Event) → Optional&lt;ReplyTarget&gt;; connector-owned; engine looks up by connector id | src/main/java/com/vinekeepers/connectors/ReplyTargetResolver.java |
| ASSET-DISCORD-REPLY-TARGET-RESOLVER | Implements ReplyTargetResolver; reads Discord payload (channelId, messageId, interactionId, token, deferred) and returns InteractionTarget or ChannelTarget | src/main/java/com/vinekeepers/connectors/DiscordReplyTargetResolver.java |

# Sub-pages

- [How it works](discord/how-it-works.md)
- [Change log](discord/change-log.md)
- [Known issues](discord/known-issues.md)
- [Decisions](discord/decisions.md)
- [Contracts](discord/contracts.md)
- [Tests](discord/tests.md)
- [Diagrams](discord/diagrams.md)

