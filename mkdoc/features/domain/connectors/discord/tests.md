# Tests

# Coverage

Unit coverage verifies connector lifecycle behavior through the gateway seam, mention metadata propagation, and engine-delivered Discord replies. Manual coverage keeps the compile-level connector contract traceable.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ENGINE-DISCORD-REPLY | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | lunaWorkflowWithReplySenderSendsReplyToDiscord | Verify workflow replies are sent back through the Discord reply path |
| UNIT-DISCORD-EVENT-SOURCE | DiscordEventSourceTest | com.vinekeepers.connectors.DiscordEventSourceTest | — | Verify Discord event source and reply sender behavior |
| UNIT-DISCORD-EVENT-SOURCE-MENTIONS | DiscordEventSourceTest | com.vinekeepers.connectors.DiscordEventSourceTest | startPublishesGatewayMessageWithMentionsMetadata | Verify `DiscordEventSource` publishes mention metadata for downstream routing |
| MANUAL-CONNECTORS-DISCORD | Discord connector compiles and implements interface | — | — | Verify DiscordEventSource and DiscordReplySender exist and compile |

