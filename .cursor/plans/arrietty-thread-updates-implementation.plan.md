---
name: ""
overview: ""
todos: []
isProject: false
---

# Arrietty thread-based updates: implementation plan (code-grounded)

---

## Required routing fix (implement first)

In the current codebase, **OutboundDeliveryRouter** resolves lifecycle context by **channelId** only, and **LifecycleContextStore** is indexed only by **channelId** and **externalRunId**. If **PostChannelMessageAction** or **CursorCloudRunMonitor** sends to a thread id, the router looks up that thread id as if it were a channel id, finds no context, and falls back to the default sender/gateway. So lifecycle room updates would go to the wrong bot or channel.

**This fix is mandatory.** Implement the following before adding thread creation or changing where messages are sent:

1. **Extend LifecycleContextStore** so that thread IDs or delivery target IDs resolve to the same lifecycle context as the parent lifecycle room.
  - Add a second index (e.g. `deliveryTargetToContextId`) that maps a delivery target id (e.g. thread id) to the same context as the room’s channelId.
  - When `put(context)` is called and the context has an optional delivery target id set (e.g. `deliveryChannelId`), register that id in this index.
  - Add **getByDeliveryTargetId(String targetId)**: return the context when targetId is the room channelId (existing behavior) or when targetId is a registered delivery target id. So both room channel ids and thread ids resolve to the correct lifecycle context.
2. **Update OutboundDeliveryRouter.send(...) and gateway resolution** to resolve lifecycle context for thread targets as well as room channel ids.
  - In **send(channelId, messageId, content)**: use **lifecycleContextStore.getByDeliveryTargetId(channelId)** instead of **getByChannelId(channelId)**. Then resolve the configured bot’s sender and call **sender.send(channelId, messageId, content)**.
  - In **getGatewayForChannel(channelId)** (the gateway resolution path): use **getByDeliveryTargetId(channelId)** instead of **getByChannelId(channelId)** so that when the caller passes a thread id, the router returns the correct gateway for that lifecycle room.

After this, sending to a thread id still resolves to the correct lifecycle context and thus the correct bot sender and gateway.

---

## Runtime field: use deliveryChannelId (not updatesThreadId)

Use a **single optional `deliveryChannelId`** in the runtime model (LifecycleContext, LifecycleRunRecord, and the actions that read/write it) instead of spreading **updatesThreadId** across multiple classes.

- **Cleaner:** One field name everywhere; same key in workflow state (e.g. create_thread storeIn: deliveryChannelId). No confusion about which class uses “updatesThreadId” vs “channelId.”
- **Generic:** “Delivery channel id” means “where to send updates”; it can be a Discord channel or thread id and fits future connectors. “Updates thread id” is thread-specific and would need renaming if we later support multiple targets or non-thread channels.
- **Easier to extend:** Later we can add a map (e.g. deliveryTargetsByKey) and keep deliveryChannelId as the default key; we don’t have to rename updatesThreadId in several places.

So: add **deliveryChannelId** (optional) to LifecycleContext and LifecycleRunRecord; have CreateLifecycleContextAction, PostChannelMessageAction, and LaunchCursorRunAction use **deliveryChannelId** from bind/state; have CursorCloudRunMonitor use **runState.getDeliveryChannelId()** when present. Do **not** introduce **updatesThreadId** as a separate field in multiple runtime classes.

---

## 1. Current state assessment based on the actual code

### DiscordGateway and JdaDiscordGateway

- **DiscordGateway** ([DiscordGateway.java](src/main/java/com/vinekeepers/connectors/DiscordGateway.java)): Interface for send, createTextChannel, getSelfUserId, addPermissionOverride. No thread creation. **send(channelId, messageId, content)** takes a channel id and optional message id; the gateway does not perform any lifecycle or routing lookup—it just sends to the given channel id.
- **JdaDiscordGateway** ([JdaDiscordGateway.java](src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java)): **send** uses `jda.getChannelById(MessageChannel.class, channelId)` then sends in that channel. In Discord/JDA, a thread is a channel (ThreadChannel extends MessageChannel), so if `channelId` is a thread id, the message is sent in the thread. The gateway does not need to change for thread targets; the only requirement is that the **caller** (router) passes the correct channel/thread id and that the **router** has already chosen the correct gateway (bot) for that target. There is no **createThreadChannel** today.

