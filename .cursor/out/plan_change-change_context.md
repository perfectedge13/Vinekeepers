# Change context (for plan_change / implement)

## Scope

**Request-derived:** Arrietty UX refinement pass — main intake room short redirect; create_thread after create_lifecycle_context using lifecycle owner gateway; LifecycleContext.withDeliveryChannelId, LifecycleContextStore.setDeliveryTargetId; config/bots.yaml reorder and done message.

**Features:** FEAT-CURSOR-GATHERING, FEAT-WORKFLOW-STEPS.

**Requirements:** REQ-WORKFLOW-001, REQ-LUNA-001.

**Assets:** ASSET-LIFECYCLE-CONTEXT, ASSET-LIFECYCLE-CONTEXT-STORE, ASSET-CREATE-THREAD-ACTION, ASSET-BOOTSTRAP, ASSET-BOTS-YAML, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION.

**Validation tests (in scope):** UNIT-CREATE-THREAD-ACTION, UNIT-LIFECYCLE-CONTEXT, UNIT-LIFECYCLE-CONTEXT-STORE; extend CreateThreadActionTest (gateway by channel, store update when contextId present), LifecycleContextStoreTest (setDeliveryTargetId), LifecycleContextTest (withDeliveryChannelId).

---

## Per feature

### FEAT-CURSOR-GATHERING

**Feature:** cursor-gathering | Cursor-backed gathering workflow | active | doc_path: features/domain/workflow/cursor-gathering.md | Summary: Luna gathers repository and feature input over Discord, launches Cursor cloud run via launch_cursor_run; lifecycle room Phase 1 (extended context, run record, provisioning actions, create_channel sentinel and room naming).

**REQ-LUNA-001** — Luna bot: Discord mention trigger, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1.
- Statement: Luna registered id luna; workflowRef luna_cursor; guided repo selection, codeChange prompt/capture, confirmation, then launch_cursor_run. LifecycleRunRecord, LifecycleContext, LifecycleContextStore; RuntimeBotInstance; actions create_channel, post_channel_message, provision_bot_instance, create_lifecycle_context, create_thread, launch_cursor_run.
- Acceptance (relevant): Arrietty template workflowRef arrietty_room; luna_cursor step order create_channel → branch → provision_bot_instance → create_lifecycle_context → create_thread → post_channel_message → launch_cursor_run → done; create_thread when channelId has lifecycle context uses that context's bot gateway; after create_thread success, store updated with thread id via setDeliveryTargetId when contextId in state; post_channel_message send target deliveryChannelId or channelId; launch_cursor_run ack directs user to lifecycle room; done message for success is short redirect "Launching now. See <#{{channelId}}>."
- Traceability (subset): ASSET-LIFECYCLE-CONTEXT, ASSET-LIFECYCLE-CONTEXT-STORE, ASSET-CREATE-THREAD-ACTION, ASSET-BOOTSTRAP, ASSET-BOTS-YAML, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION.
- Validation tests: UNIT-CREATE-THREAD-ACTION, UNIT-LIFECYCLE-CONTEXT, UNIT-LIFECYCLE-CONTEXT-STORE, UNIT-CREATE-CHANNEL-ACTION, UNIT-POST-CHANNEL-MESSAGE-ACTION, UNIT-LAUNCH-CURSOR-RUN-ACTION, plus new/updated: CreateThreadAction gateway-by-channel and setDeliveryTargetId when contextId present; LifecycleContextStore setDeliveryTargetId (ignore null/blank/THREAD_CREATE_FAILED); LifecycleContext withDeliveryChannelId.
- **Anti-patterns (critical):** Hardcoding Discord channel in workflow. Storing secrets in state. Do not rely on discordTrigger alone for Luna activation. Do not log Cursor API key, request body, or full response body. Do not assume a single Cursor API error response shape. Do not serialize null fields in Cursor API request payload.

