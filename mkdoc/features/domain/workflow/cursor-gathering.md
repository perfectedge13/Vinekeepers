# Cursor-backed gathering workflow

# Status

active

# Summary

Luna bot gathering workflow and Cursor Cloud execution (REQ-LUNA-001). The shipped `luna` bot stays config-driven in `config/bots.yaml`, activates from `discordMention: luna` (and optional `discordAuthors`), runs configured workflow `luna_cursor` to gather repository and feature inputs across multiple Discord turns, then uses the **full provisioning sequence** for launch. **Guided repo selection:** the first step uses `choiceProvider: githubRepos` to present GitHub repos (from GitHub API, sort=updated), "Use last repo", and "Custom repo"; last repo is stored per author after a successful launch. A **confirmation step** (Launch / Edit repo / Edit request / Cancel) runs before the provisioning sequence. **After Launch**, the flow runs: `create_channel` (Discord gateway `createTextChannel`; returns channel id or `CHANNEL_CREATE_FAILED` on failure; room name derived from state when blank) → branch on `channelId == CHANNEL_CREATE_FAILED` to done with failure → `provision_bot_instance` → `create_lifecycle_context` → `post_channel_message` → `launch_cursor_run`. **`launch_cursor_run` is the authoritative launch path** for Luna; `cursor.fullRun` is not used in `luna_cursor`. The Cursor run prompt is built by **`CursorInstructionComposer`** (single source; includes /nova-code). **Per-step model override:** each workflow step can optionally declare `model` (string model id shorthand or object `{ provider, modelId }`). At runtime, only `openai` provider is accepted; unsupported providers fail fast with a workflow configuration error. For `call_action` steps, model metadata is passed in `__stepModel`, and `launch_cursor_run` uses that model for the launch request (falling back to `CURSOR_MODEL` env when absent). `LifecycleContext` is extended with `configuredBotId`, `runtimeBotInstanceId`, and optional `repo`/`requestText`. Vinekeepers stores `LifecycleRunRecord` and `luna:lastRepo:{authorId}`, polls Cursor, and relays launch/progress/PR updates back to Discord. The **Arrietty** bot is the template for per-channel lifecycle room instances; `RuntimeBotInstance` uses a generic instance id. The **arrietty_room** workflow is **message-first**: first step is `capture_field` (e.g. `storeIn: roomAction`, `trimAndLower: true`), then a `branch` step routes by value (status, retry, close, blank, else) to the appropriate done step. In the Luna provisioning sequence, **create_thread** runs **after create_lifecycle_context**; CreateThreadAction uses the **lifecycle owner gateway** when the channel has a context and updates **LifecycleContextStore** via **setDeliveryTargetId**(contextId, threadId); **LifecycleContext.withDeliveryChannelId** supplies the delivery target for the store. Arrietty can use a **create_thread** step (bind `channelId`, `threadName`; `storeIn: deliveryChannelId`) so Cursor updates are delivered to the thread. The **done step** may use a short **main-room redirect** message (e.g. `"Launching now. See <#{{channelId}}>."`). `LifecycleRunRecord` and `CreateLifecycleContextAction` support optional **deliveryChannelId** (excludes `THREAD_CREATE_FAILED`); **PostChannelMessageAction** send target is `deliveryChannelId` or `channelId` (bind then state); **CursorCloudRunMonitor** sends status/feedback to the thread when `deliveryChannelId` is set (messageId null).

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-LUNA-STATE | Luna per-conversation state (project, code change, step) | src/main/java/com/vinekeepers/workflow/GatheringState.java |
| ASSET-LUNA-WORKFLOW | Luna workflow class used by tests while production Luna uses configured workflow `luna_cursor` | src/main/java/com/vinekeepers/workflow/CursorCloudGatheringWorkflow.java |
| ASSET-CURSOR-ADAPTER | Cursor Cloud Agents API adapter interface | src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapter.java |
| ASSET-CURSOR-ADAPTER-IMPL | Cursor Cloud Agents API HTTP client implementation (env-based config); robust non-2xx error parsing (extractErrorMessageAndCode for error string, nested error.message/code, top-level message, plain text, empty); safe request diagnostics (debug URI, key masked, model, repo, branch); NON_NULL payload serialization; Bearer auth; logs transport exceptions and config without leaking key or body | src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapterImpl.java |
| ASSET-LUNA-RUN-STATE | Generic run record for lifecycle Cursor run (indexed by channelId, externalRunId); optional deliveryChannelId for thread delivery; Discord reply target | src/main/java/com/vinekeepers/core/cursor/LifecycleRunRecord.java |
| ASSET-CURSOR-RUN-MONITOR | Poll Cursor status/conversation and relay updates to Discord; when LifecycleRunRecord has deliveryChannelId set, sends to that thread (messageId null) | src/main/java/com/vinekeepers/core/cursor/CursorCloudRunMonitor.java |
| ASSET-CURSOR-AGENT-LAUNCH-REQUEST | Request payload model for launching a Cursor Cloud agent run | src/main/java/com/vinekeepers/core/cursor/CursorAgentLaunchRequest.java |
| ASSET-CURSOR-AGENT-CONVERSATION | Conversation response model for Cursor Cloud agent feedback | src/main/java/com/vinekeepers/core/cursor/CursorAgentConversation.java |
| ASSET-CURSOR-AGENT-DETAILS | Details model for Cursor Cloud agent status lookups | src/main/java/com/vinekeepers/core/cursor/CursorAgentDetails.java |
| ASSET-CURSOR-AGENT-LAUNCH-RESULT | Launch result model returned after starting a Cursor Cloud agent run | src/main/java/com/vinekeepers/core/cursor/CursorAgentLaunchResult.java |
| ASSET-CURSOR-AGENT-MESSAGE | Message model representing Cursor Cloud agent conversation entries | src/main/java/com/vinekeepers/core/cursor/CursorAgentMessage.java |
| ASSET-CURSOR-CLOUD-TRANSPORT | HTTP transport abstraction for Cursor Cloud adapter calls | src/main/java/com/vinekeepers/core/cursor/CursorCloudTransport.java |
| ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE | Transport response model used by Cursor Cloud adapter calls | src/main/java/com/vinekeepers/core/cursor/CursorCloudTransportResponse.java |
| ASSET-CURSOR-CLOUD-EXCEPTION | Exception type for Cursor Cloud transport and adapter failures | src/main/java/com/vinekeepers/core/cursor/CursorCloudException.java |
| ASSET-CURSOR-FULL-RUN-TOOL | Tool wrapper that launches a Cursor Cloud Agent run and stores launch state and last repo per author | src/main/java/com/vinekeepers/tools/CursorFullRunTool.java |
| ASSET-GITHUB-REPOS-CHOICE-PROVIDER | Fetches user repos from GitHub API for guided repo selection; uses GITHUB_TOKEN | src/main/java/com/vinekeepers/providers/GitHubReposChoiceProvider.java |
| ASSET-LIFECYCLE-CONTEXT | Lifecycle run context (channelId, configuredBotId, runtimeBotInstanceId, optional repo/requestText, optional deliveryChannelId); withDeliveryChannelId(id) for store updates after create_thread | src/main/java/com/vinekeepers/state/LifecycleContext.java |
| ASSET-LIFECYCLE-CONTEXT-STORE | Store and resolve lifecycle contexts by key; setDeliveryTargetId(contextId, deliveryTargetId) used by create_thread after success (ignores null/blank/sentinel) | src/main/java/com/vinekeepers/state/LifecycleContextStore.java |
| ASSET-RUNTIME-BOT-INSTANCE | Runtime instance of a bot provisioned from a template (e.g. Arrietty); instanceId, templateBotId, displayName, channelId | src/main/java/com/vinekeepers/bot/RuntimeBotInstance.java |
| ASSET-CREATE-CHANNEL-ACTION | Workflow action to create a Discord text channel via gateway for lifecycle room | src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java |
| ASSET-POST-CHANNEL-MESSAGE-ACTION | Workflow action to post a message to a Discord channel; send target = deliveryChannelId or channelId (bind then state); treats THREAD_CREATE_FAILED as fallback to channelId | src/main/java/com/vinekeepers/workflow/actions/PostChannelMessageAction.java |
| ASSET-PROVISION-BOT-INSTANCE-ACTION | Workflow action to provision a runtime bot instance (e.g. Arrietty) bound to a channel | src/main/java/com/vinekeepers/workflow/actions/ProvisionBotInstanceAction.java |
| ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION | Workflow action to create and store a lifecycle context for a run; reads optional deliveryChannelId from bind/state (excludes THREAD_CREATE_FAILED) | src/main/java/com/vinekeepers/workflow/actions/CreateLifecycleContextAction.java |
| ASSET-CREATE-THREAD-ACTION | Workflow action to create a Discord thread; bind channelId, threadName, optional contextId. Uses lifecycle owner gateway when channel has context; on success when contextId present updates LifecycleContextStore via setDeliveryTargetId(contextId, threadId). storeIn (e.g. deliveryChannelId) stores thread id or THREAD_CREATE_FAILED | src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java |
| ASSET-LAUNCH-CURSOR-RUN-ACTION | Workflow action to launch a Cursor cloud run and register run record; passes optional deliveryChannelId into LifecycleRunRecord (authoritative launch path for luna_cursor) | src/main/java/com/vinekeepers/workflow/actions/LaunchCursorRunAction.java |
| ASSET-CURSOR-INSTRUCTION-COMPOSER | Single source for Cursor run instruction text (repo, baseBranch, change; includes /nova-code) | src/main/java/com/vinekeepers/core/cursor/CursorInstructionComposer.java |
| ASSET-BOTS-YAML | Bot definitions YAML including Luna routing and configured workflow reference | config/bots.yaml |
| ASSET-BOOTSTRAP | Register Luna workflow runner, tools, and adapter dependencies | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-ENGINE | Deliver workflow replies to Discord when the source is Discord | src/main/java/com/vinekeepers/core/VinekeepersEngine.java |

# Sub-pages

- [How it works](cursor-gathering/how-it-works.md)
- [Change log](cursor-gathering/change-log.md)
- [Known issues](cursor-gathering/known-issues.md)
- [Decisions](cursor-gathering/decisions.md)
- [Contracts](cursor-gathering/contracts.md)
- [Tests](cursor-gathering/tests.md)
- [Diagrams](cursor-gathering/diagrams.md)

