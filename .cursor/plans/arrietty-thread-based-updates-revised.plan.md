# Arrietty thread-based updates (revised plan)

## Current state assessment based on the actual code

### LifecycleContextStore ([LifecycleContextStore.java](src/main/java/com/vinekeepers/state/LifecycleContextStore.java))

- Indexes contexts by: `contextId` (byId), `channelId` (channelToContextId), `externalRunId` (externalRunIdToContextId).
- `put(LifecycleContext)` registers only `context.getChannelId()` and `context.getExternalRunId()` in the index maps. There is no index by thread id or any other delivery target.
- Lookup is exclusively `getByChannelId(channelId)` or `getByContextId` / `getByExternalRunId`. There is no way to resolve a context from a thread id.

### OutboundDeliveryRouter ([OutboundDeliveryRouter.java](src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java))

- `send(channelId, messageId, content)`: uses `lifecycleContextStore.getByChannelId(channelId)` to find context, then resolves `configuredBotId` and the bot’s sender, then calls `sender.send(channelId, messageId, content)`. The same `channelId` is used as both the **lookup key** and the **send target**.
- `getGatewayForChannel(channelId)`: same lookup — `getByChannelId(channelId)` only. Used by `DiscordAppReplySink.gatewayFor(target)` with `target.channelId()`.
- **Consequence**: If a workflow or the monitor calls `send(threadId, null, content)`, the router does `getByChannelId(threadId)`. No context has `channelId == threadId` (the room’s channelId is the parent channel). Lookup fails, router falls back to `defaultSender` (e.g. Luna’s sender). So updates intended for Arrietty’s room would go to the wrong bot or wrong channel. “Just store updatesThreadId and send to it” is therefore **not sufficient** in this codebase.

### LifecycleContext ([LifecycleContext.java](src/main/java/com/vinekeepers/state/LifecycleContext.java))

- Holds: contextId, channelId, externalRunId, configuredBotId, runtimeBotInstanceId, repo, requestText, status. No delivery-target or thread field.

### CreateLifecycleContextAction ([CreateLifecycleContextAction.java](src/main/java/com/vinekeepers/workflow/actions/CreateLifecycleContextAction.java))

- Builds context from bind/state: channelId, configuredBotId, runtimeBotInstanceId (from instanceId), repo (from project), requestText (from codeChange), status. Puts the context; no delivery target is set.

### PostChannelMessageAction ([PostChannelMessageAction.java](src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java))

- Resolves `channelId` from bind then state; sends via `replySender.send(channelId, null, content)`. No concept of a separate delivery target (e.g. thread). Backward compatible: callers only need channelId.

### LaunchCursorRunAction ([LaunchCursorRunAction.java](src/main/java/com/vinekeepers/workflow/actions/LaunchCursorRunAction.java))

- Builds `LifecycleRunRecord` with channelId (from args/state/event meta), replyToMessageId (from event meta — the message that triggered the flow, e.g. in the intake channel). Record has no thread or delivery-target field. Stored in stateStore under `cursor:run:{agentId}`.

### CursorCloudRunMonitor ([CursorCloudRunMonitor.java](src/main/java/com/vinekeepers/core/cursor/CursorCloudRunMonitor.java))

- `sendUpdate(runState, message)` calls `sender.send(runState.getChannelId(), runState.getReplyToMessageId(), message)`. So all Cursor status/PR/feedback/terminal updates go to the room channel, with optional replyToMessageId. If we switched to sending to a thread id without fixing the router, we’d still hit the wrong-sender problem above; and replyToMessageId refers to a message in the parent channel, not in the thread.

### DiscordGateway / JdaDiscordGateway

- `send(channelId, messageId, content)`: uses `jda.getChannelById(MessageChannel.class, channelId)`. In Discord, a thread is a channel, so sending to a thread id works **once** the correct sender (gateway) is chosen. The gateway does not do lifecycle lookup; that is the router’s job. No thread-creation API exists today.

### Bootstrap ([Bootstrap.java](src/main/java/com/vinekeepers/core/Bootstrap.java))

