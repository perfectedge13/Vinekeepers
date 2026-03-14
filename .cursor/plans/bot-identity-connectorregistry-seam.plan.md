# Bot identity config generalization + ConnectorRegistry introduction (revised)

**Scope:** Bot identity config generalization + ConnectorRegistry introduction. No new connectors, no deliveryChannelId/lifecycle redesign, no Router or workflow config redesign.

**Goal:** Make the core runtime less Discord-shaped by moving bot identity into connector-scoped config and introducing a ConnectorRegistry seam, while preserving all current Discord behavior.

---

## 1. Current-state assessment

*(Unchanged from original plan: BotDefinition has discordTokenEnvKey and handlesOwnedSpaces; ConfigLoader parses top-level keys; BotConfig has defaultDiscordTokenEnvKey; Bootstrap.withDiscord() does all Discord wiring; OutboundDeliveryRouter is botId-keyed; Router uses handlesOwnedSpacesByBotId map. See prior plan for full table.)*

---

## 2. Recommended design decisions

### 2.1 Bot identity config generalization

**A. Recommended new config/model shape**

- **Preferred YAML shape:** Per-bot, optional `identities` map keyed by connector id. For Discord: `identities.discord.tokenEnvKey`, `identities.discord.handlesOwnedSpaces`.
- **Root-level default:** `defaultDiscordTokenEnvKey` remains a **retained transitional root-level setting** for this pass — not the long-term preferred identity model. Document it explicitly as such: when a bot has no `identities.discord.tokenEnvKey`, this root-level key is used as the fallback for the Discord connector only. A future pass could move to a connector-scoped default (e.g. under a connector config block) if desired.

**B. Fields under connector identities**

- **Discord:** `tokenEnvKey`, `handlesOwnedSpaces`. Both live under `identities.discord`.
- **handlesOwnedSpaces semantics (docs):** In docs/specs, state explicitly that **handlesOwnedSpaces is connector-scoped ownership behavior for Discord in this pass**, not a generic bot-wide property. It applies only to Discord lifecycle channels and single-owner precedence; other connectors may have different or no equivalent.

**C. What remains top-level on BotDefinition**

- id, persona, modelProfile, toolPolicy, memoryPolicy, workflowType, workflowParams, conversationMode, sessionKeyStrategy. No Discord-named fields at top level.

**D. Backward compatibility**

- Dual-read, normalize internally. Legacy top-level `discordTokenEnvKey` / `handlesOwnedSpaces` normalized into connector identity; new shape preferred; both supported with new winning if both present. config/bots.yaml migrated to new shape in this pass.

**E. ConnectorIdentity wrapper (refinement)**

- Use a **small ConnectorIdentity wrapper** instead of raw `Map<String, Map<String, Object>>` on BotDefinition. This keeps the model generic without spreading nested stringly-typed maps everywhere.
- **ConnectorIdentity:** Immutable value type (e.g. record or final class) that holds connector-specific attributes. Minimal API: e.g. `Map<String, Object> getAttributes()` or `Object getAttribute(String key)` so adapters can read known keys (e.g. `tokenEnvKey`, `handlesOwnedSpaces` for Discord) without the core depending on connector names. No Discord-specific subtype in this pass.
- **BotDefinition:** Holds `Map<String, ConnectorIdentity> connectorIdentities` (or equivalent); access via `Optional<ConnectorIdentity> getConnectorIdentity(String connectorId)`. ConfigLoader builds ConnectorIdentity instances from the raw YAML map for each connector id; legacy top-level keys are normalized into a ConnectorIdentity for `"discord"`.

---

### 2.2 ConnectorRegistry seam

**A.–B. Responsibilities and limits**

- Same as before: register connector by id; provide adapter to Bootstrap for “register your bots”; no generic event abstraction, no plugin loader, no capability discovery.

**C. How Bootstrap changes**

