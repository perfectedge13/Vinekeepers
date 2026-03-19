---
name: ""
overview: ""
todos: []
isProject: false
---

# Multi-bot feature-room lifecycle (Step 1) — Revised implementation plan

This plan revises the multi-bot feature-room design to address response policy, permissions, participant handoff, minimal workflows, exact Luna sequence, and Arrietty-as-Orchestrator. The overall architecture (FeatureRoomState, Router, OutboundDeliveryRouter, backward compatibility) is unchanged; only the listed areas are tightened.

---

## Summary of revisions


| Area                             | Decision / change                                                                                                                                                                                                                             |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Response policy**              | All four bots receive the event; only Arrietty (Orchestrator) may publicly reply by default. Implemented via workflow design: Architect/Auditor/Scribe have minimal “silent” workflows. Document in code, docs, and routing.                  |
| **Permissions**                  | Step 1 includes permission overrides for all four bots. Extend CreateRoomRequest and DiscordSpaceOperations to support multiple participant bot ids; apply overrides for room and ensure thread posting works for all.                        |
| **Participant handoff**          | Exact state key `featureRoomParticipants`; value type `List<Map<String, Object>>`; required keys per entry; InitializeFeatureRoomStateAction reads and validates; RuntimeBotInstance remains in StateStore under `bot_instance:{instanceId}`. |
| **Minimal workflows (Option A)** | Four bot configs with extremely thin workflows: only enough to support routing and identity proof; no rich persona behavior in Step 1.                                                                                                        |
| **Luna sequence**                | Exact step order and rationale; Arrietty provisioned first for lifecycle context; FeatureRoomState before kickoff posts; create_channel with all four participant bot ids for permissions.                                                    |
| **Arrietty migration**           | Arrietty = Orchestrator; primary coordinator in multi-bot room; Architect, Auditor, Scribe added as additional participants; config/docs/specs updated so no split-brain with old single-owner story.                                         |


---

## 1. Response policy for four-bot inbound handling

### Problem

Router returns all four participant bot ids for feature-room events. If every bot runs workflow + reasoner and delivers a reply, the room becomes noisy and duplicative.

### Decision (Step 1)

- **Who receives the event:** All four bots remain first-class participants; Router still returns all four bot ids so lifecycle, session, and routing semantics are correct.
- **Who may publicly reply by default:** Only **Arrietty, acting as Orchestrator**, is the primary public responder for generic user messages.
- **Architect, Auditor, Scribe:** They only publicly respond when:
  - explicitly invoked by workflow logic (e.g. an action posts as that role), or
  - directly addressed (future: mention or trigger), or
  - a specific step uses `post_channel_message` with `asRole: architect` (etc.).

### Implementation

- **No Engine change required.** The engine already calls `handleEventForBot` for each routed bot and only calls `deliverReply` when `buildOutboundResponse` returns a non-null `OutboundResponse`; `buildOutboundResponse` returns null when both workflow and reasoner reply text are empty.
- **Workflow-level policy:** Give Architect, Auditor, and Scribe **minimal “silent” workflows** that complete without producing any reply (e.g. a single `done` step with empty or no message). Arrietty (Orchestrator) keeps a workflow that may produce a reply (e.g. arrietty_room or a dedicated orchestrator_kickoff that can reply when appropriate).
- **Documentation:** In code, document in Router and Engine (or a short “Feature room response policy” comment) that for feature rooms, only the primary responder (Orchestrator) should have workflows that return a public reply by default; other participants reply only when invoked via action or later logic. In docs (see Deliverables), add a “Response policy” section: who receives events, who may reply by default, how to avoid four replies per message, and how bot-to-bot coordination can fit without spam.

### Deliverable

- Code comments in Router and/or Engine describing the response policy.
- Docs: feature-room page or cursor-gathering page with “Response policy” subsection.
- Workflow config: Architect, Auditor, Scribe workflows are minimal and do not reply; Arrietty (Orchestrator) is the only one that may reply to generic messages in Step 1.

---

## 2. Discord permissions for all four bots (Step 1)

### Problem

Relying on guild defaults for the other three bots is insufficient; all four must be able to view the room, post in the room, and post in the intake/spec thread.

### Decision

Step 1 **must** include explicit permission overrides for all four participant bots when creating a multi-bot feature room.

### Implementation

