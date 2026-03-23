# Specs governance and bootstrap

# Status

active

# Summary

Spec metadata anchors traceability, and VinekeepersApp plus Bootstrap wire the runtime before events begin flowing.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-SPEC-INDEX | Spec index and validation entrypoint | specs/specs.yml |
| ASSET-REGISTRY | Core requirements registry | specs/core-registry.yml |
| ASSET-APP | Application entrypoint; loads .env and bootstraps engine | src/main/java/com/vinekeepers/VinekeepersApp.java |
| ASSET-BOOTSTRAP | Wire engine, config, ConnectorRegistry; build Discord adapter; registerBots; create FeatureRoomStateStore, FeaturePlanStateStore, RepoWorkspaceStateStore, RepoWorkspaceService and pass room store to Router and OutboundDeliveryRouter; register engine reply sender, sink, ReplyTargetResolver, and Discord SpaceOperations by connector id; register workflow actions including provision_room_participants, initialize_feature_room_state, initialize_feature_plan_state, hydrate_planning_session, ensure_repo_workspace, prep_planning_repo_grounding (PrepPlanningRepoGroundingAction with OpenAI + optional Qdrant-backed PlanningRepoGroundingService), spread_plan_workspace_signals, structured discovery and plan critique/approval actions; action and sink registration | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-PACKAGE-MARKER | Package marker utility | src/main/java/com/vinekeepers/util/PackageMarker.java |

# Sub-pages

- [How it works](core/how-it-works.md)
- [Change log](core/change-log.md)
- [Known issues](core/known-issues.md)
- [Decisions](core/decisions.md)
- [Contracts](core/contracts.md)
- [Tests](core/tests.md)
- [Diagrams](core/diagrams.md)
