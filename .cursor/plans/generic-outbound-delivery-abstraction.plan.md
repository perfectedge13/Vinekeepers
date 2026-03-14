# Generic outbound delivery abstraction — plan only (revised)

**Scope:** Generic outbound delivery abstraction.  
**Goal:** Make the core runtime less connector-specific by finishing the migration from DiscordReplySender to the existing ReplySender contract and introducing a small transitional OutboundGateway abstraction, while preserving all current Discord behavior.

**Assumption:** ReplySender already exists in the codebase as the generic sender contract. This pass does not add ReplySender; it completes the migration of core-facing code to use ReplySender and adds only OutboundGateway as the new abstraction.

---

## 1. Current-state assessment

**OutboundDeliveryRouter** ([OutboundDeliveryRouter.java](src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java))
- Stores `Map<String, DiscordReplySender> botIdToSender`, `Map<String, DiscordGateway> botIdToGateway`; `DiscordReplySender defaultSender`; `DiscordGateway defaultGateway`.
- `registerSender(botId, DiscordReplySender, DiscordGateway)`, `setDefaultSender(DiscordReplySender)`, `setDefaultGateway(DiscordGateway)`.
- Returns `DiscordGateway` from `getGatewayForChannel(channelId)` and `getDefaultGateway()`.
- `getDiscordUserIdForBot(botId)` returns that bot gateway’s `getSelfUserId()` (used by CreateChannelAction for permission override).
- Implements `DiscordReplySender`; delegates `send(channelId, messageId, content)` to the resolved sender.

**ReplySender (existing)**
- Treated as the existing generic sender contract (e.g. `void send(String channelId, String messageId, String content)`). Core-facing code should depend on ReplySender, not DiscordReplySender.

**DiscordReplySender**
- Currently used by OutboundDeliveryRouter, VinekeepersEngine, CursorCloudRunMonitor, PostChannelMessageAction, and tests. Should extend or align with ReplySender so that migration means switching declared types to ReplySender.

**DiscordGateway**
- Full interface with send, getSelfUserId, isConnected, createTextChannel, createThreadChannel, addPermissionOverride, plus interaction-specific methods (send with components, sendFollowUp, updateMessage, openModal, connect, shutdown). Core router and workflow actions need only the first set; DiscordAppReplySink needs the interaction-specific ones.

**DiscordAppReplySink**
- Resolves gateway via `router.getGatewayForChannel(target.channelId())` and calls `gw.send`, `gw.sendFollowUp`, `gw.updateMessage`, `gw.openModal`. **Intentionally relies on DiscordGateway-specific methods**; will cast from the generic gateway. This is explicit out-of-scope debt for this pass—no generic “interaction gateway” in this refactor.

**Other call sites**
- **VinekeepersEngine**: `DiscordReplySender replySender`; `setReplySender(DiscordReplySender)`. **Migrate to ReplySender.**
- **CursorCloudRunMonitor**: `DiscordReplySender replySender`; `setReplySender(DiscordReplySender)`. **Migrate to ReplySender.**
- **PostChannelMessageAction**: takes `DiscordReplySender`. **Migrate to ReplySender.**
- **CreateChannelAction** / **CreateThreadAction**: use `DiscordGateway` from router. **Migrate to OutboundGateway** (router returns OutboundGateway).
- **DiscordConnectorAdapter**: passes DiscordEventSource and JdaDiscordGateway to router; after refactor, same objects but router accepts ReplySender and OutboundGateway.
- **Bootstrap**: passes outboundDeliveryRouter everywhere; no API change except types.

---

## 2. Recommended design decisions

### ReplySender (existing contract)

- ReplySender is the **existing** generic sender contract used by the core. This pass **finishes the migration** from DiscordReplySender to ReplySender in all core-facing code.
- **Core-facing code that must use ReplySender after this pass:** VinekeepersEngine, CursorCloudRunMonitor, PostChannelMessageAction, OutboundDeliveryRouter (implements ReplySender; internal maps use ReplySender).
- DiscordReplySender may extend ReplySender (if not already) so that existing implementations (OutboundDeliveryRouter, DiscordEventSource) satisfy ReplySender without change. Call sites in engine, monitor, and PostChannelMessageAction then declare ReplySender instead of DiscordReplySender.

### OutboundGateway (new — small and transitional)

- **OutboundGateway is the only new abstraction in this pass.** It is a **small, transitional** generic gateway interface that covers current core needs (router + CreateChannel/CreateThread), **not** the final perfect connector-neutral model. Future passes can broaden or rename as needed.
- **Methods:** `void send(String channelId, String messageId, String content)`; `String getSelfUserId()`; `boolean isConnected()`; `String createTextChannel(String guildId, String channelName)`; `String createThreadChannel(String parentChannelId, String threadName)`; `boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny)`.
- **DiscordGateway** extends OutboundGateway and keeps all existing default methods (send with components, sendFollowUp, updateMessage, openModal, connect, shutdown). JdaDiscordGateway continues to implement DiscordGateway.

