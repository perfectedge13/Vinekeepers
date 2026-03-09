# Change context (for plan_change / implement)

## Scope

**Request-derived:** Implement Phases 1A–1E of the rich app interactions plan (approved design). Source: `c:\Users\perfe\.cursor\plans\discord_interactions_and_mcp_ef00127f.plan.md`.

**Impacted registry slice:** Core (engine, workflow, reasoner, reply path) and Connectors (Discord). Primary scope:

- **Features:** FEAT-ENGINE, FEAT-WORKFLOW, FEAT-WORKFLOW-STEPS, FEAT-CONNECTORS-DISCORD; plus **new** interactions/reply feature to be added to core-registry.
- **Requirements (existing, impacted):** REQ-CORE-003, REQ-WORKFLOW-001, REQ-REASONER-001, REQ-CONNECTORS-DISCORD-001.
- **Requirements (to add per plan §7):** Core — rich reply and interaction handling (engine uses connector contract with lifecycle operations, OutboundResponse, ReplyTarget; workflow/reasoner may produce OutboundResponse; interaction events supported; connector owns platform timing). Connectors — Discord adapter implements full lifecycle (immediate, defer, follow-up, update, modal); adapter responsible for ack/defer within Discord window; auto-defer when sync reply not safely possible; fallback to text.
- **Assets (existing, changed):** ASSET-ENGINE, ASSET-WORKFLOW-RUN-RESULT, ASSET-STEP-RESULT, ASSET-REASONER-OUTPUT, ASSET-BOOTSTRAP, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-WORKFLOW-DEFINITION, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-BOTS-YAML, ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT.
- **Assets (to add):** New package `com.vinekeepers.interactions` (ResponseIntent, OutboundResponse, ReplyTarget, Capabilities, connector contract interface); ASSET-DISCORD-REPLY-SINK (DiscordAppReplySink).

---

## Per feature / domain

### Engine (FEAT-ENGINE) — core reply path and sink

**Feature:** Event-driven engine orchestration. Status: active. Doc path: features/domain/core/engine.md. Summary: VinekeepersEngine routes events to bots, runs workflow and reasoner, applies tool/state side effects, emits replies.

**Requirements (in scope):**
- **REQ-CORE-003** — Event-driven engine routes events to bots. Statement: Engine receives events, routes to matching bots, runs WorkflowRunner, passes workflow context and session state to reasoner, chooses reply, records audit. Criteria: Engine subscribes to bus, dispatches by routing; uses runner.runResult(event, stateStore, botId); gives reasoner workflow reply context, state, last normalized message; applies state patches and executes proposed tool calls via ToolRunner. Traceability: ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT. Validation: UNIT-ENGINE (VinekeepersEngineTest). Anti_patterns: (none in registry).

**Assets:** ASSET-ENGINE (VinekeepersEngine.java — route events, run workflow/reasoner, deliver replies). Role: Replace DiscordReplySender with sink registry (transitional: by sourceId prefix); build OutboundResponse from workflow/reasoner; resolve ReplyTarget from event; call lifecycle methods (respondImmediately, sendFollowUp, updateMessage); no defer in engine.

**Doc excerpts (engine):**
- **Decisions:** Workflow runs before reasoner; proposed tool calls executed by ToolRunner under policy; engine orchestration documented separately from bootstrap.
- **Contracts:** ReasonerInput/ReasonerOutput/WorkflowRunResult schemas; VinekeepersEngine, WorkflowRunner, ToolRunner, Reasoner interfaces. Engine is internal orchestration component.
- **Known issues:** Missing bot registrations logged and skipped; reply delivery is connector-specific (Discord-sourced events).

---

### Workflow and workflow steps (FEAT-WORKFLOW, FEAT-WORKFLOW-STEPS)

**Feature (workflow):** Workflow runners and session lifecycle. Doc path: features/domain/workflow/workflow.md. Summary: WorkflowRunner and ConfigurableWorkflowRunner execute configured or stub workflows with persisted state.

**Feature (steps):** Workflow step DSL and branching actions. Doc path: features/domain/workflow/workflow-steps.md. Summary: WorkflowDefinition and step types implement prompt, capture, branch, action, done.

