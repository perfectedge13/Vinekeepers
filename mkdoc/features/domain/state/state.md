# State store for workflow state

# Status

active

# Summary

State store for bot workflow state (REQ-STATE-001). StateStore persists and loads per-bot, per-conversation workflow state (structured state objects). **Generic workflow ledger DTOs** in `com.vinekeepers.state.workflow` (**UnresolvedItem**, **UnresolvedItemStatus**) support config-driven clarification ledgers used by workflow engine features (REQ-WORKFLOW-002). **Feature room state:** PlanningRole (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE), RoomParticipant (role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator), FeatureRoomState (contextId, featureId, featureSlug, roomChannelId, intakeThreadId, participants, status), and FeatureRoomStateStore (put, getByContextId, getByRoomChannelId, getByDeliveryTargetId; **getParticipantBotIds** returns participant configuredBotIds in PlanningRole order—Orchestrator, Architect, Auditor, Scribe) support multi-bot feature room routing and delivery. **InitializeFeatureRoomStateAction** generates featureId (feat- + 12 hex) and featureSlug (from initialRequest/repo sanitized) when missing; preserves when supplied. **Feature plan state:** `FeaturePlanState` / `FeaturePlanStateStore` hold canonical planning sections and workspace linkage fields (`repoWorkspaceId`, `repoWorkspaceStatus`, `repoLocalPath`, `repoAccessNotes`). **Repo workspace:** `RepoWorkspaceState` / `RepoWorkspaceStateStore`, `DefaultRepoRefResolver`, and `RepoWorkspaceService` resolve repo input to a local path (existing git checkout or optional shallow clone when `VINEKEEPERS_ALLOW_GIT_CLONE=true`); `EnsureRepoWorkspaceAction` persists workspace state and updates plan linkage. **Structured discovery:** `DiscoveryGap`, `DiscoveryQuestion`, `DiscoveryAgenda`, and `DiscoveryFinding` (planning package) are JSON-friendly models used by Luna Phase B and `StructuredDiscoverySupport`.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-STATE-STORE | Persist and load bot workflow state | src/main/java/com/vinekeepers/state/StateStore.java |
| ASSET-PLANNING-ROLE | Enum for feature room planning roles (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE) | src/main/java/com/vinekeepers/state/planning/PlanningRole.java |
| ASSET-ROOM-PARTICIPANT | Model for a participant in a feature room (role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator) | src/main/java/com/vinekeepers/state/planning/RoomParticipant.java |
| ASSET-FEATURE-ROOM-STATE | State model for a feature room (contextId, featureId, featureSlug, roomChannelId, intakeThreadId, participants, status) | src/main/java/com/vinekeepers/state/planning/FeatureRoomState.java |
| ASSET-FEATURE-ROOM-STATE-STORE | Store and resolve feature room state by contextId, roomChannelId, intakeThreadId, featureId, or deliveryTargetId (thread then room); **getParticipantBotIds** / **resolveCoordinatorConfiguredBotId** support Router and OutboundDeliveryRouter | src/main/java/com/vinekeepers/state/planning/FeatureRoomStateStore.java |
| ASSET-FEATURE-PLAN-STATE | Canonical structured planning state for a feature room (legacy sections, workspace linkage, profileId, artifacts map) | src/main/java/com/vinekeepers/state/planning/FeaturePlanState.java |
| ASSET-WORK-PROFILE-REGISTRY | Resolve work profile definitions by id | src/main/java/com/vinekeepers/profile/WorkProfileRegistry.java |
| ASSET-ARTIFACT-STATE-FACTORY | Empty artifact trees from a profile for new plans | src/main/java/com/vinekeepers/profile/ArtifactStateFactory.java |
| ASSET-BIND-PLACEHOLDER-RESOLVER | Nested `{{key}}` resolution in artifact upsert data | src/main/java/com/vinekeepers/profile/BindPlaceholderResolver.java |
| ASSET-FEATURE-PLAN-STATE-STORE | In-memory FeaturePlanState indexed by contextId, featureId, roomChannelId, intakeThreadId | src/main/java/com/vinekeepers/state/planning/FeaturePlanStateStore.java |
| ASSET-UNRESOLVED-ITEM | Generic unresolved-item DTO for workflow clarification ledger | src/main/java/com/vinekeepers/state/workflow/UnresolvedItem.java |
| ASSET-UNRESOLVED-ITEM-STATUS | Status enum for unresolved workflow items | src/main/java/com/vinekeepers/state/workflow/UnresolvedItemStatus.java |
| ASSET-REPO-WORKSPACE-STATE | Repo workspace resolution/materialization record per lifecycle context | src/main/java/com/vinekeepers/state/repo/RepoWorkspaceState.java |
| ASSET-REPO-WORKSPACE-STATE-STORE | In-memory RepoWorkspaceState store | src/main/java/com/vinekeepers/state/repo/RepoWorkspaceStateStore.java |
| ASSET-REPO-WORKSPACE-SERVICE | Resolve local git path or clone under workspace root (env-toggled) | src/main/java/com/vinekeepers/state/repo/RepoWorkspaceService.java |
| ASSET-DISCOVERY-GAP | Typed gap (id, kind, artifact/section/field, reason, severity) for structured discovery JSON | src/main/java/com/vinekeepers/state/planning/DiscoveryGap.java |
| ASSET-DISCOVERY-QUESTION | Discovery prompt with optional REQUIRED_FIELD apply target | src/main/java/com/vinekeepers/state/planning/DiscoveryQuestion.java |
| ASSET-DISCOVERY-AGENDA | Ordered questions and open-gap summary for workflow snapshots | src/main/java/com/vinekeepers/state/planning/DiscoveryAgenda.java |
| ASSET-DISCOVERY-FINDING | Captured answer metadata for discovery logs (optional persistence) | src/main/java/com/vinekeepers/state/planning/DiscoveryFinding.java |

# Sub-pages

- [How it works](state/how-it-works.md)
- [Change log](state/change-log.md)
- [Known issues](state/known-issues.md)
- [Decisions](state/decisions.md)
- [Contracts](state/contracts.md)
- [Tests](state/tests.md)
- [Diagrams](state/diagrams.md)

