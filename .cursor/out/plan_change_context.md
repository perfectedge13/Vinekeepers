# Change context (for plan_change / implement)

## Scope

**Request-derived:** Discord waiting-session routing fix. VinekeepersEngine.onEvent: after router.route(event), for Discord message events only, add bots that have WAITING_INPUT state for the event's session key (SessionKeyStrategies + stateStore). No Router or config changes. Add tests in VinekeepersEngineTest.

**Impacted registry slice:** specs/core-registry.yml — engine delivery (REQ-CORE-003), session key and workflow state (REQ-WORKFLOW-001, REQ-STATE-001), routing (REQ-BOT-001 referenced; no config/router changes).

**Features / requirements / assets in scope:**
- **Features:** FEAT-ENGINE, FEAT-WORKFLOW, FEAT-STATE
- **Requirements:** REQ-CORE-003 (primary), REQ-WORKFLOW-001, REQ-STATE-001
- **Assets:** ASSET-ENGINE, ASSET-SESSION-KEY-STRATEGIES, ASSET-SESSION-KEY-STRATEGY, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-STATE-STORE; test: VinekeepersEngineTest (UNIT-ENGINE)

---

## Per feature

### FEAT-ENGINE (Event-driven engine orchestration)

- **Feature:** title: Event-driven engine orchestration; status: active; doc_path: features/domain/core/engine.md; summary: VinekeepersEngine routes events to matched bots, executes workflow runners, applies reasoner output, and emits replies.
- **Requirements (REQ-CORE-003):**
  - id: REQ-CORE-003; title: Event-driven engine routes events to bots
  - statement: VinekeepersEngine receives events, routes them to matching bots, runs the registered WorkflowRunner, then passes workflow context and session state to the reasoner before choosing a reply and recording audit events. Reply delivery uses the connector contract (AppReplySink) with distinct lifecycle operations (respondImmediately, sendFollowUp, updateMessage, openModal, getCapabilities); engine builds OutboundResponse from workflow or reasoner (rich or text-only), resolves ReplyTarget from event, and calls the sink registered for the event source (transitional source-prefix routing). Interaction events (kind interaction) are supported; platform timing and defer are connector-owned only.
  - acceptance.criteria: Engine subscribes to event bus and dispatches to bots by routing rules; Engine uses registered runners and runner.runResult(event, stateStore, botId) for workflow execution; Engine gives the reasoner workflow reply context, current session state, and the last normalized user message; Engine applies reasoner state patches and executes proposed tool calls through ToolRunner; Engine delivers replies via sink registry (source-prefix) and lifecycle methods (respondImmediately, sendFollowUp, updateMessage, openModal); sink exposes getCapabilities; no defer in engine; No bot registered for id logs warning and does not fail.
  - validation.tests: UNIT-ENGINE — VinekeepersEngineTest — Verify engine receives events and routes to bots.
  - anti_patterns: (none listed on this requirement)
- **Assets:** ASSET-ENGINE (path: src/main/java/com/vinekeepers/core/VinekeepersEngine.java; role: Route events to bots; run workflow and reasoner; apply tool/state side effects; deliver replies via connector sink registry; build OutboundResponse; resolve ReplyTarget; no defer in engine).
- **Doc excerpts (mkdoc, docs_dir: mkdoc):**
  - engine.md: Summary — Event-driven engine routes events to bots (REQ-CORE-003). VinekeepersEngine receives events, routes them to matching bots, runs WorkflowRunner, builds ReasonerInput from workflow context and session state, applies ReasonerOutput patches and proposed tool calls, records audit, builds OutboundResponse, resolves ReplyTarget, delivers via sink registry (respondImmediately, sendFollowUp, updateMessage); no defer in engine.
  - decisions.md: Workflow execution runs before reasoner. Proposed tool calls executed by ToolRunner under policy. Engine orchestration documented separately from bootstrap.
  - contracts.md: ReasonerInput carries workflow context, current state, last normalized user message. ReasonerOutput carries reply text, optional state patches, proposed tool calls. WorkflowRunResult carries workflow reply and lifecycle flags. VinekeepersEngine subscribes to events and coordinates bot execution; WorkflowRunner is engine-facing workflow execution contract.
  - known-issues.md: Missing bot registrations logged and skipped. Reply delivery is connector-specific; built-in reply path is for Discord-sourced events.