**Requirements (in scope):**
- **REQ-WORKFLOW-001** — Workflow state machine and config-driven runners. Statement: WorkflowRunner runs workflow for an event and returns WorkflowRunResult with reply, waiting, completion, error. Criteria: Configurable workflows support prompt_for_field and capture_field; session key strategies; CallActionStep can invoke workflow action or ToolRunner-backed tool. Traceability: ASSET-WORKFLOW-RUN-RESULT, ASSET-STEP-RESULT, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-WORKFLOW-DEFINITION, etc. Validation: UNIT-CONFIGURABLE-WORKFLOW-RUNNER-CONVERSATION, UNIT-STEP-RESULT, etc. Anti_patterns: (none listed for workflow).

**Assets (changed):** ASSET-WORKFLOW-RUN-RESULT (optional OutboundResponse richReply; keep replyMessage). ASSET-STEP-RESULT (optional OutboundResponse richReply; keep message). ASSET-CONFIGURABLE-WORKFLOW-RUNNER (parse intent, choices, confirmLabel, cancelLabel, fields; steps producing OutboundResponse). ASSET-PROMPT-FOR-FIELD-STEP (produce StepResult with OutboundResponse when intent/options present). ASSET-CAPTURE-FIELD-STEP (for kind: interaction read values/customId from payload; message unchanged). ASSET-WORKFLOW-DEFINITION (step config may include intent, choices, fields). ASSET-BOTS-YAML (optional example workflow with intent/choices).

**Doc excerpts (workflow-steps):**
- **Decisions:** StepOutcome is explicit; CallActionStep resolves workflow action or ToolRunner-backed tool; runner lifecycle documented separately.
- **Contracts:** WorkflowDefinition id + step configs; StepResult carries message, stored values, next-step, StepOutcome. WorkflowStep, WorkflowAction, WorkflowActionRegistry interfaces.
- **Known issues:** DSL limited to built-in step types; tool-backed actions depend on runner for context and policy.

---

### Reasoner (FEAT-REASONER)

**Requirements (in scope):**
- **REQ-REASONER-001** — Reasoner interface for bot decisions. Statement: Reasoner.reason(ReasonerInput) returns ReasonerOutput with reply text, state patch, proposed tool calls. Criteria: ReasonerOutput can return reply text, state patches, proposed tool calls. Traceability: ASSET-REASONER-OUTPUT, ASSET-ENGINE.

**Assets:** ASSET-REASONER-OUTPUT (ReasonerOutput.java). Add optional OutboundResponse richReply; keep replyText. Constructor/factory updated.

---

### New: interactions package and connector contract (core-registry)

**Scope (to add):** Package `com.vinekeepers.interactions` and connector contract. Not yet in registry; plan §4–§7 defines:

- **ResponseIntent** — Typed enum/sealed + records: PresentChoices, ConfirmAction, CollectText, CollectForm, ShowActions, ShowStatus (platform-neutral fields only).
- **OutboundResponse** — Optional text, Optional single ResponseIntent; at least one present; design evolvable (e.g. list of intents later).
- **ReplyTarget** — Sealed: ChannelTarget(sourceId, channelId, messageId), InteractionTarget(sourceId, channelId, messageId, interactionId, token, alreadyDeferred).
- **Capabilities** — Typed record: supportedIntents (Set<ResponseIntentType>), supportsInteractions, supportsModalInput, supportsMessageUpdate, supportsEphemeralReplies, deferRequiredWithinMs, optional limits (maxChoicesPerSelect, etc.). No Set<String>.
- **Connector contract (e.g. AppReplySink):** respondImmediately(OutboundResponse, ReplyTarget), sendFollowUp(OutboundResponse, ReplyTarget), updateMessage(OutboundResponse, ReplyTarget), openModal(OutboundResponse, ReplyTarget), getCapabilities(). Defer is adapter-internal only; engine never calls defer.

**Anti_patterns (from plan):** Do not put response/intent model under connectors; do not expose defer to engine; do not use stringly-typed capabilities.

---

### Discord connector (FEAT-CONNECTORS-DISCORD)

**Feature:** Discord event source and reply. Status: active. Doc path: features/domain/connectors/discord.md. Summary: DiscordEventSource and JDA gateway receive messages, preserve mention metadata, deliver replies.