- Creates ConnectorRegistry; builds **Discord adapter with Discord-specific config** (see below); registers adapter under `"discord"`. Builds handlesMap from `bot.getConnectorIdentity("discord").map(d -> (Boolean) d.getAttribute("handlesOwnedSpaces")).orElse(false)` (or equivalent on ConnectorIdentity). Calls `adapter.registerBots(lastLoadedBots, context)`.
- **Action/sink registration stays in Bootstrap.** The Discord adapter **focuses only on per-bot gateway/sender/default registration** (registerSender, setDefaultSender, setDefaultGateway, start event sources). Bootstrap retains responsibility for broader application wiring: `engine.setReplySender(outboundDeliveryRouter)`, `engine.registerSink("discord", new DiscordAppReplySink(outboundDeliveryRouter))`, `cursorCloudRunMonitor.setReplySender(outboundDeliveryRouter)`, and `actionRegistry.register("create_channel", ...)` / `actionRegistry.register("create_thread", ...)` when a default gateway is available. This keeps the adapter narrow and avoids it becoming responsible for engine/sink/action wiring.

**D. ConnectorContext generic (refinement)**

- **Keep ConnectorContext generic.** Do not put `defaultDiscordTokenEnvKey` (or other Discord-specific options) directly into ConnectorContext. ConnectorContext should hold only connector-agnostic dependencies: e.g. `Env`, `EventBus`, `OutboundDeliveryRouter` (the interface used is still Discord-typed today, but the *context* object does not name Discord).
- **Discord-specific options:** Pass them into the **Discord adapter constructor** or a **small Discord-specific config object** that Bootstrap builds from config (e.g. `DiscordConnectorConfig` with `defaultTokenEnvKey`) and passes when constructing the Discord adapter. The adapter is then registered with the registry; when `registerBots(bots, context)` is called, the adapter already has the default token key and any other Discord-only settings from its constructor/config.

**E. ConnectorAdapter and registry**

- `ConnectorAdapter`: `void registerBots(List<BotDefinition> bots, ConnectorContext context);` — context is generic. ConnectorRegistry: `register(connectorId, adapter)`, `get(connectorId)`.

---

## 3. Migration strategy

- Unchanged: dual-read, new-write, normalize internally; config/bots.yaml migrated; legacy keys deprecated; docs present new shape as preferred.
- **defaultDiscordTokenEnvKey:** Document as a **retained transitional root-level setting**, not the long-term preferred identity model. Preferred model is per-bot `identities.discord.tokenEnvKey`; the root-level default exists only to support existing setups and will not be extended as the generic pattern for other connectors.

---

## 4. Behavior unchanged

- Unchanged: same Discord identities, per-bot gateways, owned-space precedence, lifecycle/thread flows, routing, config-driven workflows. Refactor is structural only.

---

## 5. Current vs target data structures

**Target BotDefinition:**

- Holds `Map<String, ConnectorIdentity> connectorIdentities`. Access: `getConnectorIdentity("discord")` → `Optional<ConnectorIdentity>`. ConnectorIdentity exposes attributes (e.g. `getAttribute(String key)` or `getAttributes()`) so Discord adapter reads `tokenEnvKey`, `handlesOwnedSpaces` without core referencing Discord.

**ConnectorContext:**

- Generic: Env, EventBus, OutboundDeliveryRouter (and any other shared runtime deps). No `defaultDiscordTokenEnvKey` or Discord-named fields.

**Discord adapter:**

- Constructed with a small **Discord-specific config** (e.g. `defaultTokenEnvKey` from Bootstrap reading `config.getDefaultDiscordTokenEnvKey()`). Implements `registerBots(bots, context)`; uses context for OutboundDeliveryRouter and EventBus, uses its own config for default token key and any other Discord-only options.

**Bootstrap:**

- Builds `DiscordConnectorConfig` (or equivalent) from config; creates Discord adapter with that config; registers adapter. After adapter.registerBots(), Bootstrap performs action/sink registration and engine/cursor wiring as today.

---

## 6. Intentionally deferred

- Unchanged: no new connectors, no generic event abstraction, no Router/workflow/deliveryChannelId redesign, no plugin loader, no multi-connector behavior.

---

## 7. Files to add or touch