- **Extend CreateRoomRequest:** Add support for multiple bot ids for permission override. Two options:
  - **Option A:** New field `lifecycleParticipantBotIds: List<String>` (or equivalent in bind/state). When present and non-empty, use it for permission overrides instead of (or in addition to) a single `lifecycleOwnerBotId`. Prefer this for multi-bot rooms.
  - **Option B:** Keep `lifecycleOwnerBotId` and add `lifecycleParticipantBotIds`; apply override for owner plus each participant.
- **Concrete choice:** Add `lifecycleParticipantBotIds` to the request. In Luna workflow, after we have the participant list (from provision_room_participants), we do **not** have it at create_channel time. So either:
  - **Approach 1:** create_channel accepts a **fixed** list of four bot ids for multi-bot rooms (e.g. bind `lifecycleParticipantBotIds: [arrietty, architect, auditor, scribe]`). Then DiscordSpaceOperations loops over the list and calls `addPermissionOverride` for each bot’s Discord user id. No need to wait for provision_room_participants.
  - **Approach 2:** Add a separate action `add_participant_permissions` that runs after create_channel and after we have channelId and participant list (or fixed four bot ids), and that calls a new SpaceOperations method or gateway method to add permission overrides for each participant. Then create_channel keeps a single owner (orchestrator) for backward compatibility, and add_participant_permissions adds the other three (or all four) for the same channel.

**Recommended:** **Approach 1** — extend CreateRoomRequest with an optional list of bot ids (e.g. `participantBotIds` or `lifecycleParticipantBotIds`). Bind from workflow: `participantBotIds: [arrietty, architect, auditor, scribe]`. DiscordSpaceOperations: after creating the channel, for each id in the list (or first the single lifecycleOwnerBotId if list absent, then the list if present), resolve Discord user id via `router.getSelfUserIdForBot(botId)` and call `addPermissionOverride(channelId, guildId, userId, LIFECYCLE_OWNER_ALLOW, 0L)`. This keeps one code path and one place for permission logic.

- **Thread posting:** Discord threads inherit parent channel permissions by default. So once all four bots have VIEW_CHANNEL | SEND_MESSAGES on the parent room, they can post in the thread. No separate thread permission step unless the codebase explicitly overrides thread permissions elsewhere.

### Files to change

- **CreateRoomRequest** (connectors): Add optional `List<String> participantBotIds` (or similar). In `from()`, read from bind/state (e.g. `participantBotIds` as list; support YAML list in bind).
- **DiscordSpaceOperations.createRoom:** If `request.participantBotIds()` is non-empty, iterate and add permission override for each; else fall back to single `lifecycleOwnerBotId` for backward compatibility.
- **CreateRoomRequest.from():** Parse list from bind/state (config may pass list of bot ids).

### Docs/runbook

- Document that multi-bot feature rooms require `participantBotIds` (or equivalent) in create_channel bind so all four bots get explicit override.
- Runbook: ensure all four Discord bot tokens are in the same guild and that create_channel is called with the four bot ids for permission.

---

## 3. Participant provisioning output / state handoff (exact contract)

### State key and storage

- **Workflow state key:** `featureRoomParticipants`
- **Stored by:** The step that calls `provision_room_participants` with `storeIn: featureRoomParticipants`.
- **Stored value type:** `List<Map<String, Object>>` (same as `Object` in `ConfigurableWorkflowState.getData()`; the runner calls `state.put("featureRoomParticipants", actionReturnValue)`).

### Shape of each participant entry

Each element of the list is a `Map<String, Object>` with the following keys (all required for downstream):


| Key                    | Type    | Description                                              |
| ---------------------- | ------- | -------------------------------------------------------- |
| `role`                 | String  | One of `ORCHESTRATOR`, `ARCHITECT`, `AUDITOR`, `SCRIBE`  |
| `configuredBotId`      | String  | Bot id from config (e.g. `arrietty`, `architect`)        |
| `runtimeBotInstanceId` | String  | Instance id from provisioning (e.g. `arrietty-a1b2c3d4`) |
| `displayName`          | String  | Display name for the participant                         |
| `primaryCoordinator`   | Boolean | True only for the Orchestrator                           |


### How ProvisionRoomParticipantsAction produces it

