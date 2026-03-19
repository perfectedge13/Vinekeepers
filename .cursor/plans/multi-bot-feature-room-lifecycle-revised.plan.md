---
name: ""
overview: ""
todos: []
isProject: false
---

# Multi-bot feature-room lifecycle (Step 1) — Revised implementation plan

## 1. Executive Summary

This revision tightens the existing Step 1 multi-bot feature-room plan without changing its core architecture. The architecture remains anchored on `FeatureRoomState`, typed `RoomParticipant` + `PlanningRole`, `FeatureRoomStateStore`, router participant resolution, and `OutboundDeliveryRouter` send-as behavior, with Luna provisioning the room/thread and legacy single-owner behavior preserved.

The key correction is policy clarity: **all four bots (Arrietty, Architect, Auditor, Scribe) communicate visibly in the intake/spec thread**, while room-channel responses remain controlled to prevent noise. The revised plan replaces "silent workflows" with explicit response policy, role-based participant validation, deterministic sender resolution precedence, and sharper acceptance criteria.

## 2. What Changed from the Prior Plan

### Change for issue 2 (response policy should not rely on silent workflows)

- Removed "silent workflows" as the primary response-control mechanism.
- Added explicit policy by surface:
  - **room channel**: coordinator-centric, low-noise
  - **intake/spec thread**: visible structured multi-role collaboration
- Added explicit distinction between:
  - workflow-triggered role posts (allowed, preferred)
  - generic inbound-triggered behavior (constrained to avoid 4x spam)

### Change for issue 3 (`featureRoomParticipants` map handoff brittleness)

- Explicitly documented boundary:
  - `featureRoomParticipants: List<Map<String,Object>>` is a **transient workflow handoff contract only**
  - `InitializeFeatureRoomStateAction` must validate + translate immediately
  - persisted domain model remains typed `RoomParticipant` in `FeatureRoomState`
- Explicit field contract added for map entries.

### Change for issue 4 (size==4 too weak/rigid)

- Replaced size-only validation with **role-contract validation**:
  - exactly one `ORCHESTRATOR`, `ARCHITECT`, `AUDITOR`, `SCRIBE`
  - no duplicate roles
  - required IDs non-blank
- Kept "four participants for Step 1" as an expectation, but validation authority is role-based.
- Strengthened provisioning contract: ORCHESTRATOR must reuse pre-existing Arrietty runtime instance when already provisioned earlier in Luna flow.

### Change for issue 7 (`post_channel_message` sender precedence unclear)

- Added deterministic precedence:
  1. `asBotId`
  2. `asRole`
  3. default sender behavior
- Added explicit failure/fallback rules based on whether sender selection was explicit or implicit.
- Clarified thread-vs-room interaction with sender selection.

### Change for issue 10 (acceptance criteria too diffuse)

- Added crisp, testable acceptance criteria with explicit pass/fail statements for:
  - permissions
  - thread posting capability
  - router resolution behavior
  - controlled noise policy
  - deterministic role sender behavior
  - legacy compatibility

### Change for "all bots communicate in the thread"

- Thread now defined as the intentional visible collaboration surface.
- Architect/Auditor/Scribe are explicitly expected to post in the thread (not silently).
- Collaboration is structured by workflow/action policy to avoid chaotic duplicate replies.

## 3. Revised Response Policy

### Room channel behavior

- **Primary goal:** user-facing room status, checkpoints, and launch/result summaries.
- **Default speaker:** Arrietty (`ORCHESTRATOR`).
- Architect/Auditor/Scribe do not auto-reply to generic room inbound by default.
- Non-coordinator room messages are allowed only when explicitly workflow-triggered (e.g., targeted `post_channel_message` with role/bot sender).

### Intake/spec thread behavior

- **Primary goal:** visible collaborative planning between all four participants.
- All four roles are allowed to communicate here.
- Collaboration is **structured and intentional**, not unbounded fan-out:
  - workflow/action-driven role turns are preferred
  - generic inbound should not trigger automatic four-bot replies

### Coordinator behavior

- Arrietty remains orchestration lead:
  - initiates thread collaboration sequence
  - requests role-specific contributions
  - maintains progress/synthesis checkpoints
  - provides final thread summary unless workflow explicitly delegates

### Role participation behavior

