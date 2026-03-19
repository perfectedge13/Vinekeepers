# State

# Features

| Feature | Status | Link |
|---------|--------|------|
| State store for workflow state | active | [State store for workflow state](state/state.md) |
| Feature room state (multi-bot) | active | — |

**Feature room state:** Workflow state key **featureRoomParticipants** (List&lt;Map&gt;; keys: role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator) is produced by **provision_room_participants** and consumed by **initialize_feature_room_state**, which builds **FeatureRoomState** and stores it in **FeatureRoomStateStore** (by channel and delivery target). Used by Router for multi-bot participant routing and by OutboundDeliveryRouter for sendAs/sendAsRole.

