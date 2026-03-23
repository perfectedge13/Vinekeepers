# Change log

# Entries

## 2026-03-23

- **Planner stabilization (failure + intake binding):** **`PlanningFailureCategory`**, **`PlanningIntakeBindingResolver`**, and **`FeaturePlanState.withPlannerRecoveryFields`** document typed synthesis/orchestration failures and coordinator-exclusive intake routing data used by **`hydrate_planning_session`**, **`RunLlmPlanningSynthesisAction`**, **`PlanningPostDraftGovernor`**, Router, engine, and Discord ingress. Specs: **state-registry**, **workflow-registry**, **bot-registry**, **connectors-registry**; tests: **PlanningFailureCategoryTest**, **PlanningIntakeBindingResolverTest**, and related workflow tests.
- **Plan confidence (known vs unknown):** **`PlanConfidence`** documents and persists **structuredKnownFactCount**, **materialUnknownCount**, and **materialUnknownLabels** so critique/readiness and approval gating can reflect how much of the plan is grounded vs still unknown. **`FeaturePlanState`** / **`REQ-STATE-001`** wording aligned in **state-registry** with workflow **PlanReadinessCalculator** / **PlanningApprovalGateSupport** (launch bar **0.85**). Docs: summary, how-it-works, contracts.

## 2026-03-22

- **Canonical planning projections and readiness seam:** **`FeaturePlanState`** remains the durable source for packet timing (**`packetPostedAt`**, fingerprint/chunk fields) and **`PlanConfidence`**; **`PlanReadinessEvaluator`** treats “packet posted” as authoritative from plan state when workflow spread omits it. **`hydrate_planning_session`** projects **`planningPacketPosted`**, **`planningPacketPostedVersion`**, and **`planReadinessStatus`** from persisted plan when present; **`evaluate_planning_approval_gate`** prefers stored **`PlanConfidence`** for the approval-ready check. Specs: **state-registry**, **workflow-registry**; tests: **PlanReadinessEvaluatorTest**, **HydratePlanningSessionActionTest**, **SpreadPlanWorkspaceSignalsActionTest**.

## 2026-03-19 (Phase C state)

- **Planning gate models:** **PlanCritiqueFinding**, **PlanCritiqueSnapshot**, **PlanReadinessStatus**, **PlanApprovalStatus**; **PlanConfidence** extended with **readinessStatus** and **computedAt**; **FeaturePlanState** carries optional critique snapshot and **withPlanConfidence** / **withPlanApproval** / **withPlanCritiqueSnapshot**.

## 2026-03-19

- **Structured discovery models (mk sync):** Summary, **key assets**, **contracts**, and **how-it-works** updated for **DiscoveryGap**, **DiscoveryQuestion**, **DiscoveryAgenda**, **DiscoveryFinding** (REQ-STATE-001).

## 2026-03-18

- **Work profiles on FeaturePlanState:** **profileId** and **artifacts** map (`ArtifactState` per artifact id); **WorkProfileRegistry**, **ArtifactStateFactory**, **BindPlaceholderResolver** under `com.vinekeepers.profile`. **InitializeFeaturePlanStateAction** sets default profile and empty artifacts from registry. Specs: state-registry; tests: ArtifactStateFactoryTest, BindPlaceholderResolverTest, FeaturePlanStateStoreTest updates.
- **Step 1 follow-up (participant order, featureSlug):** **FeatureRoomState** extended with **featureSlug**; **FeatureRoomStateStore.getParticipantBotIds** returns participant configuredBotIds in **PlanningRole.ordinal()** order (Orchestrator, Architect, Auditor, Scribe). Tests: FeatureRoomStateStoreTest getParticipantBotIds_whenParticipantsInWrongOrder_returnsRoleOrderOrchestratorArchitectAuditorScribe. Docs: state summary, contracts.
- **Multi-bot feature room state:** Added **PlanningRole** (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE), **RoomParticipant** (role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator), **FeatureRoomState** (contextId, featureId, roomChannelId, intakeThreadId, participants, status), and **FeatureRoomStateStore** (put, getByContextId, getByRoomChannelId, getByDeliveryTargetId). Used by Router for multi-bot participant routing and by OutboundDeliveryRouter for sendAs/sendAsRole. Tests: FeatureRoomStateStoreTest.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