- `registerLifecycleActions(registry)` registers: provision_bot_instance, create_lifecycle_context, launch_cursor_run (no Discord dependency).
- In `withDiscord()`: registers create_channel (when default gateway is set), post_channel_message; engine and monitor get `outboundDeliveryRouter` as reply sender. create_thread does not exist.

### config/bots.yaml

- luna_cursor workflow: create_channel → branch on CHANNEL_CREATE_FAILED → provision_bot_instance → create_lifecycle_context → post_channel_message → launch_cursor_run → done. No thread step; all posts go to channelId.

---

## Required routing fix (mandatory)

**Problem:** OutboundDeliveryRouter resolves lifecycle context only by channelId. LifecycleContextStore is indexed only by channelId and externalRunId. If PostChannelMessageAction or CursorCloudRunMonitor sends to a thread id, the router looks up that id, finds no context, and falls back to the default sender/gateway. So we must **extend lifecycle resolution so that a thread id (or any delivery-target id) resolves to the same lifecycle context as the parent room**.

**Two required steps (non-optional).** See **Required implementation step: routing fix** below for the concrete implementation.

1. **LifecycleContext** gains an optional **delivery channel id** (e.g. `deliveryChannelId`): when set, it is the Discord channel/thread id where updates should be sent (thread id in our case). When null, behavior is unchanged (delivery target = room channelId). This keeps the model generic (one optional “where to send” target) and avoids Arrietty-specific naming in the domain.

2. **LifecycleContextStore** must resolve context by **delivery target id** as well as by channelId:
   - When `put(context)` is called and `context.getDeliveryChannelId()` is non-null, register that id in a second index (e.g. `deliveryTargetToContextId`) so that a lookup by that id returns the same context.
   - Add `getByDeliveryTargetId(String targetId)`: first try `getByChannelId(targetId)` (backward compatible); if empty, look up in the delivery-target index and return that context. So both room channel ids and thread ids resolve to the correct context.

3. **OutboundDeliveryRouter** must use this new resolution for both sender and gateway lookup:
   - In `send(channelId, messageId, content)`: treat the first parameter as the **delivery target id** (channel or thread). Use `lifecycleContextStore.getByDeliveryTargetId(channelId)` instead of `getByChannelId(channelId)`. Then resolve sender and call `sender.send(channelId, messageId, content)` (the actual send target is still the same id).
   - In `getGatewayForChannel(String channelId)`: use `getByDeliveryTargetId(channelId)` instead of `getByChannelId(channelId)` so that when the sink or a caller passes a thread id, the correct gateway (e.g. Arrietty’s) is returned. Signature can stay for backward compatibility; semantics become “resolve gateway for this delivery target (channel or thread) id”.

With this, when PostChannelMessageAction or CursorCloudRunMonitor sends to a thread id, the router resolves the lifecycle context (and thus the configured bot) via the new index and uses that bot’s sender; no Arrietty-specific logic in core.

**Reply behavior when sending to a thread:** `replyToMessageId` in the run record refers to the message that triggered the flow (e.g. “Launch” in the intake channel or in the room). When delivery target is a **thread**, that message is not in the thread, so replying to it from the thread is wrong or unsupported. The plan must **explicitly** set `messageId` to `null` when sending to a thread (or to any delivery target that is not the room channel). So: in the monitor, when `runState.getDeliveryChannelId() != null`, call `send(runState.getDeliveryChannelId(), null, content)`.

---

## Required implementation step: routing fix (do this first)

**This step is mandatory.** Without it, when PostChannelMessageAction or CursorCloudRunMonitor sends to a thread id, the router looks up context by that id, finds nothing (the store is indexed only by room channelId and externalRunId), and falls back to the default sender/gateway. Updates intended for the lifecycle room would then go to the wrong bot or channel.

Implement the following before adding thread creation or changing where messages are sent:

1. **Extend LifecycleContextStore** so that thread (and other) delivery targets resolve to the same lifecycle context as the parent lifecycle room:
   - Add a second index (e.g. `deliveryTargetToContextId`) that maps a delivery target id (e.g. thread id) to the same context as the room’s channelId.
   - When `put(context)` is called and the context has an optional delivery target id (e.g. `deliveryChannelId`) set, register that id in this index.
   - Add `getByDeliveryTargetId(String targetId)`: return the context when targetId is the room channelId (existing behavior) or when targetId is a registered delivery target id. So both room channel ids and thread ids resolve to the correct lifecycle context.

