# Tests

# Coverage

Unit tests cover Luna gathering state, YAML-configured mention routing, the legacy workflow test harness, the Cursor adapter implementation (including error parsing, auth, and transport), the launch tool, and the cloud-run monitor.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-LUNA-STATE | GatheringStateTest | com.vinekeepers.workflow.GatheringStateTest | — | Verify Luna conversation state for project, code change, and step tracking |
| UNIT-LUNA-MENTION-ROUTING | ConfigLoaderTest | com.vinekeepers.config.ConfigLoaderTest | buildRouterParsesDiscordMentionRouting | Verify Luna config uses discordMention routing that activates on @Luna-style messages |
| UNIT-LUNA-WORKFLOW | CursorCloudGatheringWorkflowTest | com.vinekeepers.workflow.CursorCloudGatheringWorkflowTest | — | Verify Luna multi-turn workflow and Cursor adapter invocation |
| UNIT-CURSOR-ADAPTER | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | — | Verify Cursor Cloud API adapter implementation |
| UNIT-CURSOR-ADAPTER-API-KEY | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | launchAgentWithoutApiKeyFailsFast | Verify launch fails fast when CURSOR_API_KEY is not configured |
| UNIT-CURSOR-ADAPTER-ERROR-NESTED | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | apiErrorsSurfaceCursorMessage | Verify non-2xx with nested error.message/code surfaces in exception |
| UNIT-CURSOR-ADAPTER-ERROR-STRING | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | non2xxErrorAsStringSurfacesInException | Verify non-2xx with error as string surfaces in exception |
| UNIT-CURSOR-ADAPTER-ERROR-TOPLEVEL | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | non2xxTopLevelMessageSurfacesInException | Verify non-2xx with top-level message surfaces in exception |
| UNIT-CURSOR-ADAPTER-ERROR-PLAINTEXT | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | non2xxPlainTextBodySurfacesInException | Verify non-2xx plain text body surfaces in exception |
| UNIT-CURSOR-ADAPTER-ERROR-EMPTY | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | non2xxEmptyBodySurfacesGenericMessage | Verify non-2xx empty body yields generic message with status |
| UNIT-CURSOR-ADAPTER-ERROR-EMPTY-JSON | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | non2xxEmptyJsonObjectSurfacesGenericMessage | Verify non-2xx empty JSON object yields generic message with status |
| UNIT-CURSOR-ADAPTER-ERROR-NONJSON | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | non2xxNonJsonBodySurfacesInException | Verify non-2xx non-JSON body content surfaces in exception |
| UNIT-CURSOR-ADAPTER-TRANSPORT-EXCEPTION | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | transportExceptionSurfacesMessage | Verify transport exceptions surface cause message and are wrapped in CursorCloudException |
| UNIT-CURSOR-ADAPTER-BEARER-GET | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | getAgentSendsConfiguredApiKeyAsBearerTokenToTransport | Verify getAgent sends configured API key as Bearer token to transport |
| UNIT-CURSOR-ADAPTER-BEARER-LAUNCH | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | transportReceivesBearerTokenAsConfiguredKey | Verify launchAgent sends configured API key as Bearer token to transport |
| UNIT-CURSOR-FULL-RUN-TOOL | CursorFullRunToolTest | com.vinekeepers.tools.CursorFullRunToolTest | — | Verify cursor.fullRun launches a Cursor cloud run and stores run state |
| UNIT-CURSOR-RUN-MONITOR | CursorCloudRunMonitorTest | com.vinekeepers.core.cursor.CursorCloudRunMonitorTest | — | Verify CursorCloudRunMonitor reports Cursor status and feedback back to Discord |
| UNIT-LIFECYCLE-RUN-RECORD | LifecycleRunRecordTest | com.vinekeepers.core.cursor.LifecycleRunRecordTest | — | Verify LifecycleRunRecord model and indexing by channelId and externalRunId |
| UNIT-LIFECYCLE-CONTEXT | LifecycleContextTest | com.vinekeepers.state.LifecycleContextTest | — | Verify LifecycleContext model and builder |
| UNIT-LIFECYCLE-CONTEXT-STORE | LifecycleContextStoreTest | com.vinekeepers.state.LifecycleContextStoreTest | — | Verify LifecycleContextStore store and resolve by key |
| UNIT-RUNTIME-BOT-INSTANCE | RuntimeBotInstanceTest | com.vinekeepers.bot.RuntimeBotInstanceTest | — | Verify RuntimeBotInstance model for provisioned bots |
| UNIT-CREATE-CHANNEL-ACTION | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | — | Verify create_channel action uses Discord gateway createTextChannel for lifecycle room |
| UNIT-CREATE-CHANNEL-ACTION-SENTINEL | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | runReturnsChannelCreateFailedWhenCreateTextChannelReturnsNull | Verify create_channel returns CHANNEL_CREATE_FAILED on gateway failure or null |
| UNIT-CREATE-CHANNEL-ACTION-NORMALIZE | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | runNormalizesChannelNameFromBindToLowercaseDiscordSafe | Verify create_channel normalizes channel name to Discord-safe lowercase |
| UNIT-POST-CHANNEL-MESSAGE-ACTION | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | — | Verify post_channel_message action posts to Discord channel |
| UNIT-POST-CHANNEL-MESSAGE-LIFECYCLE-BOT-NAME | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | runInterpolatesLifecycleBotNameFromMergedMapBindWins | Verify post_channel_message interpolates lifecycleBotName from merged map (bind wins) |
| UNIT-PROVISION-BOT-INSTANCE-ACTION | ProvisionBotInstanceActionTest | com.vinekeepers.workflow.actions.ProvisionBotInstanceActionTest | — | Verify provision_bot_instance action provisions runtime bot instance |
| UNIT-CREATE-LIFECYCLE-CONTEXT-ACTION | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | — | Verify create_lifecycle_context action creates and stores lifecycle context |
| UNIT-CREATE-LIFECYCLE-CONTEXT-BIND-WINS | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | runConfiguredBotIdBindPrecedenceBindWinsOverState | Verify create_lifecycle_context bind precedence (bind overrides state for configuredBotId) |
| UNIT-CREATE-THREAD-ACTION | CreateThreadActionTest | com.vinekeepers.workflow.actions.CreateThreadActionTest | — | Verify create_thread delegates to SpaceOperations by source prefix; setDeliveryTargetId on success when contextId present; blank channelId returns THREAD_CREATE_FAILED |
| UNIT-LAUNCH-CURSOR-RUN-ACTION | LaunchCursorRunActionTest | com.vinekeepers.workflow.actions.LaunchCursorRunActionTest | runLaunchesAndStoresRecordAndBindsContext | Verify launch_cursor_run action launches run, registers run record, and acknowledges to Discord with status (e.g. launching) and lifecycle room |
| UNIT-DISCORD-SPACE-OPERATIONS | DiscordSpaceOperationsTest | com.vinekeepers.connectors.DiscordSpaceOperationsTest | — | Verify DiscordSpaceOperations createRoom and createThread (normalizeChannelName, setDeliveryTargetId) |
| UNIT-SPACE-OPERATIONS-REGISTRY | SpaceOperationsRegistryTest | com.vinekeepers.connectors.SpaceOperationsRegistryTest | — | Verify SpaceOperationsRegistry register and get by connector id |

