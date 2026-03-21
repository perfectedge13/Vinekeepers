# Change log

# Entries

## 2026-03-21

- **Cursor launch model resolution:** Documented per-workflow-step **`model`** on **`call_action`**, bind override keys, bot **`persona.model`** default via **`WorkflowRunnerFactory`**, and **`CURSOR_MODEL`** fallback; **`launch_cursor_run`** uses **`CursorLaunchModel.resolveForLaunch`**. README, architecture, configuring-bots, workflow-steps, and cursor-gathering contracts/how-it-works updated.

## 2026-03-13

- **Arrietty room UX and message-first capture:** Arrietty template in `config/bots.yaml` uses `workflowRef: arrietty_room` with **message-first** flow: first step is `capture_field` (e.g. `storeIn: roomAction`) with optional **trimAndLower: true** for room name UX (trim and lowercase user input). Router ownership warning when lifecycle owner lacks handlesOwnedSpaces. No hardcoded bot ids in Router or engine.
- **Arrietty thread-based updates:** Routing fix for delivery by target (channel or thread id). **CreateThreadAction** (`create_thread` step): bind `channelId`, `threadName`; `storeIn` (e.g. `deliveryChannelId`) stores thread id or `THREAD_CREATE_FAILED`. **LifecycleContext** and **CreateLifecycleContextAction** support optional **deliveryChannelId** (excludes sentinel). **PostChannelMessageAction** send target = `deliveryChannelId` or `channelId` (bind then state); **LaunchCursorRunAction** passes `deliveryChannelId` into **LifecycleRunRecord**. **CursorCloudRunMonitor** sends status/feedback to the thread when `deliveryChannelId` is set (messageId null). **OutboundDeliveryRouter** resolves sender by delivery target id (thread or channel). Arrietty workflow config may include `create_thread` with `storeIn: deliveryChannelId` and optional branch on `THREAD_CREATE_FAILED`.
- **Arrietty UX refinement:** **create_thread** runs **after create_lifecycle_context** in the provisioning sequence (e.g. in `luna_cursor`); CreateThreadAction uses the **lifecycle owner gateway** when the channel has a lifecycle context. **LifecycleContext.withDeliveryChannelId** and **LifecycleContextStore.setDeliveryTargetId** (contextId, threadId) update the delivery target after thread creation; setDeliveryTargetId ignores null, blank, and THREAD_CREATE_FAILED. **Done step** may use a short **main-room redirect** message (e.g. `"Launching now. See <#{{channelId}}>."`).

## 2026-03-12

- **CreateChannelAction lifecycle owner and permission overwrite:** `create_channel` step bind may include `lifecycleOwnerBotId`. After creating the channel via gateway `createTextChannel`, the action calls `addPermissionOverride` for that bot's Discord user (resolved via router `getDiscordUserIdForBot(lifecycleOwnerBotId)`) when the gateway supports it, so the lifecycle room bot (e.g. Arrietty) has explicit permission on the new channel. Room naming and sentinel `CHANNEL_CREATE_FAILED` unchanged. See CreateChannelActionTest.runWithRouterAndLifecycleOwnerBotId_addsPermissionOverwriteAfterCreate.

## 2026-03-10

- **Phase 1 correctness (docs):** Bind precedence for call_action (bind overrides state). Merged interpolation for `post_channel_message`: content uses state then bind (bind overrides), e.g. `lifecycleBotName` in bind in `config/bots.yaml` for room intro. `create_channel` normalizes channel name to Discord-safe before gateway create; returns `CHANNEL_CREATE_FAILED` on failure. `launch_cursor_run` acknowledgement includes optional status (e.g. "Status: launching"). README and workflow-steps/cursor-gathering/Discord docs updated.
- **Phase 1 completion:** Luna workflow now uses the full provisioning sequence: `create_channel` → branch on `CHANNEL_CREATE_FAILED` → `provision_bot_instance` → `create_lifecycle_context` → `post_channel_message` → `launch_cursor_run`. `launch_cursor_run` is the authoritative launch path; `cursor.fullRun` is not used in luna_cursor. **CursorInstructionComposer** is the single source for the Cursor run prompt (includes /nova-code). Room naming: when channel name is blank, it is derived from state (project + codeChange). `create_channel` returns sentinel `CHANNEL_CREATE_FAILED` on gateway failure. **LifecycleContext** extended with `configuredBotId`, `runtimeBotInstanceId`, optional `repo`/`requestText`. **Provision_bot_instance** uses generic instance id. README and impacted docs updated.

## 2026-03-09

- **Lifecycle room Phase 1:** Added `LifecycleContext`, `LifecycleContextStore`, `RuntimeBotInstance`; workflow actions `create_channel`, `post_channel_message`, `provision_bot_instance`, `create_lifecycle_context`, `launch_cursor_run`. Arrietty bot template in config for per-channel lifecycle room instances; Discord gateway `createTextChannel(guildId, channelName)` for channel creation.
- **Cursor API error visibility:** CursorCloudAdapterImpl constructor masks API key in logs; transport/send logging added for diagnostics without leaking key or body. CursorCloudException message is surfaced to callers (e.g. CursorCloudAdapterImplTest.transportExceptionSurfacesMessage); CursorCloudTransport throws Exception so adapter can wrap and expose cause.
- **Cursor adapter bug-fix:** Robust non-2xx error parsing (multiple response shapes: nested error.message/code, top-level message, plain text, empty body); safe per-request diagnostics at DEBUG (URI, key configured, model, repo, branch); JSON request body uses NON_NULL; CursorCloudAdapterImplTest extended with error-shape and auth tests. Auth tests (Bearer token for getAgent and launchAgent); non-2xx empty JSON object and non-JSON body tests (non2xxEmptyJsonObjectSurfacesGenericMessage, non2xxNonJsonBodySurfacesInException) added.
- **Edit-reprompt and config alignment:** Luna and configured workflow use `ConfigurableWorkflowRunner` and `ConfigurableWorkflowState`; `StepResult.clearKeys` supports edit-reprompt so state keys can be cleared before re-prompting. `config/bots.yaml` and workflow runner/state assets updated for consistency.
- **REQ-LUNA-001 acceptance (confirmation-branch clear):** Confirmation step (Launch / Edit repo / Edit request / Cancel) clears branch/state when the user chooses Edit repo or Edit request so the flow returns to repo or request input without carrying prior confirmation state.
- **Luna discordAuthors:** Luna routing in `config/bots.yaml` can include optional `discordAuthors` (e.g. `novawilde13_72571`) so only listed Discord users can trigger Luna when they mention the bot; gateway and NormalizedEventContext supply author data for routing.

## Prior

- Renamed the documented capability from a Luna-only dossier to the spec-defined `cursor-gathering` feature.
- Captured the configured `luna_cursor` flow, Cursor adapter handoff, and Discord reply path as one feature.