2. **Update OutboundDeliveryRouter** so that both send and gateway resolution use this lookup:
   - In **`send(channelId, messageId, content)`**: resolve lifecycle context with `getByDeliveryTargetId(channelId)` instead of `getByChannelId(channelId)`. Then resolve the configured bot’s sender and call `sender.send(channelId, messageId, content)` (the first argument remains the actual send target).
   - In **`getGatewayForChannel(channelId)`** (and any other gateway resolution path): use `getByDeliveryTargetId(channelId)` instead of `getByChannelId(channelId)` so that when the caller passes a thread id (e.g. from DiscordAppReplySink or from an action), the router returns the correct gateway for that lifecycle room.

After this, sending to a thread id still resolves to the correct lifecycle context and thus the correct bot sender and gateway.

---

## Delivery target: use one generic field (deliveryChannelId)

**Recommendation: use a single optional `deliveryChannelId` everywhere instead of spreading `updatesThreadId` across multiple runtime classes and actions.**

- **Cleaner:** One field name in LifecycleContext, LifecycleRunRecord, CreateLifecycleContextAction, PostChannelMessageAction, CursorCloudRunMonitor, and workflow state. Callers and config use the same key (e.g. create_thread `storeIn: deliveryChannelId`; actions and monitor read/write `deliveryChannelId`). No need to remember which class uses which name.
- **Generic:** “Delivery channel id” fits both Discord (channel or thread id) and future connectors; “updates thread id” is Discord- and thread-specific.
- **Easier to extend:** Later, if you add multiple targets per room (e.g. a map by key), you can keep `deliveryChannelId` as the default/default key and add a separate structure; you don’t have to rename `updatesThreadId` in several places.

So: add optional `deliveryChannelId` to LifecycleContext and LifecycleRunRecord; have CreateLifecycleContextAction, PostChannelMessageAction, and LaunchCursorRunAction use only `deliveryChannelId` from bind/state; have CursorCloudRunMonitor use `runState.getDeliveryChannelId()` when present; and use `storeIn: deliveryChannelId` in the create_thread workflow step. Do not introduce `updatesThreadId` in the Java API or in multiple actions.

---

## Revised implementation plan

### 1. LifecycleContext: optional deliveryChannelId

- Add optional field `deliveryChannelId` (String). When non-null, it is the channel/thread id to use for outbound delivery (e.g. Discord thread id). When null, delivery uses `channelId` (current behavior).
- Add constructor overload(s) and getter; keep all existing constructors for backward compatibility. No other behavior change in this class.

### 2. LifecycleContextStore: index by delivery target and getByDeliveryTargetId (implements required step 1)

- Add a map: `deliveryTargetToContextId` (e.g. `ConcurrentHashMap<String, String>`).
- In `put(LifecycleContext)`: if `context.getDeliveryChannelId() != null && !context.getDeliveryChannelId().isBlank()`, put `context.getDeliveryChannelId() -> context.getContextId()` into `deliveryTargetToContextId`. (If we later support context removal or updating delivery target, we would need to remove the old delivery target id from this map; not required for the minimal first pass.)
- Add `Optional<LifecycleContext> getByDeliveryTargetId(String targetId)`:
  - If targetId is null/blank, return empty.
  - Try `getByChannelId(targetId)`; if present, return it.
  - Else look up `deliveryTargetToContextId.get(targetId)` to get contextId, then return `byId.get(contextId)`.
- No other API changes. All existing callers of `getByChannelId` remain valid.

### 3. OutboundDeliveryRouter: resolve by delivery target id (implements required step 2)

- In `send(String channelId, String messageId, String content)`: replace `lifecycleContextStore.getByChannelId(channelId)` with `lifecycleContextStore.getByDeliveryTargetId(channelId)`. The parameter remains the actual send target (channel or thread id); it is now also used as the lookup key for context resolution. Then resolve configuredBotId and sender as today; call `sender.send(channelId, messageId, content)`.
- In `getGatewayForChannel(String channelId)`: replace `getByChannelId(channelId)` with `getByDeliveryTargetId(channelId)`. Callers (e.g. DiscordAppReplySink) continue to pass the reply target id (channel or thread); they need no change.

