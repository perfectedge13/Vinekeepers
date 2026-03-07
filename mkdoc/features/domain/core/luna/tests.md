# Tests

# Coverage

Unit tests cover GatheringState, CursorCloudGatheringWorkflow, CursorCloudAdapterImpl, and engine integration (workflow reply to Discord). Stub adapter used in workflow tests.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-LUNA-STATE | GatheringStateTest | com.vinekeepers.workflow.GatheringStateTest | — | Verify Luna conversation state (project, code change, step) |
| UNIT-LUNA-WORKFLOW | CursorCloudGatheringWorkflowTest | com.vinekeepers.workflow.CursorCloudGatheringWorkflowTest | — | Verify Luna multi-turn workflow and Cursor adapter invocation |
| UNIT-CURSOR-ADAPTER | CursorCloudAdapterImplTest | com.vinekeepers.core.cursor.CursorCloudAdapterImplTest | — | Verify Cursor Cloud API adapter implementation |
| (engine) | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | lunaWorkflowWithReplySenderSendsReplyToDiscord | Engine sends workflow reply via DiscordReplySender when source is Discord |
