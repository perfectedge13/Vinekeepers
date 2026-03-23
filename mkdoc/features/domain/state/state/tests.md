# Tests

# Coverage

StateStoreTest verifies state load/save and key scoping. FeatureRoomStateStoreTest verifies FeatureRoomStateStore put, getByContextId, getByRoomChannelId, getByDeliveryTargetId. PlanningFailureCategoryTest covers enum parse/wireName and unknown inputs. PlanningIntakeBindingResolverTest covers intake binding from room and plan stores (including plan-only active intake).

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-STATE-STORE | StateStoreTest | com.vinekeepers.state.StateStoreTest | (various) | Verify state load/save and key scoping |
| UNIT-FEATURE-ROOM-STATE-STORE | FeatureRoomStateStoreTest | com.vinekeepers.state.planning.FeatureRoomStateStoreTest | (various) | Verify FeatureRoomStateStore put, getByContextId, getByRoomChannelId, getByDeliveryTargetId |
| UNIT-FEATURE-ROOM-STATE-STORE-PARTICIPANT-ORDER | FeatureRoomStateStoreTest | com.vinekeepers.state.planning.FeatureRoomStateStoreTest | getParticipantBotIds_returnsStableOrderFromParticipantList | Verify getParticipantBotIds returns participant bot ids in stable role order (Orchestrator, Architect, Auditor, Scribe) |
| UNIT-PLANNING-FAILURE-CATEGORY | PlanningFailureCategoryTest | com.vinekeepers.state.planning.PlanningFailureCategoryTest | (various) | Verify PlanningFailureCategory parse, wireName, and unknown input handling |
| UNIT-PLANNING-INTAKE-BINDING-RESOLVER | PlanningIntakeBindingResolverTest | com.vinekeepers.state.planning.PlanningIntakeBindingResolverTest | (various) | Verify intake binding resolution from room and plan stores including plan-only active intake paths |

