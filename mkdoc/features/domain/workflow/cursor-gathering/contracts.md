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

- **`LifecycleContext`:** run context with channelId, externalRunId, configuredBotId, runtimeBotInstanceId, and optional repo/requestText; indexed by channelId and externalRunId.
- **`LifecycleContextStore`:** store and resolve lifecycle contexts by key (e.g. channelId, externalRunId).
- **`RuntimeBotInstance`:** provisioned bot instance (generic instanceId, templateBotId, displayName, channelId); e.g. Arrietty template.
- **Workflow actions (call_action):** Bind has precedence over state for action inputs. `create_channel` (Discord gateway `createTextChannel`; returns channel id or `CHANNEL_CREATE_FAILED` on failure; channel name derived from state when blank and normalized to Discord-safe before create), branch on `channelId == CHANNEL_CREATE_FAILED` to done, `provision_bot_instance`, `create_lifecycle_context`, `post_channel_message` (content interpolated from state + bind, bind overrides—e.g. `lifecycleBotName` in bind for room intro), `launch_cursor_run` (authoritative launch path for luna_cursor; returns ack with optional status e.g. "Status: launching").
- **`CursorInstructionComposer`:** single source for Cursor run instruction text (repo, baseBranch, change); includes /nova-code; used by `LaunchCursorRunAction` and `CursorFullRunTool`.

# Interfaces

- **`CursorCloudAdapter`:** abstraction over Cursor Cloud Agent launch, status, conversation, and follow-up operations.
- **`CursorFullRunTool`:** tool surface that launches the remote run and stores `LifecycleRunRecord`.
- **`CursorCloudRunMonitor`:** polling component that turns Cursor state changes into Discord replies.
- **`CursorCloudGatheringWorkflow`:** legacy workflow class retained for tests while production uses the configured flow.

