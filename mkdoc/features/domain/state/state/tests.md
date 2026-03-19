# Tests

# Coverage

StateStoreTest verifies state load/save and key scoping. FeatureRoomStateStoreTest verifies FeatureRoomStateStore put, getByContextId, getByRoomChannelId, getByDeliveryTargetId.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-STATE-STORE | StateStoreTest | com.vinekeepers.state.StateStoreTest | (various) | Verify state load/save and key scoping |
| UNIT-FEATURE-ROOM-STATE-STORE | FeatureRoomStateStoreTest | com.vinekeepers.state.planning.FeatureRoomStateStoreTest | (various) | Verify FeatureRoomStateStore put, getByContextId, getByRoomChannelId, getByDeliveryTargetId |
| UNIT-FEATURE-ROOM-STATE-STORE-PARTICIPANT-ORDER | FeatureRoomStateStoreTest | com.vinekeepers.state.planning.FeatureRoomStateStoreTest | getParticipantBotIds_returnsStableOrderFromParticipantList | Verify getParticipantBotIds returns participant bot ids in stable role order (Orchestrator, Architect, Auditor, Scribe) |