- The action returns a `List<Map<String, Object>>` built from the four RoomParticipant-equivalent maps. It does **not** store this list in StateStore itself; the workflow runner stores the return value in `state.getData().put("featureRoomParticipants", returnValue)`.
- Runtime bot instances are **also** persisted in StateStore under `bot_instance:{runtimeBotInstanceId}` by the same action (same as ProvisionBotInstanceAction), so each participant’s runtime instance is available for other code that keys by instance id.

### How InitializeFeatureRoomStateAction reads and validates

- Read from state (and bind override if desired): `state.get("featureRoomParticipants")`.
- Validate: value must be a non-null List; size must be 4; each element must be a Map containing the keys above; role must be one of the four enum values; configuredBotId and runtimeBotInstanceId non-blank. On validation failure, return a clear error (e.g. "Missing or invalid featureRoomParticipants").
- Build `List<RoomParticipant>` from the list of maps (or build FeatureRoomState directly from maps) and put FeatureRoomState in FeatureRoomStateStore.

### Deliverable

- Code: ProvisionRoomParticipantsAction returns exactly `List<Map<String, Object>>` with the five keys per entry; storeIn key is `featureRoomParticipants`.
- Code: InitializeFeatureRoomStateAction reads `featureRoomParticipants` from state, validates shape and size, then builds FeatureRoomState.
- Plan and docs: document this contract in the plan and in workflow/action docs so it is deterministic and not approximate.

---

## 4. Minimal workflows for all four bots (Option A)

### Decision

Keep four separate bot configs (arrietty, architect, auditor, scribe) but make Architect, Auditor, and Scribe workflows **extremely thin**: only what is needed to prove routing and identity for Step 1. No rich persona behavior yet.

### Rationale

- Step 1 goal is architectural support: four real bots, routing, sender-by-role, permissions, and Luna handoff. Rich behavior (design perspective, risk perspective, spec perspective) is later.
- Thin workflows reduce config and test surface and avoid unnecessary complexity.
- Arrietty already has `arrietty_room`; it can remain as the Orchestrator workflow that may reply. The other three get new minimal workflows that complete without replying (e.g. one step: done with empty message, or a single capture_field that never triggers in practice).

### Expected result

- All four bots are real configured participants with Discord identity and workflow refs.
- Each bot can send as itself via `post_channel_message` with `asRole` or `asBotId`.
- Only Arrietty (Orchestrator) workflow is configured to potentially produce a public reply to a generic message; the other three workflows are minimal and do not reply.
- Config and workflow complexity stay low for Step 1.

### Deliverable

- Plan and config: document Option A; in bots.yaml, define minimal workflows for architect, auditor, scribe (e.g. one-step done with no message or minimal message that is not used for generic replies).

---

## 5. Exact Luna handoff sequence

The following order is dependency-safe and matches the action contracts.

### Step order and rationale


| Step | Action / step                 | Rationale                                                                                                                                                                                                                                |
| ---- | ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | create_channel                | Create the feature room first. Bind `participantBotIds: [arrietty, architect, auditor, scribe]` (and optionally keep `lifecycleOwnerBotId: arrietty` for backward compat). All four get permission override.                             |
| 2    | branch                        | If channelId == CHANNEL_CREATE_FAILED → error step; else continue.                                                                                                                                                                       |
| 3    | provision_bot_instance        | Template arrietty; storeIn instanceId. Required because create_lifecycle_context expects one configuredBotId and one runtimeBotInstanceId (for the primary coordinator).                                                                 |
| 4    | create_lifecycle_context      | Bind configuredBotId: arrietty, channelId from state, instanceId from state, repo/requestText from state. Creates the single LifecycleContext that ties room to Arrietty as primary. storeIn: contextId.                                 |
| 5    | create_thread                 | Bind threadName: "intake-spec" (or "feature-intake"), channelId/contextId from state. storeIn: deliveryChannelId. CreateThreadAction / DiscordSpaceOperations will call setDeliveryTargetId(contextId, threadId) on success.             |
| 6    | provision_room_participants   | Bind channelId from state. Produces four participants (Arrietty already provisioned in step 3; this action can either reuse that instance for Orchestrator or provision all four—see below). storeIn: featureRoomParticipants.           |
| 7    | initialize_feature_room_state | Bind contextId, roomChannelId (channelId), intakeThreadId (deliveryChannelId), repo, initialRequest (codeChange), createdBy* from state; participants from state.featureRoomParticipants. Creates FeatureRoomState; status INTAKE_READY. |
| 8    | post_channel_message          | Room kickoff. Content: e.g. "Feature room ready. Repo: {{project}}. Request: {{codeChange}}. Four bots active: Orchestrator, Architect, Auditor, Scribe." Bind asRole: orchestrator (Arrietty).                                          |
| 9    | post_channel_message          | Intake-thread kickoff. Target: deliveryChannelId. Content: e.g. "Intake/spec thread ready." Bind asRole: scribe.                                                                                                                         |
| 10   | launch_cursor_run             | Unchanged.                                                                                                                                                                                                                               |
| 11   | done                          | Message: "Launching now. See <#{{channelId}}>."                                                                                                                                                                                          |


