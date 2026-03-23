# Contracts

# APIs

None. StateStore load/save.

# Schemas

State: JSON-shaped per workflow (e.g. TicketDraft, ReviewDraft). Key: (botId, conversationKey).

# Interfaces

- **StateStore:** load(botId, key), save(botId, key, state).
- **PlanningRole:** enum ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE.
- **RoomParticipant:** role (PlanningRole), configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator.
- **FeatureRoomState:** contextId, featureId, featureSlug, roomChannelId, intakeThreadId, participants (List&lt;RoomParticipant&gt;), status.
- **FeatureRoomStateStore:** put(state), getByContextId(contextId), getByRoomChannelId(roomChannelId), getByDeliveryTargetId(deliveryTargetId). **getParticipantBotIds(state)** returns participant configuredBotIds in **PlanningRole.ordinal()** order (Orchestrator, Architect, Auditor, Scribe).
- **DiscoveryGap:** id, kind, artifact/section/field identifiers, reason, severity (structured discovery).
- **DiscoveryQuestion:** prompt text and optional apply target when kind is REQUIRED_FIELD.
- **DiscoveryAgenda:** ordered questions plus summary fields for workflow state snapshots.
- **DiscoveryFinding:** optional metadata for a captured answer (future persistence).
- **PlanConfidence:** readiness status, qualitative level, notes, **confidenceScore** (0–1), **confidenceReasons** (including summaries tied to known-vs-unknown signals), **structuredKnownFactCount**, **materialUnknownCount**, **materialUnknownLabels**, **computedAt**.