- Architect: posts design/structure proposals in thread.
- Auditor: posts validation/risk/check strategy in thread.
- Scribe: posts artifact consolidation/status in thread.
- These are expected visible contributions, not hidden/silent behavior.

### Workflow-triggered vs generic inbound behavior

- **Workflow-triggered/action-triggered posts:** explicitly allowed and deterministic.
- **Generic inbound (room/thread):** constrained:
  - no default "all 4 respond" behavior
  - coordinator-first handling or explicit mention/target policy for non-coordinator auto-response
- This prevents uncontrolled duplicates while preserving visible multi-bot thread collaboration.

## 4. Revised Participant Handoff Contract

### Transient workflow handoff format (workflow boundary)

- Key: `featureRoomParticipants`
- Type: `List<Map<String,Object>>`
- Required map fields per entry:
  - `role`
  - `configuredBotId`
  - `runtimeBotInstanceId`
  - `displayName`
  - `primaryCoordinator`
- This format is allowed only as action-to-action handoff within workflow state.

### Typed stored domain/state format (persistent domain)

- Canonical persisted model:
  - `FeatureRoomState.participants: List<RoomParticipant>`
- `InitializeFeatureRoomStateAction` responsibilities:
  1. validate transient map contract
  2. enforce role requirements
  3. convert to typed `RoomParticipant`
  4. persist typed `FeatureRoomState`
- Raw `Map` participant data must not be persisted as long-term domain state.

## 5. Revised Validation Contract

Validation in `InitializeFeatureRoomStateAction` must enforce:

1. `featureRoomParticipants` exists and is parseable as list of maps.
2. Role contract:
  - exactly one `ORCHESTRATOR`
  - exactly one `ARCHITECT`
  - exactly one `AUDITOR`
  - exactly one `SCRIBE`
3. No duplicate roles.
4. For each participant:
  - `configuredBotId` is present and non-blank
  - `runtimeBotInstanceId` is present and non-blank
5. `displayName` may default if missing, but role and IDs cannot.
6. On contract failure: return clear action error and **do not** write partial `FeatureRoomState`.

### Step-1 participant cardinality note

- Step 1 still expects four participants, but enforcement is role-based (not only `size == 4`).

### ORCHESTRATOR runtime reuse requirement

- If Arrietty runtime instance already exists from earlier Luna steps (`provision_bot_instance`), participant provisioning must reuse that runtime instance for `ORCHESTRATOR`.
- Creating a second ORCHESTRATOR runtime when reusable one exists is a contract violation.

## 6. Revised Sender Resolution Rules

For `post_channel_message` sender selection:

1. **If `asBotId` is present**
  - Resolve exact sender for that bot ID.
  - If unresolved: action returns explicit error.
  - No fallback to default sender for this case.
2. **Else if `asRole` is present**
  - Resolve `asRole` via `FeatureRoomStateStore` participant mapping.
  - Map role -> participant `configuredBotId` -> sender.
  - If unresolved: action returns explicit error.
  - No fallback to default sender for this case.
3. **Else**
  - Use existing default sender behavior (current OutboundDeliveryRouter/default routing path).

### Target precedence (unchanged) and interaction

- Target selection remains deterministic (e.g., explicit target channel > target mode > legacy fallback).
- Sender precedence is independent of target precedence.
- For role-visible thread collaboration, workflow steps should use explicit `asRole`/`asBotId` so authorship is deterministic.

### Fallback/error rules summary

- **Explicit sender requested (`asBotId`/`asRole`)**: strict mode, fail if unresolved.
- **No explicit sender requested**: default behavior may apply.
- This ensures correctness and testability for role-based speaking.

## 7. Revised Luna Sequence

Preserve existing dependency-safe provisioning and tighten collaboration policy:

1. `create_channel` (with explicit permission overrides for all four participants)
2. `provision_bot_instance` (Arrietty runtime instance)
3. `create_lifecycle_context`
4. `create_thread` (intake/spec thread)
5. `provision_room_participants`
  - emit transient `featureRoomParticipants` handoff
  - reuse existing Arrietty runtime instance for `ORCHESTRATOR`
6. `initialize_feature_room_state`
  - role-contract validation + typed persistence