### DiscordAppReplySink — intentional debt

- **DiscordAppReplySink will intentionally continue to rely on DiscordGateway-specific methods** (sendFollowUp, updateMessage, openModal, send with components). It will obtain the gateway from the router and **cast to DiscordGateway** where it needs those methods. This is **intentional out-of-scope debt** for this pass: no generic interaction gateway, and the sink remains Discord-specific. Document this clearly in the sink and in the plan.

### Rename: getDiscordUserIdForBot → getSelfUserIdForBot

- Rename on OutboundDeliveryRouter to **getSelfUserIdForBot(String botId)** so the router is not Discord-named. Behavior unchanged: delegate to the bot’s gateway’s getSelfUserId().

---

## 3. Revised implementation plan

**Step 1: Use existing ReplySender as the contract; align DiscordReplySender.**
- ReplySender already exists as the generic sender contract. Ensure DiscordReplySender extends ReplySender so that existing implementations (OutboundDeliveryRouter, DiscordEventSource) satisfy ReplySender. This pass migrates core-facing call sites to declare and use ReplySender instead of DiscordReplySender.

**Step 2: Add OutboundGateway (new).**
- Create `OutboundGateway` with the six methods above. Use same signatures as DiscordGateway for those methods so DiscordGateway can extend without adaptation. Frame in javadoc as a small transitional interface for current core needs.

**Step 3: DiscordGateway extends OutboundGateway.**
- Change `DiscordGateway` to extend `OutboundGateway`; ensure the six methods are inherited (declare in OutboundGateway with appropriate defaults if needed). All other DiscordGateway methods unchanged.

**Step 4: OutboundDeliveryRouter to generic types.**
- Internal maps and fields: `Map<String, ReplySender>`, `Map<String, OutboundGateway>`; `ReplySender defaultSender`; `OutboundGateway defaultGateway`.
- Signatures: `registerSender(String botId, ReplySender sender, OutboundGateway gateway)`, `setDefaultSender(ReplySender)`, `setDefaultGateway(OutboundGateway)`, `getGatewayForChannel(String) -> OutboundGateway`, `getDefaultGateway() -> OutboundGateway`.
- **Rename** `getDiscordUserIdForBot` → **getSelfUserIdForBot**; implementation unchanged.
- OutboundDeliveryRouter **implements ReplySender** (so it remains the object passed to engine and monitor).

**Step 5: Migrate VinekeepersEngine, CursorCloudRunMonitor, PostChannelMessageAction to ReplySender.**
- **VinekeepersEngine:** Field and setter type `ReplySender` (replace DiscordReplySender).
- **CursorCloudRunMonitor:** Field and setter type `ReplySender`.
- **PostChannelMessageAction:** Constructor and field type `ReplySender`.

**Step 6: CreateChannelAction and CreateThreadAction to OutboundGateway.**
- Use `OutboundGateway` from router: `getDefaultGateway()` and `getGatewayForChannel(...)` return OutboundGateway; call createTextChannel, createThreadChannel, addPermissionOverride, isConnected on that type. Use `router.getSelfUserIdForBot(botId)` (renamed).

**Step 7: DiscordAppReplySink — cast to DiscordGateway.**
- Keep constructor taking `OutboundDeliveryRouter`. In `gatewayFor(ReplyTarget)` continue to call `router.getGatewayForChannel(target.channelId())`. **Cast the result to DiscordGateway** at call sites that use sendFollowUp, updateMessage, openModal, or send with components. Add a short comment that the sink intentionally relies on DiscordGateway-specific methods and that this is accepted debt for this pass.

**Step 8: DiscordConnectorAdapter and Bootstrap.**
- Adapter: no change to what it passes (DiscordEventSource, JdaDiscordGateway); router parameter types become ReplySender and OutboundGateway, so adapter code compiles as-is.
- Bootstrap: no behavioral change; only ensure all call sites compile with new types.

**Lifecycle and default behavior:** Unchanged. Resolution by delivery target id → lifecycle context → configuredBotId → that bot’s sender/gateway; no context → default. Thread creation and create_channel semantics unchanged. Rename only: getSelfUserIdForBot.

---

## 4. Files to add/touch

**New files**
- `src/main/java/com/vinekeepers/connectors/OutboundGateway.java` — new interface (small, transitional). This is the only new type in this pass.

