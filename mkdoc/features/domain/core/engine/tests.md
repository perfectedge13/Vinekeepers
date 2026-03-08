# Tests

# Coverage

Unit tests cover event routing through the engine plus reasoner integration for state patches, proposed tools, and reply selection.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ENGINE | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | â€” | Verify engine receives events and routes them to bots |
| UNIT-ENGINE-REASONER-INTEGRATION | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | reasonerAppliesStatePatchRunsToolAndRepliesWhenWorkflowIsSilent | Verify reasoner state patches, tool proposals, and reply selection are applied by the engine |