### FEAT-WORKFLOW (Workflow runners and session lifecycle)

- **Requirements (REQ-WORKFLOW-001):** Workflow state machine and config-driven runners. SessionKeyStrategies resolve channel, channel_user, or thread conversation keys for persisted workflow state. Configurable workflow state records waiting field, pending prompt, completion, and error status. Configurable workflows support prompt_for_field and capture_field steps for conversational pause/resume.
- **Assets:** ASSET-SESSION-KEY-STRATEGY (path: src/main/java/com/vinekeepers/workflow/SessionKeyStrategy.java; role: Contract for resolving per-bot workflow session keys from events). ASSET-SESSION-KEY-STRATEGIES (path: src/main/java/com/vinekeepers/workflow/SessionKeyStrategies.java; role: Built-in session key strategies for channel, channel_user, and thread conversations). ASSET-CONFIGURABLE-WORKFLOW-STATE (path: src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowState.java; role: Mutable state for configurable workflow including waiting, completed, and error lifecycle data; clearKeys(keys) removes given keys from state (edit-reprompt)). Status enum: ACTIVE, WAITING_INPUT, COMPLETED, ERROR; getStatus(), markWaiting(field, prompt).
- **Anti_patterns:** (none on these assets)

### FEAT-STATE (State store for workflow state)

- **Requirements (REQ-STATE-001):** StateStore persists and loads per-bot, per-conversation workflow state (structured state objects). StateStore.load(botId, conversationKey) returns state or empty; StateStore.save persists state for bot and conversation.
- **Assets:** ASSET-STATE-STORE (path: src/main/java/com/vinekeepers/state/StateStore.java; role: Persist and load bot workflow state). API: get(key, Class), put(key, state), keys().
- **Anti_patterns:** (none)

---

## Implementation notes for implement step

- **REQ-CORE-003, engine delivery:** After `router.route(event)` in `VinekeepersEngine.onEvent`, for **Discord message events only** (e.g. `"message".equals(event.getKind())` and `event.getSourceId().startsWith("discord")`), compute the set of bot ids that already have WAITING_INPUT state for this event's session key, and **add** those bot ids to the list of bot ids to handle (merge with router result, avoid duplicates). Then iterate the combined list and call handleEventForBot for each.
- **Session key:** Use existing `SessionKeyStrategies.resolve(bot.getSessionKeyStrategy(), event).resolveSessionKey(bot.getId(), event)` per registered bot; BotDefinition.getSessionKeyStrategy() may be null/blank (SessionKeyStrategies.resolve uses defaultStrategyName(event)).
- **Waiting state:** For each registered bot, resolve session key from event, `stateStore.get(sessionKey, ConfigurableWorkflowState.class)`; if present and `state.getStatus() == ConfigurableWorkflowState.Status.WAITING_INPUT`, include that botId in the set to handle. Only consider bots that are registered (have runner/reasoner as needed); do not change Router or config.
- **Tests (VinekeepersEngineTest):** Add test(s) that: (1) register a bot with a configurable workflow that leaves state in WAITING_INPUT for a given session key; (2) send a first Discord message so the bot is routed and enters WAITING_INPUT; (3) send a second Discord message in the same session that the router would *not* route to that bot (e.g. no mention); (4) assert the engine still invokes the bot for the second message because state for that session key is WAITING_INPUT. Optionally add a test that non-Discord or non-message events do not get waiting-session augmentation.
- **Schema constraints:** Do not add or remove requirement ids or asset ids in core-registry.yml; do not invent new spec keys. Optional: add a validation test entry for the new VinekeepersEngineTest method(s) under REQ-CORE-003 validation.tests (same schema as existing UNIT-ENGINE).

---

## Schema constraints (brief)

Allowed requirement keys: id, title, statement, status, priority, type, behavior, acceptance, traceability, validation, anti_patterns. Allowed asset keys: id, kind, path, role, requires, feature_ids. Do not delete requirements or remove required functionality; repair spec drift before coding; avoid anti_patterns on impacted requirements/assets.