### OutboundDeliveryRouter

- **OutboundDeliveryRouter** ([OutboundDeliveryRouter.java](src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java)): Implements **DiscordReplySender**. **send(channelId, messageId, content)** (lines 99–124): it looks up lifecycle context with **lifecycleContextStore.getByChannelId(channelId)** only. From that context it gets **configuredBotId**, resolves the bot’s sender from **botIdToSender**, then calls **sender.send(channelId, messageId, content)**. So the first parameter is used both as the **lookup key** (to find context and thus the bot) and as the **send target**. **getGatewayForChannel(channelId)** (lines 65–78): same lookup—**getByChannelId(channelId)** only—then returns that bot’s gateway or the default. So **every place that resolves “which bot/gateway for this target?” uses channelId only**.
- **Implication**: If any caller (e.g. PostChannelMessageAction or CursorCloudRunMonitor) passes a **thread id** as the first argument, the router calls **getByChannelId(threadId)**. No lifecycle context has **channelId == threadId** (the room’s channelId is the **parent** channel). Lookup returns empty, the router uses **defaultSender** / **defaultGateway**. Result: messages intended for the lifecycle room (Arrietty) go to the wrong bot or channel. **This is the core bug.**

### LifecycleContextStore

- **LifecycleContextStore** ([LifecycleContextStore.java](src/main/java/com/vinekeepers/state/LifecycleContextStore.java)): Holds contexts in **byId** (contextId → context). Indexes: **channelToContextId** (channelId → contextId) and **externalRunIdToContextId** (externalRunId → contextId). **put(context)** only registers **context.getChannelId()** and **context.getExternalRunId()**. There is **no** index by thread id or any other delivery target. **getByChannelId(channelId)** is the only lookup used by the router for send/gateway resolution. So a thread id can never resolve to a context.

### LifecycleContext

- **LifecycleContext** ([LifecycleContext.java](src/main/java/com/vinekeepers/state/LifecycleContext.java)): Fields: contextId, channelId, createdAt, externalRunId, configuredBotId, runtimeBotInstanceId, repo, requestText, status. **channelId** is the room (parent) channel. There is no field for “where to send updates” (e.g. thread id or delivery target).

### CreateLifecycleContextAction

- **CreateLifecycleContextAction** ([CreateLifecycleContextAction.java](src/main/java/com/vinekeepers/workflow/actions/CreateLifecycleContextAction.java)): Reads channelId, configuredBotId, runtimeBotInstanceId (from instanceId), repo (from project), requestText (from codeChange), status from bind then state. Builds a **LifecycleContext** with the 9-arg constructor and calls **lifecycleContextStore.put(context)**. It does not set any delivery target or thread id.

### PostChannelMessageAction

- **PostChannelMessageAction** ([PostChannelMessageAction.java](src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java)): Resolves **channelId** from bind then state (line 26); resolves **content** from bind then state and interpolates with merged state+bind. Then calls **replySender.send(channelId, null, content)** (line 40). So it always sends to **channelId** (the room channel). It has no concept of a separate thread or delivery target. The **replySender** here is the **OutboundDeliveryRouter**; so the router receives **channelId** (room id) and correctly finds the lifecycle context. If we changed this to send to a thread id without fixing the router, the router would receive the thread id and **getByChannelId(threadId)** would fail.

### LaunchCursorRunAction

- **LaunchCursorRunAction** ([LaunchCursorRunAction.java](src/main/java/com/vinekeepers/workflow/actions/LaunchCursorRunAction.java)): Reads channelId from args/state or from __event; replyToMessageId from __event (messageId). Builds **LifecycleRunRecord** with channelId, replyToMessageId, and no thread/delivery field (lines 85–98). Record is stored in stateStore. So the monitor later has only **channelId** and **replyToMessageId** to send to.

### CursorCloudRunMonitor

- **CursorCloudRunMonitor** ([CursorCloudRunMonitor.java](src/main/java/com/vinekeepers/core/cursor/CursorCloudRunMonitor.java)): **sendUpdate(runState, message)** (lines 97–104) calls **sender.send(runState.getChannelId(), runState.getReplyToMessageId(), message)**. So it always sends to the room **channelId** with optional **replyToMessageId**. The sender is the router; so again the router gets the room channel id and resolves correctly. If we changed the monitor to send to a thread id (e.g. from a new field on LifecycleRunRecord) without fixing the router, **getByChannelId(threadId)** would fail and the default sender would be used. **replyToMessageId** today is the message that triggered the flow (e.g. “Launch” in the intake channel); that message is not inside a lifecycle-room thread, so when we later send to a thread we must not use it for “reply” in that thread (Discord would fail or misplace the reply).