7. `initialize_feature_plan_state`
8. `ensure_repo_workspace`
9. planning-state/profile actions (existing flow)
10. `get_profile_missing_fields`
11. thread collaboration posts (explicit role speaking):
  - orchestrator kickoff (`asRole: orchestrator`)
  - architect contribution (`asRole: architect`)
  - auditor contribution (`asRole: auditor`)
  - scribe consolidation (`asRole: scribe`)
12. room-channel coordinator summary (Arrietty-only default)
13. continue launch flow / status updates

### Anti-chaos guard in sequence

- Generic inbound is not treated as automatic all-role trigger.
- Multi-role thread speaking is planned/explicit within workflow turns.

## 8. Revised Testing and Acceptance Criteria

### Testing strategy

Add/strengthen tests for:

- **Participant handoff conversion**
  - `InitializeFeatureRoomStateAction` validates map shape and role contract.
- **Role uniqueness and required-role enforcement**
  - duplicate/missing role cases fail with clear errors.
- **Arrietty runtime reuse**
  - provisioning reuses existing ORCHESTRATOR runtime instance.
- **Router resolution parity**
  - room channel and intake thread both resolve to same feature-room participant set.
- **Sender precedence**
  - `asBotId` overrides `asRole`
  - `asRole` resolves correctly through participant mapping
  - unresolved explicit sender errors
  - implicit/default path still works
- **Anti-spam behavior**
  - generic room/thread inbound does not cause uncontrolled four-bot auto-replies.
- **Legacy compatibility**
  - non-feature-room / single-owner lifecycle behavior unchanged.

### Crisp acceptance criteria

1. All four participants receive explicit room permission overrides.
2. All four participant bots can post in the intake/spec thread.
3. Router resolves both room channel and intake thread to the same feature-room participant set.
4. Arrietty remains default coordinator/public room responder.
5. Architect/Auditor/Scribe thread contributions are visible and intentional.
6. Generic room behavior does not produce uncontrolled multi-reply spam.
7. `post_channel_message(asRole=scribe)` sends using Scribe participant bot identity deterministically.
8. Explicit sender-resolution failures (`asBotId`/`asRole`) return clear action errors, not silent fallback.
9. Legacy single-owner rooms retain prior behavior.

## 9. Risks, Tradeoffs, and Deferred Items

### Risks

- Thread can still become noisy if workflow posts too many role turns.
- Strict sender-resolution errors can surface config inconsistencies earlier (desirable but operationally visible).
- Generic inbound policy needs careful tuning to balance responsiveness vs anti-spam.

### Tradeoffs

- Explicit policy + strict sender semantics increases predictability and testability.
- Coordination-first room policy limits noise but requires deliberate workflow orchestration for non-coordinator output.
- Structured thread collaboration favors clarity over spontaneous autonomy.

### Deferred items

- Advanced autonomous role arbitration for free-form inbound thread messages.
- Confidence gates / approval workflows / critique loops.
- Rich mention-addressed role routing heuristics beyond Step 1 scope.

## 10. File/Class/Config Impact Summary

Planned impact (plan-only, no implementation):

- `config/bots.yaml`
  - remove dependence on "silent workflow" concept as control mechanism
  - define explicit thread collaboration role posts in `luna_cursor`
  - preserve coordinator-centric room messaging
- `src/main/java/com/vinekeepers/workflow/actions/ProvisionRoomParticipantsAction.java`
  - enforce ORCHESTRATOR runtime reuse contract when Arrietty already provisioned
- `src/main/java/com/vinekeepers/workflow/actions/InitializeFeatureRoomStateAction.java`
  - strengthen role-based participant validation + fail-fast semantics
  - keep typed `FeatureRoomState` persistence
- `src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java`
  - formalize sender precedence + strict explicit-sender error behavior
- `src/main/java/com/vinekeepers/bot/Router.java`
  - ensure response policy compatibility between room and intake thread behavior
  - preserve legacy single-owner fallback
- `src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java`
  - ensure deterministic role->bot sender resolution path supports precedence rules
- Specs/docs alignment updates
  - `specs/workflow-registry.yml`
  - `specs/state-registry.yml` (if validation contract details need explicit statement)
  - `mkdoc/features/domain/workflow/cursor-gathering.md`
  - `mkdoc/features/domain/workflow/workflow-steps.md`
  - `README.md` behavior summary