This completes the **required** fix for thread-targeted delivery so that sending to a thread id still resolves to the correct lifecycle context and bot.

### 4. Discord gateway: create thread

- **DiscordGateway**: add default method `String createThreadChannel(String guildId, String channelId, String threadName)` returning thread id or null.
- **JdaDiscordGateway**: implement using existing guild/channel resolution pattern (as in createTextChannel), then `TextChannel.createThreadChannel(threadName).submit().get(timeout)` with the same timeout style as createTextChannel. Return the created thread channel’s id.

### 5. Workflow action: create_thread (config-driven)

- New action **CreateThreadAction**: takes bind/state `channelId`, `guildId` (or from event `sourceId` when it starts with `discord:`), `threadName`. Uses the same gateway source as CreateChannelAction (router.getDefaultGateway()). Calls `gateway.createThreadChannel(guildId, channelId, threadName)`; returns thread id or a sentinel (e.g. `THREAD_CREATE_FAILED`) on failure. Step `storeIn` should be `deliveryChannelId` so the thread id is stored under the same key used everywhere. No bot id or “Arrietty” in the action; it is a generic lifecycle/workflow action.
- **Bootstrap**: in `withDiscord()`, when default gateway is present, register `create_thread` with the same router (for getDefaultGateway()), e.g. `actionRegistry.register("create_thread", new CreateThreadAction(outboundDeliveryRouter))`.

### 6. CreateLifecycleContextAction: set deliveryChannelId from bind/state

- When building the context, resolve optional deliveryChannelId from bind then state. Support at least one key that workflows can use for “where to send updates,” e.g. `deliveryChannelId` or `updatesThreadId` (so that create_thread’s storeIn can be `updatesThreadId` and we don’t force a rename in YAML). Set `context.deliveryChannelId` when non-blank. No hard-coding of bot ids.

### 7. PostChannelMessageAction: send to deliveryChannelId when present

- Resolve send target from one key only: `firstNonBlank(getString(bind, "deliveryChannelId"), getString(state, "deliveryChannelId"), channelId)`. Call `replySender.send(resolvedTargetId, null, content)`. Backward compatible: when deliveryChannelId is not set, behavior is unchanged (channelId). Using a single generic key avoids spreading `updatesThreadId` across actions and keeps the contract clear for future workflows and connectors.

### 8. LifecycleRunRecord: optional deliveryChannelId; monitor sends to it and clears messageId for thread

- **LifecycleRunRecord**: add optional field `deliveryChannelId` and getter; constructor overload that accepts it. When non-null, the monitor should send to this id instead of channelId.
- **CursorCloudRunMonitor.sendUpdate**: compute send target as `runState.getDeliveryChannelId() != null && !runState.getDeliveryChannelId().isBlank() ? runState.getDeliveryChannelId() : runState.getChannelId()`. Use that as the first argument to `sender.send(...)`. For the second argument (messageId): when sending to the delivery channel (thread), pass **null** so we do not reply to a message (the original replyToMessageId is in the room channel, not in the thread). So: `messageId = (runState.getDeliveryChannelId() != null && !runState.getDeliveryChannelId().isBlank()) ? null : runState.getReplyToMessageId()`.
- **LaunchCursorRunAction**: when building LifecycleRunRecord, read optional deliveryChannelId from state/bind and pass it into the record constructor. Config-driven via state populated by create_thread’s storeIn.

### 9. Workflow config (bots.yaml)

- In **luna_cursor**, after create_channel and the branch on CHANNEL_CREATE_FAILED, add: a `call_action` step with `action: create_thread`, bind e.g. `threadName: "Room updates"`, `storeIn: deliveryChannelId`. Add a branch on `deliveryChannelId == THREAD_CREATE_FAILED` to the failure step if desired. State then has deliveryChannelId (the thread id) for subsequent steps. create_lifecycle_context reads deliveryChannelId from state and sets it on the context. post_channel_message resolves send target from state.deliveryChannelId then channelId; launch_cursor_run passes state.deliveryChannelId into LifecycleRunRecord. One key (deliveryChannelId) in both YAML and Java keeps the design clean and generic.
- This keeps the workflow config-driven; no Java code references Arrietty for threads.