### Bootstrap wiring and action registration

- **Bootstrap** ([Bootstrap.java](src/main/java/com/vinekeepers/core/Bootstrap.java)): **registerLifecycleActions** (constructor) registers provision_bot_instance, create_lifecycle_context, launch_cursor_run (no Discord). **withDiscord()** (lines 155–203): after wiring gateways and default sender, registers **create_channel** (when default gateway is set) and **post_channel_message** with the **OutboundDeliveryRouter**. Engine and **CursorCloudRunMonitor** get **outboundDeliveryRouter** as reply sender. There is no **create_thread** action.

### config/bots.yaml

- **luna_cursor** workflow (lines 125–153): create_channel (storeIn: channelId) → branch on CHANNEL_CREATE_FAILED → provision_bot_instance (storeIn: instanceId) → create_lifecycle_context (bind configuredBotId: arrietty, storeIn: contextId) → post_channel_message (bind content + lifecycleBotName) → launch_cursor_run (storeIn: launchMessage) → done. All posts go to the room **channelId** from state. No thread step; no delivery target in context or record.

### DiscordAppReplySink

- **DiscordAppReplySink** ([DiscordAppReplySink.java](src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java)): When router is set, **gatewayFor(target)** uses **router.getGatewayForChannel(target.channelId())** (line 51). So when the engine delivers a reply, the target’s channelId (e.g. from the event) is passed to the router. If that were ever a thread id, the same bug would apply: **getByChannelId(threadId)** would miss the context.

---

## 2. Required design correction

**Core bug**: OutboundDeliveryRouter resolves lifecycle context only by **channelId**. LifecycleContextStore is indexed only by **channelId** and **externalRunId**. A Discord thread id is **not** the same as the parent lifecycle room channel id. Therefore, if PostChannelMessageAction or CursorCloudRunMonitor sends to a thread id, router lookup by that thread id finds no context and the router falls back to the default sender/gateway. The fix must be in the **store** and **router**, not only in “where we pass a thread id.”

**Required correction** (first-class, not a side note):

1. **LifecycleContextStore** must be able to resolve a context when the lookup key is either the room channel id **or** a registered delivery target id (e.g. the updates thread id). So we need a second index: e.g. **deliveryTargetId → contextId** (or equivalent), populated when a context has an optional delivery target (e.g. thread id). A single new method **getByDeliveryTargetId(String targetId)** that returns the context when targetId is the room channelId (existing behavior) or when targetId is in the new index is sufficient and backward compatible.
2. **OutboundDeliveryRouter** must use this resolution for **both** send and gateway lookup. In **send(channelId, messageId, content)**: resolve context with **getByDeliveryTargetId(channelId)** instead of **getByChannelId(channelId)**. In **getGatewayForChannel(channelId)**: use **getByDeliveryTargetId(channelId)** instead of **getByChannelId(channelId)**. The parameter remains the actual send target (channel or thread id); the router’s job is to resolve “which bot/gateway for this target?” using the store’s delivery-target-aware lookup. No other gateway resolution path exists in the codebase (CreateChannelAction uses getDefaultGateway(); DiscordAppReplySink and actions use getGatewayForChannel or send on the router).

After this, when any caller passes a thread id to **send** or **getGatewayForChannel**, the router still resolves the correct lifecycle context and thus the correct configured bot sender and gateway.

---

## 3. Revised implementation plan

**Implementation order** (to avoid partial broken states):

1. **First:** LifecycleContext (add deliveryChannelId) + LifecycleContextStore (deliveryTargetToContextId, getByDeliveryTargetId, put consistency rules) + OutboundDeliveryRouter (send and getGatewayForChannel use getByDeliveryTargetId).  
2. **Second:** DiscordGateway/JdaDiscordGateway thread creation support (createThreadChannel).  
3. **Third:** CreateThreadAction + bootstrap registration.  
4. **Fourth:** CreateLifecycleContextAction (read deliveryChannelId, set on context) + LifecycleRunRecord (add deliveryChannelId) + LaunchCursorRunAction (pass deliveryChannelId into record).  
5. **Fifth:** PostChannelMessageAction (send target = deliveryChannelId or channelId; sentinel handling) + CursorCloudRunMonitor (send target, replyToMessageId = null for thread, sentinel handling).  
6. **Sixth:** config/bots.yaml (create_thread step, branch, state flow).  
7. **Seventh:** tests, docs, specs.

