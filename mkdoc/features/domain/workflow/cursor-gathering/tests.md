# Tests

# Coverage

Unit tests cover Luna gathering state, YAML-configured mention routing, the legacy workflow test harness, the Cursor adapter implementation, the launch tool, and the cloud-run monitor.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-LUNA-STATE | GatheringStateTest | com.vinekeepers.workflow.GatheringStateTest | — | Verify Luna conversation state for project, code change, and step tracking |
| UNIT-LUNA-MENTION-ROUTING | ConfigLoaderTest | com.vinekeepers.config.ConfigLoaderTest | buildRouterParsesDiscordMentionRouting | Verify Luna config uses `discordMention` routing that activates on `@Luna` messages |
| UNIT-LUNA-WORKFLOW | CursorCloudGatheringWorkflowTest | com.vinekeepers.workflow.CursorCloudGatheringWorkflowTest | — | Verify multi-turn gathering workflow and Cursor adapter invocation |
| UNIT-CURSOR-ADAPTER | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | — | Verify Cursor Cloud API adapter implementation |
| UNIT-CURSOR-FULL-RUN-TOOL | CursorFullRunToolTest | com.vinekeepers.tools.CursorFullRunToolTest | — | Verify `cursor.fullRun` launches a Cursor run and stores run state |
| UNIT-CURSOR-RUN-MONITOR | CursorCloudRunMonitorTest | com.vinekeepers.core.cursor.CursorCloudRunMonitorTest | — | Verify Cursor feedback and terminal updates are reported back to Discord |