**Requirements (in scope):**
- **REQ-CONNECTORS-DISCORD-001** — Discord event source and reply. Statement: DiscordEventSource implements EventSource; gateway receives/sends; DiscordReplySender (channelId, messageId, content). Criteria: Emit events to bus; pass mention metadata; send replies to channel/message; engine delivers replies when source is Discord. Traceability: ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT. Validation: UNIT-ENGINE-DISCORD-REPLY, UNIT-DISCORD-EVENT-SOURCE, etc.

**Requirements (to add per plan §7):** Discord adapter implements full lifecycle (immediate, defer, follow-up, update, modal); adapter responsible for ack/defer within Discord window; auto-defer when sync reply not safely possible; fallback to text. New asset: DiscordAppReplySink.

**Assets (changed):** ASSET-DISCORD-GATEWAY / ASSET-DISCORD-GATEWAY-CONTRACT — extend with immediate response (with components), defer, follow-up, update message, open modal. ASSET-DISCORD-GATEWAY (JdaDiscordGateway) — handle Interaction Create; on receive ack or auto-defer within 3s; store token; publish Event with deferred: true and token. ASSET-DISCORD-SOURCE — provide DiscordAppReplySink; register with Bootstrap; may retain DiscordReplySender during migration. **New:** DiscordAppReplySink — implements connector contract; all lifecycle operations; render intents to Discord components; fall back to text; return typed Capabilities; Discord V2 as one rendering mode.

**Doc excerpts (discord):**
- **Decisions:** Preserve Discord mentions in connector events; mention metadata on payload; reply delivery through connector abstraction.
- **Contracts:** EventSource.start(EventBus); payload has content, channelId, authorId, mentions. DiscordReplySender send(channelId, messageId, content).
- **Known issues:** (None.)

---

### Routing (REQ-BOT-001) — NormalizedEventContext

**Assets:** ASSET-NORMALIZED-EVENT-CONTEXT. Optionally normalize interaction payload for routing/display (e.g. kind: interaction with interactionId, token, customId, values).

---

### Bootstrap (FEAT-CORE)

**Assets:** ASSET-BOOTSTRAP. Register Discord sink for discord source (transitional prefix). Centralize sink resolution so it can be replaced with explicit surface model later.

---

## Schema constraints

- **core-registry / connectors-registry:** Use existing req-registry schema. Add new requirements and assets with existing keys (id, title, statement, status, priority, type, acceptance.criteria, traceability.assets, validation.tests). Do not invent new top-level or requirement/asset keys without schema update (guardrails).
- **Workflow step keys:** If adding intent, choices, confirmLabel, cancelLabel, fields to step config, stay within existing schema rules for workflow definition (no new keys outside allowed set without schema change).

---

## Implementation constraints (from plan and guardrails)

- **Defer:** Never in engine. Adapter-only: on interaction receive, adapter acks or defers within platform window; engine only calls respondImmediately, sendFollowUp, updateMessage, openModal when it has content.
- **Package placement:** Response/intent model and connector contract interface in `com.vinekeepers.interactions`; DiscordAppReplySink implementation in `com.vinekeepers.connectors`.
- **Event kind:** Support `kind: "interaction"` in payload (interactionId, token, interactionKind, customId, values, channelId, messageId, authorId, optional deferred).
- **Backward compatibility:** Existing string reply path remains; richReply optional; when absent engine uses string as text-only OutboundResponse.
- **Guardrails:** Do not delete requirements; do not remove required functionality; do not change schema or invent new spec keys; repair drift before coding; avoid anti_patterns on requirements/assets; feature slugs are not bot ids.

---

## Doc update targets (Phase 1E)

- **mkdoc/architecture.md:** Engine uses connector contract with distinct lifecycle operations; reply path = OutboundResponse → sink lifecycle method; Discord adapter owns timing and may auto-defer; transitional source-prefix routing; rich interactions subsection.
- **mkdoc/features/domain/connectors/discord.md:** Lifecycle operations, timing responsibility (defer adapter-owned only), auto-defer, Discord V2 as one rendering mode, intents, fallback.
- **mkdoc/features/domain/workflow/** (workflow.md, workflow-steps.md): Intent-based steps, capture from interaction vs message.
- **README.md:** Rich interactions, configurable intents, link to docs.