### Step 1 (mandatory first): Fix lifecycle routing for thread targets

**1.1 LifecycleContext**  

- Add one optional field: **deliveryChannelId** (String). Semantics: when non-null/non-blank, this is the channel/thread id where lifecycle updates should be sent (e.g. the “Room updates” thread). When null or blank, delivery uses **channelId** (current behavior). Add constructor overload(s) and getter; keep all existing constructors for backward compatibility.

**1.2 LifecycleContextStore**  

- Add a map: **deliveryTargetToContextId** (e.g. `ConcurrentHashMap<String, String>`).  
- In **put(LifecycleContext)** — index consistency (avoid stale entries):
  - **Before** adding any new delivery-target mapping for this context: if an existing context with the same **contextId** is already in **byId** and that existing context (or a previous put) had a **deliveryChannelId** that was indexed, **remove** the old delivery-target mapping. Concretely: look up the existing context by **context.getContextId()**; if present, get its current **deliveryChannelId** (if the store does not hold mutable context, you must track “which delivery target id was last indexed for this contextId” e.g. in a side map **contextIdToLastDeliveryTargetId**, and remove **lastDeliveryTargetId → contextId** from **deliveryTargetToContextId** before updating). So: remove any **deliveryTargetToContextId** entry whose value equals **context.getContextId()** (i.e. remove the old key(s) that pointed to this context).
  - If the context is updated so **deliveryChannelId** becomes null or blank: remove any previous delivery-target mapping for this context (any entry in **deliveryTargetToContextId** that maps to this **contextId**). Do **not** add a new mapping.
  - If **context.getDeliveryChannelId()** is non-null and non-blank after cleanup: put **context.getDeliveryChannelId() → context.getContextId()** into **deliveryTargetToContextId**.  
  - Result: if a context is re-put with a **different** deliveryChannelId, the old thread id no longer resolves to that context. If re-put with null/blank deliveryChannelId, no delivery target id resolves to that context.
- **If the codebase adds a delete/remove path for lifecycle contexts** (e.g. remove by contextId or by channelId): that path must also remove any **deliveryTargetToContextId** entries whose value is the removed context’s contextId (so the index does not retain stale mappings to deleted contexts). The current codebase may not have such a path; the plan assumes put-only for now but requires that any future remove logic cleans up **deliveryTargetToContextId**.
- Add **Optional<LifecycleContext> getByDeliveryTargetId(String targetId)**:
  - If targetId is null or blank, return empty.
  - Call **getByChannelId(targetId)**; if present, return it (backward compatible: room channel id still works).
  - Else get **contextId = deliveryTargetToContextId.get(targetId)**; if non-null, return **byId.get(contextId)**.
  - Else return empty.

**1.3 OutboundDeliveryRouter**  

- In **send(String channelId, String messageId, String content)**: replace **lifecycleContextStore.getByChannelId(channelId)** with **lifecycleContextStore.getByDeliveryTargetId(channelId)**. Keep the rest unchanged: resolve configuredBotId, sender, then **sender.send(channelId, messageId, content)**.  
- In **getGatewayForChannel(String channelId)**: replace **getByChannelId(channelId)** with **getByDeliveryTargetId(channelId)**. Return that context’s bot gateway or default.

This completes the **routing fix**. No new actions or workflow changes are required for the fix itself; once this is in place, any caller that passes a thread id will get the correct bot and gateway.

### Step 2: Discord thread creation

**2.1 DiscordGateway**  

- Add default method **String createThreadChannel(String parentChannelId, String threadName)** returning the new thread’s channel id or null. Parameters: **parentChannelId** = the lifecycle room text channel id; **threadName** = the thread name. No **guildId** in the signature: in JDA we can resolve the text channel by id (**jda.getTextChannelById(parentChannelId)**); if the channel is in cache, we can call **createThreadChannel(threadName)** on it. If a future implementation needs guildId (e.g. for uncached channels), the gateway can be extended later; for the minimal first pass, parent channel id is sufficient.

