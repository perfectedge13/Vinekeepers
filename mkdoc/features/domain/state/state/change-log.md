# Change log

# Entries

## 2026-03-18

- **Work profiles on FeaturePlanState:** **profileId** and **artifacts** map (`ArtifactState` per artifact id); **WorkProfileRegistry**, **ArtifactStateFactory**, **BindPlaceholderResolver** under `com.vinekeepers.profile`. **InitializeFeaturePlanStateAction** sets default profile and empty artifacts from registry. Specs: state-registry; tests: ArtifactStateFactoryTest, BindPlaceholderResolverTest, FeaturePlanStateStoreTest updates.
- **Step 1 follow-up (participant order, featureSlug):** **FeatureRoomState** extended with **featureSlug**; **FeatureRoomStateStore.getParticipantBotIds** returns participant configuredBotIds in **PlanningRole.ordinal()** order (Orchestrator, Architect, Auditor, Scribe). Tests: FeatureRoomStateStoreTest getParticipantBotIds_whenParticipantsInWrongOrder_returnsRoleOrderOrchestratorArchitectAuditorScribe. Docs: state summary, contracts.
- **Multi-bot feature room state:** Added **PlanningRole** (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE), **RoomParticipant** (role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator), **FeatureRoomState** (contextId, featureId, roomChannelId, intakeThreadId, participants, status), and **FeatureRoomStateStore** (put, getByContextId, getByRoomChannelId, getByDeliveryTargetId). Used by Router for multi-bot participant routing and by OutboundDeliveryRouter for sendAs/sendAsRole. Tests: FeatureRoomStateStoreTest.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

