# How it works

# Overview

The shipped Luna flow is configured in `config/bots.yaml` as workflow `luna_cursor`. Routing activates the `luna` bot from `discordMention: luna` (and optional `discordAuthors` to restrict which Discord users can trigger Luna), the configurable runner gathers repository and feature inputs across conversation turns, then runs the **full provisioning sequence**; **`launch_cursor_run`** is the authoritative launch path (not `cursor.fullRun`).

# Flow

1. A Discord message referencing `@Luna` matches the `luna` bot through routing (and satisfies `discordAuthors` when that filter is configured).
2. The configured runner executes `luna_cursor`: the first repo step uses `choiceProvider: githubRepos` to show "Use last repo", GitHub repos (from API, sort=updated), and "Custom repo"; if the user picks "Custom repo", a follow-up prompt captures free-text repo; then a prompt/capture for the code change; then a confirmation step (Launch / Edit repo / Edit request / Cancel) with summary (repo, branch, change). Discord can render choices as buttons or a select menu.
3. After the user confirms Launch, the flow runs the provisioning sequence: **create_channel** (Discord gateway `createTextChannel`; channel name derived from state when blank and normalized to Discord-safe; optional bind `lifecycleOwnerBotId`—after create, the action calls `addPermissionOverride` for that bot's Discord user when the gateway supports it; stores channel id in state, or `CHANNEL_CREATE_FAILED` on failure) → **branch** on `channelId == CHANNEL_CREATE_FAILED` to done with failure message → **provision_bot_instance** (stores `RuntimeBotInstance` with generic instance id) → **create_lifecycle_context** → **post_channel_message** (content supports `{{key}}` interpolation from state + bind, bind overrides—e.g. `lifecycleBotName: "Arrietty"` in bind for the room intro) → **launch_cursor_run**. `launch_cursor_run` uses `CursorInstructionComposer` to build the Cursor run prompt (single source; includes /nova-code), launches `POST /v0/agents`, stores `LifecycleRunRecord` and `luna:lastRepo:{authorId}`, and returns an acknowledgement that includes optional status (e.g. "Status: launching") and points to the lifecycle room.
4. `CursorCloudRunMonitor` polls `GET /v0/agents/{id}` and `GET /v0/agents/{id}/conversation` for state changes and assistant feedback.
5. The engine and monitor send launch/progress/final replies back to Discord when the source is Discord.

**Lifecycle room (Phase 1):** The Luna workflow uses the full sequence above. Provisioning workflows (e.g. using the Arrietty template) can use the same actions: `create_channel`, branch on `CHANNEL_CREATE_FAILED`, `provision_bot_instance`, `create_lifecycle_context`, `post_channel_message`, `launch_cursor_run`. Arrietty can optionally use **create_thread** (bind `channelId`, `threadName`; `storeIn: deliveryChannelId`) so Cursor run updates are delivered to the thread; branch on `THREAD_CREATE_FAILED` when needed. Run state uses `LifecycleRunRecord` (optional `deliveryChannelId` for thread delivery); context uses `LifecycleContext` (extended with configuredBotId, runtimeBotInstanceId, optional repo/requestText, optional deliveryChannelId) and `LifecycleContextStore`. `CursorCloudRunMonitor` sends status and feedback to the thread when `deliveryChannelId` is set (messageId null).

# Inputs and outputs

- **Inputs:** Discord event content or mention metadata, configured Luna workflow steps, per-conversation gathering state, and Cursor API credentials from environment.
- **Outputs:** Updated gathering state, in-memory run-tracking state, Cursor-backed repository side effects (feature branch and PR), and workflow/monitor replies delivered to Discord.