### ProvisionRoomParticipantsAction and Arrietty

- **Option A:** provision_room_participants provisions all four, including Arrietty again (second instance). Then LifecycleContext still has the first Arrietty instance from step 3; FeatureRoomState has four participants (each with its own runtime instance). Slight redundancy but consistent.
- **Option B:** provision_room_participants provisions only architect, auditor, scribe; for Orchestrator it reuses state.instanceId and state (arrietty) configuredBotId. Then the participant list has one entry with runtimeBotInstanceId from step 3 and three from step 6. This avoids two Arrietty instances.
- **Preferred for plan:** **Option B** — provision_room_participants accepts optional “reuse instance for orchestrator” by reading state.instanceId and state channelId; it provisions the other three and builds the Orchestrator participant from state (configuredBotId: arrietty, runtimeBotInstanceId: state.instanceId). So step 3 remains required; step 6 produces the list of four with no duplicate provisioning.

### Thread name

Use **intake-spec** consistently in create_thread bind and in docs.

### Deliverable

- Plan and config: exact Luna workflow order in bots.yaml and in this plan; rationale documented per step.
- Code: Ensure CreateThreadAction / DiscordSpaceOperations receive contextId so setDeliveryTargetId is called after thread creation (already the case via request/state).

---

## 6. Arrietty migration / replacement story

### Decision

**Arrietty becomes the Orchestrator** in the feature-room flow. Arrietty is not removed; it is the primary coordinator and fills the Orchestrator role. Architect, Auditor, and Scribe are added as additional real bot participants.

### Implications

- **Config:** Luna’s create_channel still uses arrietty as the first/orchestrator participant; create_lifecycle_context uses configuredBotId: arrietty. Arrietty keeps workflow arrietty_room (or a dedicated orchestrator workflow). No “Arrietty vs Orchestrator” split: Arrietty is the Orchestrator.
- **Docs/specs:** Replace “Arrietty as sole room owner” with “Arrietty as Orchestrator (primary coordinator) in a four-bot feature room; Architect, Auditor, Scribe are additional participants.” Backward compatibility: rooms created without FeatureRoomState remain single-owner (Arrietty only).
- **Naming:** In comments and docs, use “Arrietty (Orchestrator)” or “Orchestrator (Arrietty)” so the mapping is clear. Do not introduce a separate “orchestrator” bot id if we use arrietty for that role; the plan previously said “orchestrator” as a bot id—clarify: either the bot id is `arrietty` and the role is ORCHESTRATOR, or we have a separate bot id `orchestrator`. User said “Arrietty becomes the Orchestrator”, so the bot id remains **arrietty** and the PlanningRole for that participant is ORCHESTRATOR. So we have three **new** bots: architect, auditor, scribe; and arrietty is the fourth participant with role ORCHESTRATOR.

### Config summary

- **Bots in config:** luna, arrietty, architect, auditor, scribe (five bots).
- **Feature room participants:** arrietty (ORCHESTRATOR), architect (ARCHITECT), auditor (AUDITOR), scribe (SCRIBE).
- **create_channel participantBotIds:** [arrietty, architect, auditor, scribe].
- **create_lifecycle_context configuredBotId:** arrietty.

### Deliverable

- Plan and docs: explicit “Arrietty = Orchestrator” section; update config and runbook so there is no split-brain between old single-owner and new four-bot model.
- Specs: update bot-registry and workflow-registry wording so Arrietty is described as the Orchestrator in feature rooms and the other three are additional participants.

---

## 7. Exact file/class/action/config changes (consolidated)

### New files