**2.2 JdaDiscordGateway**  

- Implement **createThreadChannel(parentChannelId, threadName)**: get **TextChannel parent = jda.getTextChannelById(parentChannelId)**; if null return null; create thread with **parent.createThreadChannel(threadName).submit().get(timeout)** (reuse same timeout style as createTextChannel); return the created thread channel’s id.

**2.3 CreateThreadAction (new workflow action)**  

- **Inputs** (from bind then state): **channelId** (parent channel, e.g. lifecycle room), **threadName**. Optional: **guildId** from bind/state or event sourceId (discord:guildId) if a future gateway impl needs it; for the minimal JDA impl above, parent channel id is enough.  
- **Behavior**: get gateway from **OutboundDeliveryRouter.getDefaultGateway()** (same as CreateChannelAction). Call **gateway.createThreadChannel(channelId, threadName)**.  
- **Why default gateway:** Thread creation is a **child operation** on the already-created lifecycle room channel. In the current architecture, **create_channel** already relies on the default Discord gateway (no per-bot or per-channel gateway resolution for channel creation). **create_thread** follows the same pattern for consistency: one default Discord gateway creates both the room and the thread under it. This is acceptable for the current Discord-only flow. If future flows require connector-specific or per-bot thread creation (e.g. creating a thread in a channel owned by a different bot), the action can later be generalized to accept an optional gateway key or resolve gateway by channel; for this feature, default gateway is sufficient.  
- **Output**: return the thread id string, or a sentinel e.g. **THREAD_CREATE_FAILED** on failure.  
- **storeIn**: the step’s storeIn (e.g. **deliveryChannelId**) stores the return value in state. So state will contain the thread id (or sentinel) under that key.

**2.4 Bootstrap**  

- In **withDiscord()**, when **outboundDeliveryRouter.getDefaultGateway() != null**, register **create_thread** with the same router: e.g. **actionRegistry.register("create_thread", new CreateThreadAction(outboundDeliveryRouter))**.

**Sentinel THREAD_CREATE_FAILED — who interprets it**  

- **Only these components** may interpret the literal string **THREAD_CREATE_FAILED**: (1) **workflow branching** in config (e.g. branch on `deliveryChannelId == THREAD_CREATE_FAILED`), (2) **PostChannelMessageAction** when resolving the send target (treat sentinel as “no delivery target”; use channelId), (3) **CursorCloudRunMonitor** when resolving the send target (treat sentinel as “no delivery target”; use channelId).  
- **Router and store must not** treat THREAD_CREATE_FAILED as a valid delivery target id: the sentinel is never stored in **deliveryTargetToContextId** (only real thread ids are). So **getByDeliveryTargetId(THREAD_CREATE_FAILED)** will correctly return empty. Callers (PostChannelMessageAction, CursorCloudRunMonitor) must **never** pass the literal sentinel to **router.send(...)** or **gateway.send(...)**; they must treat it as “no thread” and use **channelId** as the send target.  
- **Invariant:** No code path shall ever pass the literal sentinel string as the first argument to **gateway.send(...)** or **router.send(...)**.

### Step 3: Populate delivery target in context and record

**3.1 CreateLifecycleContextAction**  

- When building the context, read optional **deliveryChannelId** from bind then state (same precedence as other fields). If the value is non-blank and **not** the sentinel **THREAD_CREATE_FAILED**, pass it into the LifecycleContext constructor (new overload that accepts deliveryChannelId). If the value is the sentinel or blank, pass null (do not set deliveryChannelId on the context). So the store **never** indexes the sentinel; only real thread ids are registered in deliveryTargetToContextId. When the workflow has run create_thread with storeIn: deliveryChannelId, state.deliveryChannelId is either the thread id or the sentinel; create_lifecycle_context sets it on the context only when it is a valid id. **put(context)** will then register it in the store’s deliveryTargetToContextId (Step 1.2) only when deliveryChannelId is set.

**3.2 LifecycleRunRecord**  

- Add optional field **deliveryChannelId** and getter; add constructor overload that accepts it. When non-null/non-blank, the monitor will send to this id instead of channelId.

**3.3 LaunchCursorRunAction**  

- When building LifecycleRunRecord, read optional **deliveryChannelId** from args (bind/state). Pass it into the LifecycleRunRecord constructor. So state.deliveryChannelId (from create_thread storeIn) flows into the record.

