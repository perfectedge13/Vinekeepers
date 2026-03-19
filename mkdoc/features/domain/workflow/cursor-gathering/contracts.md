# Contracts

# APIs

This feature calls the official Cursor Cloud Agents API:

- `POST /v0/agents`
- `GET /v0/agents/{id}`
- `GET /v0/agents/{id}/conversation`
- `POST /v0/agents/{id}/followup` (supported by the adapter contract)

# Schemas

Luna configuration in `config/bots.yaml` supplies bot routing, `workflowRef: luna_cursor`, and the ordered gathering steps. `ConfigurableWorkflowState` stores the conversational gather state, while `LifecycleRunRecord` stores the launched run identity and Cursor feedback snapshot.

# Cursor adapter behavior

- **Authentication:** `Authorization: Bearer <CURSOR_API_KEY>`; launch fails fast when `CURSOR_API_KEY` is not configured.
- **Request payload:** Serialized with Jackson `NON_NULL` so null fields are omitted (avoids API errors).
- **Non-2xx handling:** `extractErrorMessageAndCode` parses: error as string, nested `error.message`/`error.code`, top-level `message`, plain text body, or empty body (generic message with status).
- **Diagnostics:** At DEBUG, logs safe per-request data (URI, key configured, model, repo, branch); never logs API key, request body, or full response body.

# Lifecycle room (Phase 1)

- **`LifecycleContext`:** run context with channelId, externalRunId, configuredBotId, runtimeBotInstanceId, optional repo/requestText, and optional deliveryChannelId; **withDeliveryChannelId(id)** returns new instance with that delivery target for store updates after create_thread; indexed by channelId and externalRunId.
- **`LifecycleContextStore`:** store and resolve lifecycle contexts by key (e.g. channelId, externalRunId); **setDeliveryTargetId(contextId, deliveryTargetId)** updates context delivery target (ignores null, blank, THREAD_CREATE_FAILED); used by create_thread after success.
- **`RuntimeBotInstance`:** provisioned bot instance (generic instanceId, templateBotId, displayName, channelId); e.g. Arrietty template.
- **Multi-bot feature room:** **FeatureRoomState** and **FeatureRoomStateStore** hold participant state by channel and delivery target. **FeatureRoomStateStore.getParticipantBotIds** returns participant configuredBotIds in **PlanningRole.ordinal()** order (Orchestrator, Architect, Auditor, Scribe). Workflow state key **featureRoomParticipants** (List&lt;Map&gt;; keys: role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator) produced by **provision_room_participants** (storeIn: featureRoomParticipants), consumed by **initialize_feature_room_state**. **Response policy:** Router returns four participant configuredBotIds when FeatureRoomState exists; only **Arrietty (Orchestrator)** may produce a public reply by default; architect, auditor, scribe reply when invoked via action (e.g. **post_channel_message** with **asRole** or **asBotId**). **OutboundDeliveryRouter** supports **sendAs(botId)** and **sendAsRole(role)**; role resolved from FeatureRoomState participants.
- **Workflow actions (call_action):** Bind has precedence over state for action inputs. **SpaceOperations** returns typed **CreateRoomResult**/ **CreateThreadResult**; actions translate to id or sentinel for `run()`. `create_channel` builds **CreateRoomRequest** via **CreateRoomRequest.from(Event, state, bind)**; optional **participantBotIds** (list) or **lifecycleOwnerBotId**; delegates to **SpaceOperations.createRoom**; Discord applies permission overwrites for each participant. **provision_room_participants** (storeIn: featureRoomParticipants) provisions four participants (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE) into state. **initialize_feature_room_state** builds **FeatureRoomState** from featureRoomParticipants and puts it in **FeatureRoomStateStore**; when featureId/featureSlug are missing, generates featureId = feat- + 12 hex and featureSlug from initialRequest/repo sanitized; preserves when supplied. **post_channel_message** send target: **deliveryChannelId** or **channelId** (bind then state), or explicit **target: room | thread** or **targetChannelId** (channel or thread id); THREAD_CREATE_FAILED fallback to channelId. Supports **asRole** (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE) or **asBotId**; **OutboundDeliveryRouter.sendAsRole** / **sendAs** resolve role to bot from FeatureRoomStateStore. `create_thread` builds **CreateThreadRequest**; delegates to **SpaceOperations.createThread**; uses lifecycle owner gateway when channel has context; on success **LifecycleContextStore.setDeliveryTargetId**(contextId, threadId); `storeIn` e.g. `deliveryChannelId`. Branch on `channelId == CHANNEL_CREATE_FAILED` to done. Also: `provision_bot_instance`, `create_lifecycle_context`, **provision_room_participants** (storeIn: featureRoomParticipants), **initialize_feature_room_state**, `post_channel_message` (content from state + bind; optional **asRole**/ **asBotId** for sendAs/sendAsRole), `launch_cursor_run` (authoritative; ack with optional status). Done step may use main-room redirect message (e.g. `"Launching now. See <#{{channelId}}>."`).
- **`CursorInstructionComposer`:** single source for Cursor run instruction text (repo, baseBranch, change); includes /nova-code; used by `LaunchCursorRunAction` and `CursorFullRunTool`.

# Interfaces

- **`CursorCloudAdapter`:** abstraction over Cursor Cloud Agent launch, status, conversation, and follow-up operations.
- **`CursorFullRunTool`:** tool surface that launches the remote run and stores `LifecycleRunRecord`.
- **`CursorCloudRunMonitor`:** polling component that turns Cursor state changes into Discord replies.
- **`CursorCloudGatheringWorkflow`:** legacy workflow class retained for tests while production uses the configured flow.

