# Change context (for plan_change / implement)

## Scope

**Request:** Implement Reply Target Resolver Completion — remove `fallbackReplyTarget` from VinekeepersEngine; make ReplyTargetResolver the single source of truth; fail closed when no resolver or resolver returns empty; add DiscordReplyTargetResolverTest; remove/rewrite `whenNoResolverRegisteredFallbackUsesPayloadForTarget`; update specs/docs.

**Impacted registry specs:** `specs/core-registry.yml`, `specs/connectors-registry.yml`

**Impacted assets:** ASSET-ENGINE (VinekeepersEngine), ASSET-BOOTSTRAP (Bootstrap), ASSET-REPLY-TARGET-RESOLVER, ASSET-DISCORD-REPLY-TARGET-RESOLVER. **New:** DiscordReplyTargetResolverTest. **Modified tests:** VinekeepersEngineTest (remove/rewrite `whenNoResolverRegisteredFallbackUsesPayloadForTarget`).

**Requirements in scope:** REQ-CORE-003, REQ-CONNECTORS-DISCORD-001

**Features in scope:** FEAT-ENGINE (engine), FEAT-CONNECTORS-DISCORD (discord)

**Removal/rename:** true — remove `fallbackReplyTarget` method and its use; remove or rewrite test `whenNoResolverRegisteredFallbackUsesPayloadForTarget`.

---

## Per feature / requirement

### REQ-CORE-003 — Event-driven engine routes events to bots

- **Title:** Event-driven engine routes events to bots
- **Statement:** VinekeepersEngine receives events, routes them to matching bots, runs WorkflowRunner then reasoner, applies state/tool side effects, records audit, builds OutboundResponse, and delivers replies. Reply delivery uses reply senders and sinks by connector id; engine has no connector literal. **Update:** ReplyTarget is obtained only via ReplyTargetResolver registered by connector id; no fallback from event payload. When no resolver is registered for the event’s source prefix, or the resolver returns empty, the engine does not deliver the reply (fail closed).
- **Acceptance criteria (relevant):**
  - Engine uses ReplyTargetResolver registered by connector id to obtain ReplyTarget; **when no resolver is registered for the source prefix, or resolver returns Optional.empty(), engine does not deliver reply (fail closed).**
  - Engine registers reply senders and sinks by connector id; delivery uses sink when present else reply sender; no connector literal in engine.
  - Engine delivers via sink registry and lifecycle methods; no defer in engine.
- **Traceability (assets):** ASSET-ENGINE, ASSET-REPLY-TARGET-RESOLVER, ASSET-DISCORD-REPLY-TARGET-RESOLVER, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, etc.
- **Validation tests:** UNIT-ENGINE (VinekeepersEngineTest). **Change:** Remove or replace `whenNoResolverRegisteredFallbackUsesPayloadForTarget` with a test that asserts fail-closed behavior (e.g. whenNoResolverRegistered_doesNotDeliverReply; whenResolverReturnsEmpty_doesNotDeliverReply).
- **Anti-patterns (guardrails):** Do not delete requirements; do not change schema or invent new spec keys; repair spec drift before coding; avoid anti_patterns on requirements/assets.

### REQ-CONNECTORS-DISCORD-001 — Discord event source and reply

- **Statement:** Discord connector; ReplyTargetResolver registered per connector id; Bootstrap registers `engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver())`.
- **Validation tests:** Add **DiscordReplyTargetResolverTest** — verify DiscordReplyTargetResolver resolves Event to InteractionTarget or ChannelTarget from payload (channelId, messageId, interactionId, token, deferred); cover interaction vs message kinds and Optional.empty() when required fields missing if contract is strengthened.
- **Traceability:** ASSET-REPLY-TARGET-RESOLVER, ASSET-DISCORD-REPLY-TARGET-RESOLVER.

### Assets (implementation targets)