### Step 4: PostChannelMessageAction — send to delivery target when set

**4.1 Target resolution**  

- **Field to read**: optional **deliveryChannelId** from bind then state; fallback **channelId** (from bind then state). So: **sendTarget = firstNonBlank(bind.deliveryChannelId, state.deliveryChannelId, bind.channelId, state.channelId)**. **channelId** remains required for “which room” and for fallback when no thread is configured.  
- **Behavior**: call **replySender.send(sendTarget, null, content)**. When deliveryChannelId is set, sendTarget is the thread id; the router (after Step 1) will resolve context by **getByDeliveryTargetId(threadId)** and use the configured bot’s sender. When deliveryChannelId is missing or blank, sendTarget is channelId (current behavior).  
- **When thread creation failed**: if create_thread returned THREAD_CREATE_FAILED, state.deliveryChannelId is that sentinel. The action must not send to a literal "THREAD_CREATE_FAILED" channel. So: if **sendTarget** equals the sentinel (e.g. THREAD_CREATE_FAILED), treat as “no delivery target” and use **channelId** only for this send (fallback to room channel). Alternatively the workflow can branch on THREAD_CREATE_FAILED and skip post_channel_message to thread or use a different path; the action should at least not pass the sentinel to the router.

**4.2 replyToMessageId**  

- PostChannelMessageAction currently passes **null** for messageId (line 40). No change needed for reply semantics when sending to a thread.

### Step 5: CursorCloudRunMonitor — send to delivery target; fix replyToMessageId for thread

**5.1 Target resolution**  

- **Field to read**: **runState.getDeliveryChannelId()**. If non-null and non-blank and not the failure sentinel, use it as the send target; otherwise use **runState.getChannelId()**.  
- **Behavior**: **sendTarget = runState.getDeliveryChannelId() != null && !runState.getDeliveryChannelId().isBlank() && !THREAD_CREATE_FAILED.equals(runState.getDeliveryChannelId()) ? runState.getDeliveryChannelId() : runState.getChannelId()**. Call **sender.send(sendTarget, messageId, content)** where **messageId** is set as below.

**5.2 replyToMessageId when sending to a thread**  

- When the send target is the **thread** (deliveryChannelId set), **replyToMessageId** from the run record refers to a message in the intake/room channel, not in the thread. Replying to that message from inside the thread is invalid or undesired. The plan **explicitly** sets **messageId = null** when sending to the thread. So: **messageId = (sendTarget equals runState.getDeliveryChannelId()) ? null : runState.getReplyToMessageId()**. When sending to the room channel (no delivery target), keep current behavior: **messageId = runState.getReplyToMessageId()**.

**5.3 Fallback when thread missing or failed**  

- If deliveryChannelId is null, blank, or THREAD_CREATE_FAILED, send to channelId with existing replyToMessageId semantics (current behavior).

### Step 6: Workflow config (config/bots.yaml)

**6.1 luna_cursor sequence**  

- After **create_channel** and the branch on CHANNEL_CREATE_FAILED, add:
  - **call_action** with **action: create_thread**, **bind: { threadName: "Room updates" }**, **storeIn: deliveryChannelId**.
  - Optional **branch** on **deliveryChannelId == THREAD_CREATE_FAILED** to a failure step (e.g. “Could not create updates thread”) or continue and let later steps fall back to channelId.
- Then **provision_bot_instance** → **create_lifecycle_context** (bind **configuredBotId: arrietty**; state already has **channelId**, **deliveryChannelId** from create_thread, **instanceId** from provision). CreateLifecycleContextAction will read **deliveryChannelId** from state and set it on the context; **put** will register it in the store.
- Then **post_channel_message**: state has **channelId**, **deliveryChannelId** (thread id or sentinel). Action sends to **deliveryChannelId** when valid, else **channelId**.
- Then **launch_cursor_run**: state has **channelId**, **deliveryChannelId**, **contextId**, etc. LaunchCursorRunAction passes **deliveryChannelId** into LifecycleRunRecord.
- **storeIn** keys used: **channelId**, **deliveryChannelId** (create_thread), **instanceId**, **contextId**, **launchMessage**. State flows into later actions via the existing merge (state + bind) in CallActionStep.

**6.2 Config-driven**  