| File                                                                                   | Purpose                                                                                                                                                |
| -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `src/main/java/com/vinekeepers/state/planning/PlanningRole.java`                       | Enum ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE                                                                                                          |
| `src/main/java/com/vinekeepers/state/planning/RoomParticipant.java`                    | Immutable: role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator                                                                |
| `src/main/java/com/vinekeepers/state/planning/FeatureRoomState.java`                   | Immutable: contextId, featureId, featureSlug, roomChannelId, intakeThreadId, repo, initialRequest, status, participants, createdBy*, createdAt         |
| `src/main/java/com/vinekeepers/state/planning/FeatureRoomStateStore.java`              | In-memory store; indexes by contextId, roomChannelId, intakeThreadId, featureId                                                                        |
| `src/main/java/com/vinekeepers/workflow/actions/ProvisionRoomParticipantsAction.java`  | Provisions three bots (architect, auditor, scribe); reuses state.instanceId for arrietty; returns List<Map<String,Object>> for featureRoomParticipants |
| `src/main/java/com/vinekeepers/workflow/actions/InitializeFeatureRoomStateAction.java` | Reads featureRoomParticipants from state; validates; builds and stores FeatureRoomState                                                                |


### Modified files


| File                            | Change                                                                                                                                                                                                                                                                                                                                                                                                             |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `CreateRoomRequest.java`        | Add optional participantBotIds (List); in from(), parse from bind/state                                                                                                                                                                                                                                                                                                                                            |
| `DiscordSpaceOperations.java`   | In createRoom(), loop participantBotIds and add permission override for each; fallback to lifecycleOwnerBotId if list empty                                                                                                                                                                                                                                                                                        |
| `Router.java`                   | Resolve lifecycle by getByChannelId then getByDeliveryTargetId; inject FeatureRoomStateStore; when FeatureRoomState present return four participant configuredBotIds in order; else legacy single-owner                                                                                                                                                                                                            |
| `OutboundDeliveryRouter.java`   | Add FeatureRoomStateStore; add sendAs(asBotId), sendAsRole(role); resolve role via FeatureRoomState.participants                                                                                                                                                                                                                                                                                                   |
| `PostChannelMessageAction.java` | Read asBotId/asRole from bind; if OutboundDeliveryRouter, call sendAs/sendAsRole when set                                                                                                                                                                                                                                                                                                                          |
| `Bootstrap.java`                | Create FeatureRoomStateStore; pass to Router and OutboundDeliveryRouter; register provision_room_participants, initialize_feature_room_state                                                                                                                                                                                                                                                                       |
| `config/bots.yaml`              | Add bots: architect, auditor, scribe (with minimal workflows); add workflows: architect_intro, auditor_intro, scribe_intro (minimal); luna_cursor: exact sequence as in section 5, create_channel with participantBotIds: [arrietty, architect, auditor, scribe], threadName intake-spec, provision_room_participants, initialize_feature_room_state, two post_channel_message with asRole orchestrator and scribe |


### Exact Luna workflow order (YAML steps after confirm launch)

1. call_action create_channel, bind: `participantBotIds: [arrietty, architect, auditor, scribe]`, storeIn: channelId
2. branch on channelId (CHANNEL_CREATE_FAILED → error)
3. call_action provision_bot_instance, bind: templateBotId: arrietty, storeIn: instanceId
4. call_action create_lifecycle_context, bind: configuredBotId: arrietty, storeIn: contextId
5. call_action create_thread, bind: threadName: "intake-spec", storeIn: deliveryChannelId
6. call_action provision_room_participants, storeIn: featureRoomParticipants
7. call_action initialize_feature_room_state (bind passes contextId, channelId, deliveryChannelId, project, codeChange, etc. from state)
8. call_action post_channel_message, bind: content: "Feature room ready. Repo: {{project}}. Request: {{codeChange}}. Four bots: Orchestrator, Architect, Auditor, Scribe.", asRole: orchestrator
9. call_action post_channel_message, bind: content: "Intake/spec thread ready.", deliveryChannelId from state (target thread), asRole: scribe
10. call_action launch_cursor_run
11. done message: "Launching now. See <#{{channelId}}>."

(Plus existing error/cancel steps at the end.)

---

## 8. Permission-handling decision (summary)

