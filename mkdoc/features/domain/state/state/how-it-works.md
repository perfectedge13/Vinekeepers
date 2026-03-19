# How it works

# Overview

StateStore.load(botId, conversationKey) returns state or empty. StateStore.save persists state for that bot and conversation. Engine loads state before reasoner, saves after applying state patch. **Feature room planning:** **FeatureRoomStateStore** holds **FeatureRoomState** entries indexed by contextId, room channel id, intake/spec thread id, and feature id. **getByDeliveryTargetId** resolves thread-first then room. **getParticipantBotIds** sorts participants by **PlanningRole** and returns non-blank configured bot ids for intake-thread routing. **resolveCoordinatorConfiguredBotId** picks the marked primary coordinator, else the ORCHESTRATOR participant, for low-noise room-channel routing. **Feature plan and discovery:** **FeaturePlanState** holds profile-linked **artifacts** and planning fields; **DiscoveryGap** / **DiscoveryAgenda** / **DiscoveryQuestion** / **DiscoveryFinding** support Luna Phase B gap scans and YAML-driven discovery loops.

# Flow

1. Engine gets botId and conversation key from event.
2. Load state from StateStore.
3. After reasoner and actions, save updated state.
4. Workflow actions such as **initialize_feature_room_state** call **FeatureRoomStateStore.put** so Router and **OutboundDeliveryRouter** can resolve feature-room routing and role-based sends.

# Inputs and outputs

- **Inputs:** botId, conversationKey, state object. **Outputs:** Loaded state; persistence.

