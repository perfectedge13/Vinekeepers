# Tests

# Coverage

Unit coverage verifies connector lifecycle behavior through the gateway seam, mention metadata propagation, and engine-delivered Discord replies. Manual coverage keeps the compile-level connector contract traceable.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ENGINE-DISCORD-REPLY | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | lunaWorkflowWithReplySenderSendsReplyToDiscord | Verify workflow replies are sent back through the Discord reply path |
| UNIT-DISCORD-APP-REPLY-SINK | DiscordAppReplySinkTest | com.vinekeepers.connectors.DiscordAppReplySinkTest | — | Verify DiscordAppReplySink implements AppReplySink lifecycle, capabilities, ChannelTarget send with components, and intent rendering with fallback to text |
| UNIT-DISCORD-EVENT-SOURCE | DiscordEventSourceTest | com.vinekeepers.connectors.DiscordEventSourceTest | — | Verify Discord event source and reply sender behavior |
| UNIT-DISCORD-EVENT-SOURCE-MENTIONS | DiscordEventSourceTest | com.vinekeepers.connectors.DiscordEventSourceTest | startPublishesStubMessageWithMentionsMetadata | Verify `DiscordEventSource` publishes mention metadata for downstream routing |
| UNIT-OUTBOUND-DELIVERY-ROUTER | OutboundDeliveryRouterTest | com.vinekeepers.connectors.OutboundDeliveryRouterTest | — | Verify lifecycle context to configuredBotId sender resolution, default sender when no context, and no fallback when lifecycle channel has no sender for that bot |
| UNIT-DISCORD-REPLY-TARGET-RESOLVER | DiscordReplyTargetResolverTest | com.vinekeepers.connectors.DiscordReplyTargetResolverTest | — | Verify DiscordReplyTargetResolver resolves Event to ChannelTarget or InteractionTarget from payload (channelId, messageId, interactionId, token, deferred); message vs interaction kind; sourceId passed through |
| UNIT-DISCORD-SPACE-OPERATIONS | DiscordSpaceOperationsTest | com.vinekeepers.connectors.DiscordSpaceOperationsTest | — | Verify DiscordSpaceOperations createRoom (guildId, name building, permission override) and createThread (channelId, threadName, contextId, setDeliveryTargetId), normalizeChannelName |
| — | CreateRoomRequestTest | com.vinekeepers.connectors.CreateRoomRequestTest | — | Verify CreateRoomRequest.from(Event, state, bind) bind-then-state resolution and record fields |
| — | CreateThreadRequestTest | com.vinekeepers.connectors.CreateThreadRequestTest | — | Verify CreateThreadRequest.from(Event, state, bind) bind-then-state resolution and record fields |
| UNIT-SPACE-OPERATIONS-REGISTRY | SpaceOperationsRegistryTest | com.vinekeepers.connectors.SpaceOperationsRegistryTest | — | Verify SpaceOperationsRegistry register(connectorId, ops) and get(connectorId) |
| MANUAL-CONNECTORS-DISCORD | Discord connector compiles and implements interface | — | — | Verify DiscordEventSource and DiscordReplySender exist and compile |