- No Arrietty-specific logic in Java; the workflow and bind (e.g. configuredBotId: arrietty, lifecycleBotName in post_channel_message) are in YAML.

### Step 7: Specs and docs

- Update **connectors-registry** (or equivalent) and **workflow-registry**: document getByDeliveryTargetId, deliveryChannelId on context and record, create_thread action, PostChannelMessageAction and monitor behavior, replyToMessageId = null for thread sends. Update **mkdoc** for Discord connector and workflow (how-it-works, change-log).

---

## 4. Tradeoffs and why this approach

**Chosen approach: slightly more generic first-pass with a single optional delivery target id (deliveryChannelId).**

- **Option 1 (minimal: updatesThreadId only)**  
  - Pros: Very minimal naming.  
  - Cons: Thread-specific name in the domain and in multiple classes; later if we add “status thread” vs “feedback thread” we’d have to rename or add more thread-specific fields.
- **Option 2 (single deliveryChannelId)**  
  - One optional id that means “where to send lifecycle updates” (channel or thread). Same field name in LifecycleContext, LifecycleRunRecord, CreateLifecycleContextAction, PostChannelMessageAction, and workflow state. Router/store treat it as “another key that resolves to this context.”  
  - Pros: One concept, one key; works for any channel id (room or thread); easy to extend later to a map (e.g. deliveryTargetsByKey) with deliveryChannelId as the default key.  
  - Cons: Slightly more generic than “updates thread” in name only.
- **Option 3 (targetIdsByKey / deliveryTargets map)**  
  - Pros: Ready for multiple threads per room.  
  - Cons: Bigger change, more API surface and config surface for a first pass; we don’t need multiple threads yet.

**Recommendation: Option 2 (deliveryChannelId).** It fixes the routing bug with one clear concept, stays backward compatible (null/blank = use channelId), avoids spreading a thread-specific name across the codebase, and leaves a simple path to Option 3 later (e.g. add deliveryTargetsByKey and index each value in the store; default key “default” or “updates” = current deliveryChannelId).

**guildId for createThreadChannel**: Not required for the minimal JDA implementation. We use **parentChannelId** and **jda.getTextChannelById(parentChannelId)**; if the channel is in cache, we can create the thread. If in the future we need guildId for uncached channels, the gateway method can be extended with an optional guildId parameter; the workflow already has access to event sourceId (discord:guildId) and can pass it in bind if needed.

---

## 5. Validation and tests

**5.1 LifecycleContextStore**  

- **getByChannelId** unchanged: put(context) with channelId only still returns context by channelId.  
- **getByDeliveryTargetId(roomChannelId)** returns the same context as getByChannelId(roomChannelId) (backward compatibility).  
- **getByDeliveryTargetId(threadId)** returns the context when that context has deliveryChannelId == threadId and was put in the store (index populated).  
- **getByDeliveryTargetId(unknownId)** returns empty.  
- **Index consistency — re-put with different deliveryChannelId:** Re-putting a context with the same contextId but a **different** deliveryChannelId must remove the old delivery-target mapping. Test: put context A with deliveryChannelId = threadId1; getByDeliveryTargetId(threadId1) returns A. Put context A again with deliveryChannelId = threadId2; getByDeliveryTargetId(threadId1) must return empty; getByDeliveryTargetId(threadId2) must return A.  
- **Index consistency — re-put with null/blank deliveryChannelId:** Re-putting a context with the same contextId but **null or blank** deliveryChannelId must remove the previous delivery-target mapping. Test: put context A with deliveryChannelId = threadId1; then put context A with deliveryChannelId = null (or ""); getByDeliveryTargetId(threadId1) must return empty.  
- **Old thread ids no longer resolve:** After the delivery target is changed (different thread id or cleared), the old thread id must no longer resolve to that context (covered by the two tests above).  
- **Sentinel not in store:** getByDeliveryTargetId(THREAD_CREATE_FAILED) must return empty. The store never indexes the sentinel; only real channel/thread ids are stored. Router does not resolve or send using THREAD_CREATE_FAILED: ensure no test or production path passes the sentinel to router.send(...) or getGatewayForChannel(...); and that if it were passed, the router would fall back to default (store returns empty).

**5.2 OutboundDeliveryRouter**  