| Asset ID | Path | Role / change |
|----------|------|----------------|
| ASSET-ENGINE | src/main/java/com/vinekeepers/core/VinekeepersEngine.java | Remove `fallbackReplyTarget(Event)`; obtain ReplyTarget only from `resolversByConnectorId.get(sourcePrefix)`; if resolver is null or `resolver.resolve(event)` is empty, do not deliver (return early). Single source of truth: ReplyTargetResolver. |
| ASSET-BOOTSTRAP | src/main/java/com/vinekeepers/core/Bootstrap.java | No change required; already registers `engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver())`. |
| ASSET-REPLY-TARGET-RESOLVER | src/main/java/com/vinekeepers/connectors/ReplyTargetResolver.java | Interface unchanged; contract: resolve(Event) → Optional&lt;ReplyTarget&gt;. |
| ASSET-DISCORD-REPLY-TARGET-RESOLVER | src/main/java/com/vinekeepers/connectors/DiscordReplyTargetResolver.java | No change required unless adding Optional.empty() for invalid payloads; implement step may add tests only. |
| (new) | src/test/java/com/vinekeepers/connectors/DiscordReplyTargetResolverTest.java | Add unit tests: resolve returns InteractionTarget for kind=interaction with interactionId/token; returns ChannelTarget for message; payload field mapping. |
| VinekeepersEngineTest | src/test/java/com/vinekeepers/core/VinekeepersEngineTest.java | Remove `whenNoResolverRegisteredFallbackUsesPayloadForTarget`. Add test(s): when no resolver registered for source prefix, reply is not delivered (sent channel/messageId remain null); when resolver returns empty, reply is not delivered. |

---

## Doc excerpts

### Engine (mkdoc/features/domain/core/engine.md)

- **Summary (update):** Drop “when no resolver is registered uses a fallback target from the event”. Replace with: obtains ReplyTarget via connector-owned ReplyTargetResolver by connector id; **when no resolver is registered for the source prefix or the resolver returns empty, the engine does not deliver the reply (fail closed).**
- **Contracts (engine/contracts.md):** “Obtains **ReplyTarget** via **ReplyTargetResolver** registered by connector id; **when no resolver is registered** [remove: uses a fallback target from the event] **does not deliver reply (fail closed)**; when resolver returns empty, does not deliver (fail closed). Reply-sender fallback delivery uses target.channelId() and target.messageId().”

### Discord (mkdoc/features/domain/connectors/discord.md)

- **Summary:** Already states engine obtains ReplyTarget via connector-owned resolver by connector id; Bootstrap registers Discord ReplyTargetResolver. Ensure no “fallback” wording for reply target in engine.

### Decisions / Known issues

- **engine/decisions.md:** No change required for resolver completion.
- **engine/known-issues.md:** Optional: note that reply is not sent when resolver is missing or returns empty (fail closed).

---

## Schema constraints

- **req-registry:** Use existing keys only (id, title, statement, status, acceptance.criteria, traceability, validation.tests). Do not add new top-level or requirement keys.
- **Guardrails:** Do not delete requirements; do not remove required functionality without permission; do not change schema or invent new keys; repair spec drift first; consider anti_patterns on requirements/assets.

---

## Implementation checklist (for implement step)

1. **VinekeepersEngine.java:** Remove call to `fallbackReplyTarget(event)`. Replace logic: `ReplyTarget target = resolver != null ? resolver.resolve(event).orElse(null) : null;` then `if (target == null) return;` (no fallback). Delete method `fallbackReplyTarget(Event)` entirely.
2. **VinekeepersEngineTest.java:** Remove test `whenNoResolverRegisteredFallbackUsesPayloadForTarget`. Add one or two tests: (a) when no resolver registered for event’s source prefix, reply is not delivered (e.g. send not invoked or sentChannel/sentMessageId stay null); (b) when resolver returns Optional.empty(), reply is not delivered.
3. **DiscordReplyTargetResolverTest.java:** New class. Test DiscordReplyTargetResolver: message event with channelId/messageId → ChannelTarget; interaction event with interactionId, token, deferred → InteractionTarget; optionally empty payload or missing fields → Optional.empty() if contract is to return empty.
4. **specs/core-registry.yml:** In REQ-CORE-003 acceptance criteria, replace “Engine uses ReplyTargetResolver registered by connector id when present to obtain ReplyTarget; otherwise fallback for backward compatibility” with “Engine uses ReplyTargetResolver registered by connector id to obtain ReplyTarget; when no resolver is registered for the source prefix or resolver returns empty, engine does not deliver reply (fail closed).” Update ASSET-ENGINE role text to drop “fallback to reply-sender registry” for target resolution (target comes only from resolver).
5. **specs/connectors-registry.yml:** Add validation test entry for DiscordReplyTargetResolverTest if not present (e.g. UNIT-DISCORD-REPLY-TARGET-RESOLVER).
6. **mkdoc:** Update engine.md summary and engine/contracts.md as in Doc excerpts above. Optionally update known-issues.md.
7. Run: `npm run validate-specs`, `npm run validate-drift`, `mvn test`, `mvn compile`, `npm run validate-docs`.
