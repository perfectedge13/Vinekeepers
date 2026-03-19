# Change context (for plan_change / implement)

## Scope

**Request:** Step 1 multi-bot follow-up fixes (focused pass, no architecture change). Six items:

1. **PostChannelMessageAction** — Add explicit target: `target: room | thread` or `targetChannelId`. Room kickoff posts to room; scribe kickoff to thread. Update luna_cursor in config/bots.yaml.
2. **InitializeFeatureRoomStateAction** — Generate `featureId` (feat- + 12 hex) and `featureSlug` (from initialRequest/repo, sanitized) when missing; preserve when supplied.
3. **config/bots.yaml** — Add `sessionKeyStrategy: thread` for arrietty, architect, auditor, scribe.
4. **FeatureRoomStateStore.getParticipantBotIds** — Sort participants by `PlanningRole.ordinal()` so ordering is deterministic; add test for wrong input order.
5. **Architect/auditor/scribe workflows** — Ensure `message: ""` (silent); optional DoneStep or comment.
6. **Documentation** — Document thread-posting assumption (parent channel permissions) in runbook or cursor-gathering docs; optional test.

**Impacted registry slice:** workflow-registry.yml, config-registry.yml, bot-registry.yml, state-registry.yml, core-registry.yml (reference only for engine/sink).

**Features in scope:** FEAT-CURSOR-GATHERING, FEAT-WORKFLOW-STEPS, FEAT-STATE, FEAT-CONFIG, FEAT-BOT.

**Requirements in scope:** REQ-LUNA-001, REQ-WORKFLOW-001, REQ-STATE-001, REQ-CONFIG-001, REQ-BOT-003.

**Assets in scope:** ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-INITIALIZE-FEATURE-ROOM-STATE-ACTION, ASSET-FEATURE-ROOM-STATE-STORE, ASSET-BOTS-YAML, ASSET-PLANNING-ROLE, ASSET-DONE-STEP; config workflows architect_intro, auditor_intro, scribe_intro, luna_cursor.

---

## Per feature / requirement / asset

### FEAT-CURSOR-GATHERING (workflow-registry.yml)

- **Feature:** title Cursor-backed gathering workflow; status active; doc_path features/domain/workflow/cursor-gathering.md; summary: Luna gathers repo/feature, launch_cursor_run, lifecycle room Phase 1, post_channel_message, create_thread, multi-bot feature room.
- **REQ-LUNA-001:** post_channel_message send target = deliveryChannelId or channelId (bind then state); THREAD_CREATE_FAILED fallback to channelId; interpolates content; lifecycleBotName from bind; asRole/asBotId for sendAsRole/sendAs. Arrietty template workflowRef arrietty_room; create_thread storeIn deliveryChannelId; done step main-room redirect. No hardcoded bot ids.
- **Assets:** ASSET-POST-CHANNEL-MESSAGE-ACTION (path src/main/java/.../PostChannelMessageAction.java; role: post to channel, send target deliveryChannelId or channelId, asRole/asBotId), ASSET-INITIALIZE-FEATURE-ROOM-STATE-ACTION (path .../InitializeFeatureRoomStateAction.java; role: build FeatureRoomState from featureRoomParticipants, contextId, channelId; put in store), ASSET-BOTS-YAML (config/bots.yaml; luna_cursor steps, arrietty_room, architect_intro, auditor_intro, scribe_intro).
- **Anti-patterns (REQ-LUNA-001):** Hardcoding Discord channel; storing secrets in state; do not rely on discordTrigger alone; do not log Cursor API key/body; do not assume single Cursor error shape; do not serialize null in request payload.

### FEAT-WORKFLOW-STEPS (workflow-registry.yml)

- **REQ-WORKFLOW-001:** create_thread action; post_channel_message bind/state; Step bind and state passed to actions; interpolation merged map (state then bind). CallActionStep invokes registered action.
- **Assets:** ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-DONE-STEP (optional message).
- **Validation tests:** UNIT-POST-CHANNEL-MESSAGE-INTERPOLATION, UNIT-POST-CHANNEL-MESSAGE-AS-ROLE, UNIT-POST-CHANNEL-MESSAGE-AS-ROLE-BIND-OVERRIDES, UNIT-POST-CHANNEL-MESSAGE-ACTION, UNIT-POST-CHANNEL-MESSAGE-LIFECYCLE-BOT-NAME; UNIT-INITIALIZE-FEATURE-ROOM-STATE-ACTION.

### FEAT-STATE (state-registry.yml)

- **REQ-STATE-001:** StateStore load/save; FeatureRoomStateStore put, getByContextId, getByRoomChannelId, getByDeliveryTargetId.
- **Assets:** ASSET-FEATURE-ROOM-STATE (contextId, featureId, featureSlug, roomChannelId, intakeThreadId, participants, ...), ASSET-FEATURE-ROOM-STATE-STORE (path .../FeatureRoomStateStore.java; getParticipantBotIds returns participant configuredBotIds in stable order — doc says "Orchestrator first, then Architect, Auditor, Scribe"), ASSET-PLANNING-ROLE (enum ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE — ordinal 0,1,2,3).
- **Validation tests:** UNIT-FEATURE-ROOM-STATE-STORE (put, getByContextId, getByRoomChannelId, getByDeliveryTargetId). Add test for getParticipantBotIds ordering when input list order is wrong.