### 10. Specs and docs

- **connectors-registry** (or equivalent): document that the router resolves by delivery target id (channel or thread), and that the store indexes optional deliveryChannelId; document gateway createThreadChannel.
- **workflow-registry**: document create_thread action, deliveryChannelId in context and run record, PostChannelMessageAction resolution of send target, and monitor replyToMessageId = null when delivery target is set.
- **mkdoc**: update Discord connector and workflow docs (how-it-works, change-log) for thread-based lifecycle updates and the routing fix. No Arrietty-specific wording; describe “lifecycle room” and “delivery target.”

---

## Tradeoffs / first-pass vs future-proof option

**Recommendation: single optional `deliveryChannelId` (first pass) with a clear path to a generic target model.**

- **Why one field now:** The immediate goal is “updates in this room go to one thread.” One optional id (deliveryChannelId) on context and run record is the smallest change that satisfies the requirement and fixes the router/store. It keeps the contract simple: “delivery target id = channel id or thread id,” which matches Discord’s model and avoids a larger refactor before we know the exact multi-thread UX.
- **Why the name deliveryChannelId:** It is connector-agnostic enough (a channel id in Discord covers both channels and threads) and leaves room for other connectors later. We avoid “updatesThreadId” in the Java API so the domain stays generic; Using storeIn: deliveryChannelId in the create_thread step keeps one key in both YAML and Java.
- **Follow-up refactor path:** When we need multiple threads per room (e.g. “status” vs “feedback”), we can:
  - Add something like `Map<String, String> deliveryTargetsByKey` (or `deliveryChannelIdsByKey`) to LifecycleContext and optionally to the run record, and have the store index each value in deliveryTargetToContextId (so any of those ids resolves to the context).
  - PostChannelMessageAction and the monitor could then take an optional “target key” (e.g. from bind/state or a default key like “default”/“updates”); resolve the channel id from context/state by key and send there. The current single deliveryChannelId can be treated as the default key (e.g. “default” or “updates”) so existing config keeps working.

**Alternative considered:** Adding only a thread-specific index (e.g. “threadId -> contextId”) without adding deliveryChannelId to the context. That would fix routing but would duplicate the “where to send” notion (channelId vs threadId) in the store only and not in the context/record, making it harder for actions and the monitor to know where to send. Putting deliveryChannelId on the context and record keeps “where to send” in one place and is clearer for future extensions.

---

## Validation and tests

- **LifecycleContextStore**: Unit test that put(context) with deliveryChannelId set allows getByDeliveryTargetId(threadId) to return that context; getByChannelId(channelId) still returns it; getByDeliveryTargetId(channelId) still returns it (backward compatibility).
- **OutboundDeliveryRouter**: Unit test that send(threadId, null, content) uses the correct bot sender when that thread id is registered as a delivery target for a lifecycle context (mock store returning context with configuredBotId).
- **CreateThreadAction**: Unit test with mock gateway; success returns thread id and storeIn puts it in state; failure returns THREAD_CREATE_FAILED.
- **CreateLifecycleContextAction**: Unit test that context is created with deliveryChannelId when bind or state provides deliveryChannelId.
- **PostChannelMessageAction**: Unit test that when state has deliveryChannelId, send is invoked with that id as the first argument.
- **LifecycleRunRecord / CursorCloudRunMonitor**: Unit test that when record has deliveryChannelId set, sendUpdate uses that id and passes null for messageId.
- **LaunchCursorRunAction**: Unit test that LifecycleRunRecord is built with deliveryChannelId when state (or bind) contains deliveryChannelId.
- **Integration (optional):** With a real or mock Discord gateway, run the full luna_cursor flow with create_thread and assert that the store indexes the thread id and that a send to that thread id is routed to the configured bot.
- **Regression:** Existing tests that send by channelId only must still pass; no change to behavior when deliveryChannelId is not set.
- Run project validation: `npm run validate-specs`, `npm run validate-drift`, `mvn test`, `mvn compile`, `npm run validate-docs`.
