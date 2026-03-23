# State

# Features

| Feature | Status | Link |
|---------|--------|------|
| State store for workflow state | active | [State store for workflow state](state/state.md) |
| Feature room state (multi-bot) | active | — |

**Feature room state:** Transient workflow key **featureRoomParticipants** (List&lt;Map&gt;; handoff from **provision_room_participants** to **initialize_feature_room_state**) is validated and converted to typed **RoomParticipant** entries in **FeatureRoomState** and **FeatureRoomStateStore** (by channel and delivery target). Router uses coordinator-only routing for both the room channel and the intake/spec thread (messages and interactions), when the coordinator resolves. OutboundDeliveryRouter uses **sendAsExplicit** / **sendAsRoleExplicit** for explicit workflow senders.