### FEAT-CONFIG (config-registry.yml)

- **REQ-CONFIG-001:** ConfigLoader parses workflow type/params, conversationMode, **sessionKeyStrategy**, routing filters. Step bind keys (lifecycleBotName, deliveryChannelId) and create_thread storeIn supported.
- **Assets:** ASSET-BOTS-YAML, ASSET-CONFIG-LOADER, ASSET-BOT-DEFINITION (sessionKeyStrategy in BotDefinition).
- **Contracts (config):** YAML bots[] with sessionKeyStrategy; workflow step bind keys.

### FEAT-BOT (bot-registry.yml)

- **REQ-BOT-003:** BotDefinition holds sessionKeyStrategy; ConfigLoader produces BotDefinition from YAML.
- **Assets:** ASSET-BOT-DEFINITION (getSessionKeyStrategy()), ASSET-SESSION-KEY-STRATEGIES (channel, channel_user, **thread**).

### Schema constraints

- Do not add new spec keys; stay within existing req-registry schema. Requirements: id, title, statement, status, acceptance.criteria, traceability.assets, validation.tests. Assets: id, path, role, requires.

---

## Doc excerpts (mkdoc)

**docs_dir:** mkdoc (from .cursor/project.yml paths.docs_dir).

### features/domain/workflow/cursor-gathering.md

- **Summary (excerpt):** Luna provisioning sequence: create_channel → provision_bot_instance → create_lifecycle_context → create_thread → post_channel_message → launch_cursor_run. PostChannelMessageAction send target = deliveryChannelId or channelId; THREAD_CREATE_FAILED fallback. CursorCloudRunMonitor sends to thread when deliveryChannelId set (messageId null).
- **Key assets table:** ASSET-POST-CHANNEL-MESSAGE-ACTION — "send target = deliveryChannelId or channelId (bind then state); treats THREAD_CREATE_FAILED as fallback to channelId; optional asRole/asBotId".

### features/domain/workflow/cursor-gathering/contracts.md

- **Lifecycle room:** post_channel_message supports asRole (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE) or asBotId; bind has precedence over state. create_thread uses lifecycle owner gateway; setDeliveryTargetId on success.
- **Multi-bot feature room:** Router returns four participant configuredBotIds when FeatureRoomState exists; architect, auditor, scribe reply when invoked via post_channel_message asRole.

### features/domain/workflow/cursor-gathering/decisions.md

- Luna config-driven in YAML; Discord mention in routing; repo ops via Cursor adapter.

### Runbooks

- **runbooks/index.md:** Index lists Configuring bots, Troubleshooting, etc.
- **runbooks/configuring-bots.md:** Bot definition, workflow refs, identities.discord, sessionKeyStrategy not yet called out for thread-based bots. **Gap for implement:** Add note on thread-posting assumption (parent channel permissions) — either in configuring-bots.md, or in cursor-gathering known-issues/decisions: "Thread posting assumes the bot has permission to send messages in the parent channel (and thus in threads created under it)."

### Not present / to add

- Explicit "thread target vs room target" for post_channel_message (target: room | thread or targetChannelId) is not yet in contracts; implement will add behavior and update docs.
- featureId/featureSlug generation in InitializeFeatureRoomStateAction: preserve when supplied; when missing generate featureId = "feat-" + 12 hex, featureSlug = sanitized from initialRequest/repo.

---

## Implementation checklist (from request)

| # | Item | Spec/asset | Notes |
|---|------|------------|-------|
| 1 | PostChannelMessageAction explicit target | ASSET-POST-CHANNEL-MESSAGE-ACTION, REQ-LUNA-001 | Add bind/state: target (room \| thread) or targetChannelId; room kickoff → room, scribe kickoff → thread. Update luna_cursor steps in bots.yaml accordingly. |
| 2 | InitializeFeatureRoomStateAction featureId/featureSlug | ASSET-INITIALIZE-FEATURE-ROOM-STATE-ACTION | When featureId/featureSlug missing: generate featureId = "feat-" + 12 hex; featureSlug from initialRequest/repo sanitized. Preserve when supplied. |
| 3 | sessionKeyStrategy: thread | ASSET-BOTS-YAML, REQ-CONFIG-001, BotDefinition | Add under workflow.params or bot-level for arrietty, architect, auditor, scribe. ConfigLoader already reads sessionKeyStrategy; SessionKeyStrategies has thread. |
| 4 | FeatureRoomStateStore.getParticipantBotIds sort | ASSET-FEATURE-ROOM-STATE-STORE, ASSET-PLANNING-ROLE | Sort by PlanningRole.ordinal() before returning list. Add unit test: participants in wrong order → returned list still ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE order. |
| 5 | Architect/auditor/scribe message: "" | config/bots.yaml workflows | architect_intro, auditor_intro, scribe_intro already have done step message: "". Confirm no other step emits; optional DoneStep or comment only. |
| 6 | Document thread-posting assumption | mkdoc runbook or cursor-gathering | Parent channel permissions: bot must be able to send in parent to post in thread. Optional test (e.g. capability or doc-only). |
