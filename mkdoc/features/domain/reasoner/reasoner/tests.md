# Tests

# Coverage

Unit coverage verifies engine and reasoner integration for reply selection, state patches, and proposed tool execution. Manual coverage verifies the reasoner contract still compiles into the core loop.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ENGINE-REASONER-INTEGRATION | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | reasonerAppliesStatePatchRunsToolAndRepliesWhenWorkflowIsSilent | Verify engine applies reasoner state patches, runs proposed tools, and uses reply text when workflow is silent |
| MANUAL-REASONER | Reasoner interface | — | — | Verify reasoner used in core loop |