**Modified files**
- [OutboundDeliveryRouter.java](src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java) — generic maps/signatures (ReplySender, OutboundGateway); implement ReplySender; rename getDiscordUserIdForBot → getSelfUserIdForBot.
- [DiscordReplySender.java](src/main/java/com/vinekeepers/connectors/DiscordReplySender.java) — extend existing ReplySender so Discord implementations satisfy the generic contract.
- [DiscordGateway.java](src/main/java/com/vinekeepers/connectors/DiscordGateway.java) — extend OutboundGateway.
- [JdaDiscordGateway.java](src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java) — no change to class declaration (implements DiscordGateway, which extends OutboundGateway).
- [DiscordAppReplySink.java](src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java) — cast from router’s gateway to DiscordGateway where needed; add comment on intentional DiscordGateway dependency.
- [VinekeepersEngine.java](src/main/java/com/vinekeepers/core/VinekeepersEngine.java) — ReplySender for replySender field and setReplySender.
- [CursorCloudRunMonitor.java](src/main/java/com/vinekeepers/core/cursor/CursorCloudRunMonitor.java) — ReplySender for replySender and setReplySender.
- [PostChannelMessageAction.java](src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java) — ReplySender for constructor and field.
- [CreateChannelAction.java](src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java) — use OutboundGateway from router; use getSelfUserIdForBot.
- [CreateThreadAction.java](src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java) — use OutboundGateway from router.
- [Bootstrap.java](src/main/java/com/vinekeepers/core/Bootstrap.java) — no signature changes; ensure getDefaultGateway() usage compiles.

**Tests**
- OutboundDeliveryRouterTest — use ReplySender in RecordingSender (or DiscordReplySender if it extends ReplySender); stub gateway implements OutboundGateway/DiscordGateway; assert getSelfUserIdForBot.
- VinekeepersEngineTest, CursorCloudRunMonitorTest, PostChannelMessageActionTest — use ReplySender in setReplySender and mocks.
- CreateChannelActionTest, CreateThreadActionTest — stubs can remain DiscordGateway (extends OutboundGateway).
- DiscordAppReplySinkTest — unchanged; gateway stubs still implement DiscordGateway.

**Docs/specs**
- README or mkdoc: outbound delivery uses ReplySender (existing) and OutboundGateway (transitional); Discord implements both; sink intentionally uses DiscordGateway where needed.
- Specs: update router/engine asset text to ReplySender/OutboundGateway; note getSelfUserIdForBot.

---

## 5. Test plan

- **OutboundDeliveryRouter:** Store and resolve ReplySender/OutboundGateway; default sender/gateway; lifecycle-context resolution; thread-target delivery; no fallback when lifecycle bot has no sender; **getSelfUserIdForBot** returns gateway.getSelfUserId() for that bot.
- **VinekeepersEngine, CursorCloudRunMonitor, PostChannelMessageAction:** Accept ReplySender; existing tests pass with ReplySender-typed mocks.
- **CreateChannelAction / CreateThreadAction:** Use OutboundGateway from router; createTextChannel, createThreadChannel, addPermissionOverride, getSelfUserIdForBot behavior unchanged.
- **DiscordAppReplySink:** Resolve gateway via router; cast to DiscordGateway for follow-up/update; lifecycle and thread delivery unchanged; regression tests pass.
- **Regression:** All existing tests pass after type and rename changes.

---

## 6. Tradeoffs / intentionally deferred work

- **DiscordAppReplySink** continues to depend on DiscordGateway for sendFollowUp, updateMessage, openModal, send with components. Cast from generic gateway is **intentional out-of-scope debt**; no generic interaction gateway in this pass.
- **OutboundGateway** is explicitly a **small, transitional** interface for current core needs, not the final connector-neutral model; naming (e.g. guildId, channelId) remains Discord-ish where convenient.
- **Router/inbound flow:** No change. Lifecycle and deliveryChannelId semantics unchanged.

---

## 7. Success criteria

- ReplySender is the sender contract used by VinekeepersEngine, CursorCloudRunMonitor, PostChannelMessageAction, and OutboundDeliveryRouter; no core-facing code depends on DiscordReplySender for the send contract.
- OutboundGateway exists; DiscordGateway extends it; OutboundDeliveryRouter stores and returns OutboundGateway; getSelfUserIdForBot replaces getDiscordUserIdForBot.
- DiscordAppReplySink documents and uses a cast to DiscordGateway where it needs interaction-specific methods; behavior unchanged.
- All current Discord behavior preserved (per-bot sender/gateway, default sender/gateway, lifecycle/thread resolution, owned-space, create_channel/create_thread/post_channel_message, sink delivery).
- All existing tests pass.
