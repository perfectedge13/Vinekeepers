# Change log

# Entries

## 2026-03-23

- **Bootstrap wiring for planning stabilization:** **Bootstrap** passes **FeaturePlanStateStore** into **Router**, **ConnectorContext**, and related planning registrations so **`PlanningIntakeBindingResolver`**, Discord **OWNED_SPACES** predicates, and coordinator kickoff paths share the same stores; **`handlesOwnedSpaces`** map wiring unchanged. Specs: **core-registry**, **connectors-registry**, **workflow-registry**.

## 2026-03-22

- **Optional Qdrant planning RAG wiring:** **Bootstrap** registers **prep_planning_repo_grounding** (**PrepPlanningRepoGroundingAction**) with **OpenAiChatClient** and plan/workspace stores so ingress YAML can run Parquet-backed embedding cache and Qdrant retrieval after **ensure_repo_workspace** without failing the workflow when RAG is disabled or misconfigured. Specs: **core-registry**, **workflow-registry**; implementation under **workflow/planning/rag**.

## 2026-03-18

- **Bootstrap: FeatureRoomStateStore and multi-bot feature room actions:** Bootstrap creates **FeatureRoomStateStore** and passes it to **Router** and **OutboundDeliveryRouter** so multi-bot feature room routing and sendAsRole/sendAs work. Bootstrap registers workflow actions **provision_room_participants** (ProvisionRoomParticipantsAction) and **initialize_feature_room_state** (InitializeFeatureRoomStateAction). No change to engine or config wiring; action registration stays in Bootstrap.

## 2026-03-13

- **Bootstrap registers Discord SpaceOperations capability:** Bootstrap now registers the Discord **SpaceOperations** implementation with **SpaceOperationsRegistry** (e.g. register `"discord"`, DiscordSpaceOperations) so workflow actions create_channel and create_thread can resolve connector-specific space ops by event source prefix. No change to engine or config wiring; capability registration stays in Bootstrap.