**REQ-WORKFLOW-001** — Workflow state machine and config-driven runners.
- Statement: Workflow/WorkflowResult; WorkflowRunner; configurable workflows with prompt_for_field, capture_field, call_action; create_thread creates Discord thread under parent; storeIn deliveryChannelId; step order and bind from config.
- Acceptance (relevant): create_thread action bind channelId, threadName; storeIn deliveryChannelId; when channelId has lifecycle context use that context's bot gateway (router.getGatewayForChannel(channelId)); after success call LifecycleContextStore.setDeliveryTargetId(contextId, threadId) when contextId in state.
- **Anti-patterns:** (see guardrails; no new keys in spec.)

**Assets (concise):**
- ASSET-LIFECYCLE-CONTEXT | src/main/java/com/vinekeepers/state/LifecycleContext.java | Extended lifecycle run context; add withDeliveryChannelId(String) returning new instance (all fields final).
- ASSET-LIFECYCLE-CONTEXT-STORE | src/main/java/com/vinekeepers/state/LifecycleContextStore.java | Store and resolve by key; add setDeliveryTargetId(contextId, deliveryTargetId): ignore null, blank, THREAD_CREATE_FAILED; else get by contextId, put(context.withDeliveryChannelId(deliveryTargetId)); put() already maintains deliveryTargetToContextId.
- ASSET-CREATE-THREAD-ACTION | src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java | Create Discord thread; gateway = channelId non-blank ? router.getGatewayForChannel(channelId) : null; fallback router.getDefaultGateway(); inject LifecycleContextStore; after success if contextId in bind/state call lifecycleContextStore.setDeliveryTargetId(contextId, threadId).
- ASSET-BOOTSTRAP | src/main/java/com/vinekeepers/core/Bootstrap.java | Register create_thread with LifecycleContextStore: new CreateThreadAction(outboundDeliveryRouter, lifecycleContextStore).
- ASSET-BOTS-YAML | config/bots.yaml | luna_cursor steps 10–17 order: create_channel → branch → provision_bot_instance → create_lifecycle_context → create_thread (storeIn: deliveryChannelId, threadName: "Room updates") → post_channel_message → launch_cursor_run → done message "Launching now. See <#{{channelId}}>." Step 17 done is short redirect only; no {{launchMessage}} in main room.

---

## Doc excerpts (mkdoc, docs_dir: mkdoc)

**cursor-gathering.md (summary):** Luna luna_cursor; provisioning sequence create_channel → branch → provision_bot_instance → create_lifecycle_context → post_channel_message → launch_cursor_run. Plan target: insert create_thread after create_lifecycle_context; done = short redirect. LifecycleContext optional deliveryChannelId; CreateLifecycleContextAction/CreateThreadAction; PostChannelMessageAction send target deliveryChannelId or channelId; CursorCloudRunMonitor sends to thread when deliveryChannelId set.

**Decisions:** Luna config-driven in YAML; Discord mention in routing; repository operations via cursor.fullRun and Cursor adapter.

**Contracts:** LifecycleContext run context channelId, configuredBotId, runtimeBotInstanceId, optional repo/requestText; LifecycleContextStore resolve by key. create_channel, provision_bot_instance, create_lifecycle_context, post_channel_message, launch_cursor_run; create_thread with storeIn deliveryChannelId; bind precedence over state.

**Known issues:** External Cursor/repo integrations; in-memory run tracking.

---

## Plan constraints (from arrietty-ux-refinement-pass.plan.md)

- Do not change LaunchCursorRunAction behavior.
- Do not replace or rename deliveryChannelId.
- Single thread per context only; no multi-thread or multiple delivery targets.
- Minimal store/context API: withDeliveryChannelId, setDeliveryTargetId (null/blank/sentinel ignored).
- create_thread uses lifecycle owner gateway when room has context (getGatewayForChannel(channelId)); else default gateway.
- channelId at step 17 is lifecycle room from create_channel; redirect <#{{channelId}}> correct.

---

## Schema constraints

- Requirement/asset keys: id, title, statement, status, priority, type, behavior, acceptance, traceability, validation, anti_patterns (requirements); id, kind, path, role, requires, feature_ids (assets). No new keys; stay within existing schema.