- **send(roomChannelId, ...)** resolves lifecycle context and uses the configured bot’s sender (existing behavior).  
- **send(updatesThreadId, ...)** resolves lifecycle context via getByDeliveryTargetId(threadId) and uses the **same** configured bot’s sender (routing bug fix).  
- **getGatewayForChannel(roomChannelId)** returns the configured bot’s gateway.  
- **getGatewayForChannel(updatesThreadId)** returns the configured bot’s gateway (same as for room channel when thread is registered).  
- When no context exists for the given id (channel or thread), router falls back to default sender/gateway.

**5.3 PostChannelMessageAction**  

- When state has **deliveryChannelId** set to a valid thread id, **send** is invoked with that thread id as the first argument (and router resolves correctly).  
- When **deliveryChannelId** is missing or blank, **send** is invoked with **channelId** (current behavior).  
- When **deliveryChannelId** is THREAD_CREATE_FAILED, action does not pass sentinel to send (fallback to channelId or skip as designed).

**5.4 CursorCloudRunMonitor**  

- When LifecycleRunRecord has **deliveryChannelId** set to a valid thread id, **sendUpdate** calls sender.send(threadId, **null**, message) (messageId explicitly null for thread).  
- When **deliveryChannelId** is null/blank or sentinel, **sendUpdate** uses channelId and existing replyToMessageId.

**5.5 Fallback when thread creation fails**  

- When create_thread returns THREAD_CREATE_FAILED, state.deliveryChannelId is that string. PostChannelMessageAction and monitor treat it as “no thread” and fall back to channelId (and for monitor, replyToMessageId as today). No send to a literal "THREAD_CREATE_FAILED" channel.

**5.6 replyToMessageId for thread-targeted sends**  

- Test that when the monitor sends to a thread (deliveryChannelId set), the second argument to sender.send is **null**, not replyToMessageId.

**5.7 CreateThreadAction**  

- Unit test: with mock gateway, success returns thread id; failure returns THREAD_CREATE_FAILED. storeIn populates state with the returned value.

**5.8 CreateLifecycleContextAction**  

- Unit test: when bind or state provides deliveryChannelId (a valid thread id), the created context has deliveryChannelId set and put() is called; store’s getByDeliveryTargetId(deliveryChannelId) returns that context. When bind or state provides the sentinel THREAD_CREATE_FAILED, the context must not have deliveryChannelId set (null); the store must not index the sentinel.

**5.9 Integration / regression**  

- Existing tests that send by channelId only still pass.  
- Run project validation: **npm run validate-specs**, **npm run validate-drift**, **mvn test**, **mvn compile**, **npm run validate-docs**.

---

## 6. Summary: recommended approach and future path

**Recommended implementation approach**  

- Implement **Step 1 (routing fix)** first: add **deliveryChannelId** to LifecycleContext, add **deliveryTargetToContextId** and **getByDeliveryTargetId** to LifecycleContextStore, and switch **OutboundDeliveryRouter.send** and **getGatewayForChannel** to use **getByDeliveryTargetId** instead of **getByChannelId**. Then add thread creation (gateway + CreateThreadAction + bootstrap), populate deliveryChannelId in context and record (CreateLifecycleContextAction, LifecycleRunRecord, LaunchCursorRunAction), and change PostChannelMessageAction and CursorCloudRunMonitor to send to deliveryChannelId when set, with **messageId = null** for thread-targeted monitor updates.

**Why it fits this codebase**  

- It fixes the actual bug (router/store only know channelId; thread id would miss context) in the real classes (LifecycleContextStore, OutboundDeliveryRouter). It reuses the existing action/router/context/store/bootstrap patterns, stays config-driven, preserves backward compatibility (no delivery target = use channelId), and uses one generic field name (deliveryChannelId) instead of a thread-specific name in multiple runtime classes.

**Tradeoff**  

- We add one optional field and one index plus one lookup method; we do not introduce a full named-target map until we need multiple threads per room.

**Future refactor for multiple named threads per room**  

- Add e.g. **Map<String, String> deliveryTargetsByKey** (or deliveryChannelIdsByKey) to LifecycleContext (and optionally to the run record). In the store, register each value in the map in **deliveryTargetToContextId** so any of those ids resolves to the context. PostChannelMessageAction and the monitor can then take an optional “target key” (e.g. from bind/state or a default key like "updates"); resolve the channel id from context/state by key. Current **deliveryChannelId** can be the default key so existing config and behavior remain valid.