| Area | File | Change |
|------|------|--------|
| Bot | New: `ConnectorIdentity.java` | Small immutable wrapper (e.g. record or class) with `getAttribute(key)` / `getAttributes()`; built from raw map in ConfigLoader. |
| Bot | [BotDefinition.java](src/main/java/com/vinekeepers/bot/BotDefinition.java) | Replace Discord fields with `Map<String, ConnectorIdentity>` (or equivalent); `getConnectorIdentity(connectorId)`. |
| Config | [ConfigLoader.java](src/main/java/com/vinekeepers/config/ConfigLoader.java) | Parse `identities.discord` and legacy keys; build ConnectorIdentity per connector; pass to BotDefinition. |
| Core/connectors | New: `ConnectorContext.java` | Generic: Env, EventBus, OutboundDeliveryRouter only. No Discord-specific fields. |
| Core/connectors | New: `ConnectorAdapter.java` | `void registerBots(List<BotDefinition> bots, ConnectorContext context);` |
| Core/connectors | New: `ConnectorRegistry.java` | register(id, adapter), get(id). |
| Connectors | New: `DiscordConnectorConfig.java` (or similar) | Holds defaultTokenEnvKey (and any other Discord-only options). |
| Connectors | New: `DiscordConnectorAdapter.java` | Implements ConnectorAdapter; takes DiscordConnectorConfig in constructor; in registerBots() does per-bot gateway/sender/default registration only. |
| Core | [Bootstrap.java](src/main/java/com/vinekeepers/core/Bootstrap.java) | Create ConnectorRegistry; build DiscordConnectorConfig from config; create Discord adapter with that config; register adapter; build handlesMap from getConnectorIdentity("discord"); call adapter.registerBots(bots, context); keep engine/sink/cursor/action registration in Bootstrap. |
| Config | [config/bots.yaml](config/bots.yaml) | Migrate to identities.discord. Retain defaultDiscordTokenEnvKey at root as transitional. |
| Specs/docs | bot-registry, config-registry, README, mkdoc | New identity shape preferred; legacy deprecated; **handlesOwnedSpaces documented as connector-scoped ownership for Discord in this pass, not a generic bot-wide property**; **defaultDiscordTokenEnvKey documented as retained transitional root-level setting, not long-term preferred identity model**; ConnectorRegistry and adapter responsibility; action/sink registration remains in Bootstrap. |

---

## 8. Test plan

- Unchanged: config/model (legacy, new, both, missing), BotDefinition/identity access (including via ConnectorIdentity), Bootstrap/registry, runtime regression, config/docs. Add tests that ConnectorContext has no Discord-named accessors and that Discord adapter receives Discord options via constructor/config.

---

## 9. Docs/specs (refinements)

- **Bot definition:** As before; identity is connector-scoped via ConnectorIdentity.
- **Connector identity:** As before; for Discord, tokenEnvKey and handlesOwnedSpaces.
- **handlesOwnedSpaces:** State explicitly: **handlesOwnedSpaces is connector-scoped ownership behavior for Discord in this pass, not a generic bot-wide property.** It controls single-owner precedence for Discord lifecycle channels only.
- **defaultDiscordTokenEnvKey:** Document as a **retained transitional root-level setting**, not the long-term preferred identity model. Preferred model is per-bot `identities.discord.tokenEnvKey`; the root default is for backward compatibility and transition only.
- **ConnectorRegistry / adapter:** As before; add that **action and sink registration stay in Bootstrap**; the Discord adapter is responsible only for per-bot gateway/sender/default registration.
- **Intentionally deferred:** Unchanged.

---

## 10. Success criteria

- All existing Discord behavior preserved.
- BotDefinition exposes identity only via ConnectorIdentity (no top-level Discord fields in preferred API).
- ConnectorIdentity wrapper used; no raw Map<String, Map<String, Object>> spread for identity.
- ConnectorContext is generic (no defaultDiscordTokenEnvKey or Discord-named fields); Discord-specific options passed via adapter constructor or Discord-specific config object.
- Action/sink registration remains in Bootstrap; Discord adapter only registers bots with OutboundDeliveryRouter and starts sources.
- Docs state handlesOwnedSpaces as connector-scoped for Discord and defaultDiscordTokenEnvKey as transitional root-level.
