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
- **PlanningFailureCategory:** enum values such as **NONE**, **SYNTHESIS_JSON_INVALID**, **SYNTHESIS_REPAIR_EXHAUSTED**, **SYNTHESIS_UPSERT_REJECTED**, **SYNTHESIS_EMPTY_NOOP**, **REPO_GROUNDING_UNAVAILABLE**; **parse** / **wireName** for YAML and persistence.
- **PlanningIntakeBindingResolver:** resolves **Binding** from Discord channel/thread id via **FeatureRoomStateStore**, **FeaturePlanStateStore**, and active **PlanningIntakeStage**; **exclusiveCoordinatorThread** when intake is active and coordinator-only routing applies; static **isActivePlanningIntake** when stage is not **DONE**.
- **PlanningCanonicalDecision:** typed durable canonical decision payload persisted on the plan record: source, canonical next action, canonical stage, interaction state, posting/review/approval allowances, top-gap metadata, and observability ids.
- **PlanningCanonicalNextAction:** canonical routable next actions: **ASK_ONE_QUESTION**, **AUTONOMOUS_REDRAFT**, **POST_PACKET**, **BLOCK**.
- **PlanningInteractionState:** canonical operator interaction posture persisted with the canonical decision, such as waiting for a text reply during clarification.
- **FeaturePlanState:** optional **planningFailureCategory**, **planningFailurePhase**, **planningRecoverableDraftAvailable**, **planningLastRecoveryHint** (planner recovery metadata); persisted **planningCanonicalDecisionJson**, last-posted / last-asked decision ids, and a material-state fingerprint; **withPlannerRecoveryFields** for durable updates.