- **Decision:** Step 1 includes explicit permission overrides for all four bots.
- **Mechanism:** Extend CreateRoomRequest with `participantBotIds` (list). DiscordSpaceOperations.createRoom applies addPermissionOverride for each bot in the list. Luna bind: `participantBotIds: [arrietty, architect, auditor, scribe]`.
- **Thread:** Rely on Discord’s inheritance of parent channel permissions; no separate thread permission step unless the product later requires it.

---

## 9. Response policy decision (summary)

- **Decision:** All four bots receive the event; only Arrietty (Orchestrator) may publicly reply by default.
- **Mechanism:** Workflow design—Architect, Auditor, Scribe have minimal workflows that do not produce a reply. Arrietty (Orchestrator) may reply. No Engine change.
- **Documentation:** Code comments and docs section on response policy.

---

## 10. Arrietty migration decision (summary)

- **Decision:** Arrietty = Orchestrator; primary coordinator in multi-bot room; three new bots (architect, auditor, scribe) added.
- **Config:** participantBotIds and feature room participants list include arrietty with role ORCHESTRATOR; create_lifecycle_context configuredBotId remains arrietty.
- **Docs/specs:** Update all references so Arrietty is clearly the Orchestrator and the other three are additional participants; backward compatibility for legacy single-owner rooms preserved.

---

## 11. Tests and docs/spec updates

### Tests to add or update

- FeatureRoomStateStore: create, put, get by contextId, roomChannelId, intakeThreadId, featureId; update.
- ProvisionRoomParticipantsAction: returns four entries; shape matches contract; Orchestrator reuses state.instanceId; runtime instances stored in StateStore under bot_instance:*.
- InitializeFeatureRoomStateAction: validates featureRoomParticipants; rejects invalid/missing list; builds and stores FeatureRoomState.
- Router: room and thread resolve to same four bot ids; order stable; legacy room without FeatureRoomState returns single owner.
- OutboundDeliveryRouter: sendAs, sendAsRole; fallback when no role/feature state.
- PostChannelMessageAction: asRole/asBotId passed to router when present.
- CreateRoomRequest / DiscordSpaceOperations: participantBotIds applied; multiple overrides; backward compat with single lifecycleOwnerBotId.
- Config/bootstrap: new actions and bots load; no regression.

### Docs to update

- Feature room / cursor-gathering: response policy, permissions (participantBotIds), participant handoff (featureRoomParticipants contract), Arrietty = Orchestrator.
- Runbook: four Discord tokens (arrietty, architect, auditor, scribe); participantBotIds in create_channel for multi-bot rooms.

### Specs to update

- state-registry: new assets for PlanningRole, RoomParticipant, FeatureRoomState, FeatureRoomStateStore.
- workflow-registry: new actions; Luna sequence; acceptance criteria for multi-bot handoff.
- bot-registry: Router behavior; Arrietty as Orchestrator.
- connectors-registry: CreateRoomRequest participantBotIds; OutboundDeliveryRouter sendAs/sendAsRole.

---

## 12. Slices (unchanged structure, revised details)

- **Slice 1:** Add participant state model and store (PlanningRole, RoomParticipant, FeatureRoomState, FeatureRoomStateStore) — unchanged; package state.planning.
- **Slice 2:** ProvisionRoomParticipantsAction — produces List for featureRoomParticipants; reuses instanceId for arrietty; provisions architect, auditor, scribe; stores RuntimeBotInstance in StateStore per bot; exact contract as in section 3.
- **Slice 3:** InitializeFeatureRoomStateAction — reads and validates featureRoomParticipants; builds FeatureRoomState; puts in store; exact contract as in section 3.
- **Slice 4:** Router — room + thread resolution; FeatureRoomStateStore; return four participant bot ids when feature room; legacy single-owner otherwise.
- **Slice 5:** OutboundDeliveryRouter sendAs/sendAsRole; PostChannelMessageAction asRole/asBotId — unchanged.
- **Slice 6:** Four bots: arrietty (Orchestrator), architect, auditor, scribe; minimal workflows for architect/auditor/scribe (Option A); Arrietty = Orchestrator; participantBotIds in create_channel.
- **Slice 7:** Luna handoff — exact sequence as in section 5 and 7; thread name intake-spec; kickoff from Orchestrator and Scribe.
- **Slice 8:** Tests and docs/specs as in section 11.

This revised plan is ready for implementation with no hand-wavy follow-ups on permissions or handoff, and with explicit response policy and Arrietty migration story.