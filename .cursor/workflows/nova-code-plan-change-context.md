# Change context (for plan_change / implement)

## Scope

**Request:** Reply target resolution refactor — connector-owned ReplyTargetResolver; move event-to-ReplyTarget resolution out of VinekeepersEngine; add DiscordReplyTargetResolver; engine uses resolver by connector id, no longer reads Discord payload; fallback uses target.channelId()/messageId(); Bootstrap registers Discord resolver; preserve Discord and lifecycle behavior.

**Impacted registry slice:** core (engine, bootstrap), connectors (Discord).

**Features:** FEAT-ENGINE, FEAT-CORE (Bootstrap), FEAT-CONNECTORS-DISCORD.

**Requirements:** REQ-CORE-002, REQ-CORE-003, REQ-CONNECTORS-DISCORD-001.

**Assets:** ASSET-ENGINE, ASSET-BOOTSTRAP, ASSET-REPLY-TARGET; new: ReplyTargetResolver (interface), DiscordReplyTargetResolver (connector implementation). Connectors registry will gain a new asset for the Discord resolver; core registry may gain ReplyTargetResolver contract asset or it lives under connectors.

---

## Per feature

### Feature: engine (FEAT-ENGINE)

- **Title:** Event-driven engine orchestration  
- **Status:** active  
- **doc_path:** features/domain/core/engine.md  
- **Summary:** VinekeepersEngine routes events to matched bots, runs workflow runners, applies reasoner output, and emits replies.

**Requirements in scope**

- **REQ-CORE-003** — Event-driven engine routes events to bots  
  - **Statement:** VinekeepersEngine receives events, routes them to matching bots, runs WorkflowRunner, then reasoner; reply delivery uses reply senders and sinks registered by connector id (source prefix); engine has no connector-specific literal. Delivery uses sink for event's source prefix when registered, else fallback to reply sender. Engine builds OutboundResponse, **resolves ReplyTarget from event**.  
  - **Acceptance (relevant):** Engine registers reply senders and sinks by connector id; delivery uses sink when present else fallback to reply sender; no connector literal in engine; lifecycle (respondImmediately, sendFollowUp, updateMessage).  
  - **Traceability assets:** ASSET-ENGINE, ASSET-REPLY-TARGET, ASSET-APP-REPLY-SINK, ASSET-REPLY-SENDER, etc.  
  - **Validation tests:** UNIT-ENGINE (VinekeepersEngineTest).  
  - **Refactor note:** Spec text "resolves ReplyTarget from event" becomes "obtains ReplyTarget via connector-owned ReplyTargetResolver by connector id; fallback uses target.channelId()/messageId() for legacy sender path."

**Assets in scope**

- **ASSET-ENGINE** — path: `src/main/java/com/vinekeepers/core/VinekeepersEngine.java`  
  - **Role:** Route events to bots; run workflow and reasoner; deliver replies via sink registry then fallback reply-sender by connector id; build OutboundResponse; **obtain ReplyTarget via resolver by connector id (no Discord payload reads);** lifecycle (respondImmediately, sendFollowUp, updateMessage).  
  - **Change:** Remove in-engine `resolveReplyTarget(Event)` that reads payload (channelId, messageId, interactionId, token, deferred). Add registry of ReplyTargetResolver by connector id; in handleEventForBot get source prefix, look up resolver, call resolver.resolve(event) when present; else build a minimal target from event for backward compatibility. In deliverReply fallback (when using ReplySender), use target.channelId() and target.messageId() instead of reading event payload again.

- **ASSET-REPLY-TARGET** — path: `src/main/java/com/vinekeepers/interactions/ReplyTarget.java`  
  - **Role:** Sealed ReplyTarget (ChannelTarget, InteractionTarget) for where to send replies.  
  - **Change:** No API change; engine and resolver both use this type.

**Doc excerpts**

- **engine/how-it-works:** Engine receives event, routing, workflow, reasoner, audit; reply delivery uses connector-keyed reply sender map; when no sink, lookup by source prefix; no connector-specific literals.  
- **engine/decisions:** Waiting-session routing for Discord; workflow before reasoner; tool calls via ToolRunner; engine orchestration separate from bootstrap.  
- **engine/contracts:** VinekeepersEngine subscribes and coordinates; WorkflowRunner, ToolRunner, Reasoner contracts; ReasonerInput/Output, WorkflowRunResult schemas.

---

### Feature: core bootstrap (FEAT-CORE)

- **Title:** Specs governance and bootstrap  
- **Status:** active  
- **doc_path:** features/domain/core/core.md  

**Requirements in scope**

- **REQ-CORE-002** — Application bootstrap and entrypoint  
  - **Statement:** VinekeepersApp loads .env and bootstraps; Bootstrap wires engine, config, ConnectorRegistry; creates Discord adapter; **Bootstrap registers engine reply sender and sink by connector id**; engine/sink/action registration remain in Bootstrap.  
  - **Acceptance:** Bootstrap registers engine reply sender and sink (e.g. setReplySender("discord", ...), registerSink("discord", ...)); creates WorkflowRunner per bot; registers shared tools.  
  - **Validation tests:** UNIT-VINEKEEPERS-APP, UNIT-BOOTSTRAP.

**Assets in scope**

- **ASSET-BOOTSTRAP** — path: `src/main/java/com/vinekeepers/core/Bootstrap.java`  
  - **Role:** Wire engine, config, ConnectorRegistry; build Discord adapter; register engine reply sender and sink by connector id; action and sink registration stay in Bootstrap.  
  - **Change:** After registering Discord sink and reply sender, **register Discord ReplyTargetResolver:** e.g. `engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver())` (or equivalent factory). Preserve existing Discord and lifecycle behavior.

---

### Feature: Discord connector (FEAT-CONNECTORS-DISCORD)

- **Title:** Discord event source and reply  
- **Status:** active  
- **doc_path:** features/domain/connectors/discord.md  

**Requirements in scope**

- **REQ-CONNECTORS-DISCORD-001** — Discord event source and reply  
  - **Statement:** Discord event source and reply; ReplySender and OutboundGateway; per-bot identity; ConnectorContext generic; Bootstrap registers engine reply sender and sink by connector id; OutboundDeliveryRouter implements ReplySender; DiscordAppReplySink with lifecycle operations; **event payload includes channelId, messageId, interactionId, token, etc.**  
  - **Acceptance (relevant):** DiscordEventSource passes author and mention metadata; DiscordAppReplySink implements AppReplySink and lifecycle; replies delivered via sink or legacy reply path when source is Discord.  
  - **Traceability assets:** ASSET-DISCORD-REPLY-SINK, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-DISCORD-SOURCE, etc.  
  - **Validation tests:** UNIT-ENGINE-DISCORD-REPLY (lunaWorkflowWithReplySenderSendsReplyToDiscord), UNIT-DISCORD-APP-REPLY-SINK, UNIT-DISCORD-EVENT-SOURCE, etc.

**Assets to add (connectors)**

- **ReplyTargetResolver** (interface) — path TBD: e.g. `src/main/java/com/vinekeepers/connectors/ReplyTargetResolver.java`  
  - **Role:** Contract: given Event, return ReplyTarget. Connector-owned; engine uses by connector id.  
  - **Requires:** REQ-CORE-003, REQ-CONNECTORS-DISCORD-001 (when implemented by Discord).

- **DiscordReplyTargetResolver** — path TBD: e.g. `src/main/java/com/vinekeepers/connectors/DiscordReplyTargetResolver.java`  
  - **Role:** Implements ReplyTargetResolver; reads event payload (channelId, messageId, interactionId, token, deferred) and returns InteractionTarget or ChannelTarget consistent with current engine logic.  
  - **Requires:** REQ-CONNECTORS-DISCORD-001.

**Doc excerpts**

- **discord/decisions:** Generic ReplySender and OutboundGateway; core stays connector-agnostic; Discord implements both; DiscordAppReplySink casts to DiscordGateway for interaction methods. Preserve Discord mentions in connector events.  
- **discord/contracts:** Event payload includes channelId, messageId, content, authorId, mentions. Reply delivery uses channelId, messageId, content. ReplyTargetResolver (new): resolve(Event) → ReplyTarget; Discord implementation reads Discord payload fields.

---

## Schema constraints

- Do not add new spec schema keys; stay within existing req-registry schema (requirements, assets, features, traceability, validation.tests).  
- When adding new assets (ReplyTargetResolver, DiscordReplyTargetResolver), add them to the appropriate registry (connectors-registry.yml) with correct requires and feature_ids.  
- Guardrails: no deletion of requirements; no connector literal in engine; repair spec drift if any; avoid anti_patterns on impacted assets (none listed in core/connectors for these assets).

---

## Implementation checklist (for implement step)

1. **Add ReplyTargetResolver interface** (connectors package): single method e.g. `ReplyTarget resolve(Event event)`.
2. **Add DiscordReplyTargetResolver** implementing it: same logic as current `VinekeepersEngine.resolveReplyTarget(Event)` (payload keys: channelId/channel, messageId/message_id, interactionId, token, deferred; kind "interaction" → InteractionTarget, else ChannelTarget).
3. **Engine:** Add `Map<String, ReplyTargetResolver> replyTargetResolversByConnectorId` and `registerReplyTargetResolver(connectorId, resolver)`. In handleEventForBot, compute source prefix from event.getSourceId(), look up resolver; if present use `resolver.resolve(event)`, else keep a small fallback that builds ChannelTarget from event payload so non-Discord or unregistered connectors still work. Remove the private `resolveReplyTarget(Event)` method. In deliverReply, when using ReplySender fallback, use `target.channelId()` and `target.messageId()` instead of event.getPayload(...).
4. **Bootstrap:** In withDiscord(), after registerSink, call `engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver())`.
5. **Specs:** Update core-registry ASSET-ENGINE role text to say engine obtains ReplyTarget via resolver by connector id and fallback uses target.channelId()/messageId(). Add ASSET-REPLY-TARGET-RESOLVER and ASSET-DISCORD-REPLY-TARGET-RESOLVER to connectors-registry with requires and feature_ids; update REQ-CORE-003 and REQ-CONNECTORS-DISCORD-001 acceptance/traceability if needed. Run validate-specs and validate-drift.
6. **Docs:** Update mkdoc engine and discord feature pages (summary/contracts) to mention ReplyTargetResolver and DiscordReplyTargetResolver; run validate-docs.
7. **Tests:** mvn test (VinekeepersEngineTest, DiscordAppReplySinkTest, DiscordEventSourceTest) and mvn compile.
