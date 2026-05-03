# Vinekeepers

Vinekeepers is an AI-based bots collection. The bot core provides bootstrap, configuration (YAML), engine, tool registry, and audit logging.

## Build and run

- **Compile:** `mvn compile`
- **Tests:** `mvn test` (unit tests cover core components: config, core, env, events, state, bot, workflow, connectors).
- **Spec schema gate:** `npm run validate-specs`
- **Spec drift gate:** `npm run validate-drift`
- **Docs gate:** `npm run validate-docs`
- **Main class:** `com.vinekeepers.VinekeepersApp` — run from your IDE or after packaging (e.g. `mvn package` then run the JAR with dependencies on the classpath).

Requirements: Java 21, Maven, Node 18+ for the spec gates.

### Docker

Build and run in a container (Java 21 runtime, Alpine-based):

```bash
docker build -t vinekeepers .
docker run --rm -e LUNA_DISCORD_TOKEN=your_token -e ARRIETTY_DISCORD_TOKEN=your_arrietty_token vinekeepers
```

Or use Compose (optionally add a `.env` file with `DISCORD_BOT_TOKEN=...` and uncomment `env_file: .env` in `compose.yaml`):

```bash
docker compose up -d
```

To override config or env from the host when using `docker run`, mount volumes:

```bash
docker run --rm -v "$(pwd)/config:/app/config" -v "$(pwd)/.env:/app/.env" vinekeepers
```

Optional: set `JAVA_OPTS` (e.g. `-Xmx512m`) via `-e JAVA_OPTS=...`.

Bot definitions and routing are in `config/bots.yaml`. Discord identity is **bot-config driven**: each bot sets `discordTokenEnvKey` to the *name* of an env var that holds that bot's token (never put raw tokens in config). Optional top-level `defaultDiscordTokenEnvKey` is used when a bot has no `discordTokenEnvKey`. Set the same env var names in `.env` with the actual tokens. The default config uses `discordTokenEnvKey` per bot (e.g. `LUNA_DISCORD_TOKEN`, `ARRIETTY_DISCORD_TOKEN`) so lifecycle rooms can use Arrietty's identity. Bots that are not in routing (e.g. Arrietty as template) get an **optional outbound-only gateway**—they connect for sending only, with no event listener. Outbound delivery is routed by **OutboundDeliveryRouter**: it resolves the sender from the delivery target and **lifecycle context** (`configuredBotId`). **getGatewayForChannel** returns the configured bot's gateway for lifecycle channels; if that bot has no gateway, it returns null (no fallback to default). For lifecycle rooms, if the resolved bot has no registered sender (e.g. that bot's token was not set), the router logs an error and does not send—no silent fallback. Each bot can set `workflow.type` (for example `stub` or `configured`) plus runtime options such as `conversationMode` and `sessionKeyStrategy`; Bootstrap creates a `WorkflowRunner` per bot via `WorkflowRunnerFactory`, and the engine uses `runResult(event, stateStore, botId)` to execute it. Routing filters support keys such as `discordTrigger`, `discordMention`, and `discordAuthors`; `discordMention` matches case-insensitively against normalized Discord `@mentions` from message text or connector-provided mention metadata; optional `discordAuthors` restricts which Discord users can trigger the bot: list entries can be a **numeric Discord user id** (stable, matches actorId) or a **username** (matches actorUsername, case-insensitive). For **ownership-based routing**, a bot can set **handlesOwnedSpaces: true** in config; when a Discord channel has a lifecycle context and that bot is the owner (configuredBotId), the Router returns only that bot for events in that channel (single-owner precedence), so e.g. Arrietty-owned rooms receive only Arrietty. Follow-up messages in the same session (e.g. without a mention) continue the workflow when the bot is waiting for input for that session key. For `configured`, use `workflow.params.workflowRef` to reference a workflow id from the YAML `workflows:` section, which defines a step DSL with `ask_input`, `prompt_for_field`, `capture_field` (optional `trimAndLower: true` to trim and lowercase the stored value), `call_action`, `branch`, and `done`. `call_action` supports optional per-step `model` for direct LLM actions (`launch_cursor_run`, `cursor.fullRun`) and falls back to the bot default model (`model.modelId`) when omitted; non-LLM steps reject `model`. Bot model config can include optional `supportedStepModels` allow-list to fail fast on unsupported per-step models during runner construction. **Rich interactions:** intent model (e.g. `intent: present_choices`, `confirm_action`) and step options (`choices`, `confirmLabel`, `cancelLabel`, `fields`); engine delivers replies via the connector **sink registry** (lifecycle operations: respondImmediately, sendFollowUp, updateMessage); **Discord lifecycle** is adapter-owned (ack/defer within platform window, auto-defer when needed). Channel replies use the gateway **send** overload so initial messages can include **components** (buttons, select menus) when the response has an intent (e.g. present_choices, confirm_action). See [Architecture](mkdoc/architecture.md) and [Discord](mkdoc/features/domain/connectors/discord.md) in the docs.

Configured workflows persist per-session `ConfigurableWorkflowState`. `CallActionStep` prefers `ToolRunner` when a tool is registered and falls back to `WorkflowActionRegistry` for legacy actions; for tool-backed steps it also forwards event metadata and the session key so tools can launch external work and correlate replies. After the workflow runs, the engine can invoke a bot reasoner with the workflow reply context, current session state, and the last normalized user message; any proposed tool calls are executed through `ToolRunner` under the bot's `ToolPolicy`.

Example: Luna has `workflow.type: configured` with `workflow.params.workflowRef: luna_cursor` and a `discordMention: "luna"` routing rule (optionally `discordAuthors`). The shipped workflow is multi-turn: **guided repo selection** (choiceProvider `githubRepos` — GitHub API + "Use last repo" + "Custom repo"), code change prompt/capture, **confirmation step** (Launch / Edit repo / Edit request / Cancel), then `launch_cursor_run`; Discord can show buttons or a select menu for choices. Edit repo and Edit request use branch steps with `clear` (clearKeys) so the runner clears those state keys before advancing, then reprompts for the cleared fields. Set `GITHUB_TOKEN` in `.env` for repo listing. Status, feedback, and PR links are reported back to Discord while the run is active.

**Lifecycle room (Phase 1):** The **Arrietty** bot in `config/bots.yaml` is the template for per-channel lifecycle room instances; it has `workflowRef: arrietty_room` and `handlesOwnedSpaces: true` so it owns inbound events in channels it provisions. The **arrietty_room** workflow is **message-first**: the first step is `capture_field` (e.g. `storeIn: roomAction`, optional `trimAndLower: true` to normalize), then a `branch` step routes by value (status, retry, close, blank, else). The workflow may include a **create_thread** step (bind `channelId`, `threadName`; `storeIn` e.g. `deliveryChannelId`; returns thread id or `THREAD_CREATE_FAILED`) with optional branch on `THREAD_CREATE_FAILED`. For `branch`, blank/empty state is not truthy; use `when: { key, value: "" }` to match blank input explicitly. When a channel has a lifecycle context but the owner bot does not have `handlesOwnedSpaces`, the Router **logs a warning** and uses filter-based routing instead of single-owner. Luna’s workflow uses the full provisioning sequence: `create_channel` (Discord gateway `createTextChannel`; returns channel id or `CHANNEL_CREATE_FAILED` on failure; room name is derived from state when not provided—repo segment is the **repository name only**, no owner/username—and **normalized** to Discord-safe format before create). The step accepts **lifecycleOwnerBotId** in its bind (e.g. `bind: { lifecycleOwnerBotId: arrietty }`); after create, **permission overwrites** are applied for that bot's Discord user so the lifecycle owner can send in the new channel. Then: branch on `channelId == CHANNEL_CREATE_FAILED` to done with failure message → `provision_bot_instance` (stores `RuntimeBotInstance` with a generic instance id) → `create_lifecycle_context` → `create_thread` (uses lifecycle owner bot's gateway; updates context with thread id) → `post_channel_message` → `launch_cursor_run`. The main intake room **done** message is a short redirect: `"Launching now. See <#{{channelId}}>."` For **call_action** steps, **bind has precedence over state** for action inputs. `post_channel_message` send target is **deliveryChannelId or channelId** (bind then state); if `deliveryChannelId` is the `THREAD_CREATE_FAILED` sentinel, it falls back to channelId. It interpolates its `content` from a merged map (state then bind; bind overrides), so you can set `lifecycleBotName` in the step bind (e.g. `"Arrietty"`) and use `{{lifecycleBotName}}` in the content for the room intro. **`launch_cursor_run` is the authoritative launch path** for Luna; it returns an acknowledgement that includes repo/branch/agent URL and optional **status** (e.g. "Status: launching"); `cursor.fullRun` is not used in the luna_cursor flow. The Cursor run prompt is built by **`CursorInstructionComposer`** (single source; includes /nova-code). **`CursorCloudRunMonitor`** polls Cursor for run status and feedback and relays updates (running state, PR URL, final summary) to Discord; when **LifecycleRunRecord** has **deliveryChannelId** set, it sends to that thread (messageId null). `LifecycleContext` is extended with `configuredBotId`, `runtimeBotInstanceId`, optional `repo`/`requestText`, and optional **deliveryChannelId** (thread id; `THREAD_CREATE_FAILED` excluded from context). **OutboundDeliveryRouter** resolves the sender via **LifecycleContextStore.getByDeliveryTargetId** (channel or thread id); if that bot's sender is unavailable (e.g. no token), lifecycle delivery fails clearly (error log, no send). `LifecycleRunRecord` and `LifecycleContext` (in `LifecycleContextStore`) track runs and context by channelId and externalRunId; the store also indexes by delivery target id for router lookups. `post_channel_message` sends to the channel (or thread when deliveryChannelId is set) from workflow state after creating the lifecycle room.

## Documentation

Docs (MkDocs) use the configured docs root in `.cursor/project.yml` (`paths.docs_dir`, currently `mkdoc`). To build and serve the docs locally (including Mermaid diagrams on the Architecture page), install the docs stack then run MkDocs from the project root:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

Then open http://127.0.0.1:8000 (or the URL MkDocs prints).

## Project layout

| Package | Purpose |
|---------|--------|
| `com.vinekeepers` | Entrypoint: `VinekeepersApp` wires `Bootstrap` |
| `com.vinekeepers.core` | Engine (workflow first, then reasoner/tool execution), `Bootstrap` (loadConfig → factory creates runners, `registerRunner`, passes `LifecycleContextStore` and `handlesOwnedSpaces` map to Router) |
| `com.vinekeepers.config` | `ConfigLoader` (YAML), `BotConfig` |
| `com.vinekeepers.tools` | `Tool`, `ToolRegistry`, `ToolRunner`, Luna launch tool (`CursorFullRunTool`) |
| `com.vinekeepers.audit` | `AuditLog`, `AuditRecorder` |
| `com.vinekeepers.util` | Shared utilities |
| `com.vinekeepers.reasoner` | Reasoner interface and stubs |
| `com.vinekeepers.workflow` | Workflow interface, `WorkflowRunner`, `WorkflowRunnerFactory`, `StubWorkflowRunner`, `ConfigurableWorkflowRunner`, `WorkflowActionRegistry`, `WorkflowDefinition`, `ConfigurableWorkflowState`, session key helpers, step types (`AskForInputStep`, `PromptForFieldStep`, `CaptureFieldFromEventStep`, `CallActionStep`, `BranchStep`, `DoneStep`), actions **CreateThreadAction** (`create_thread`; returns thread id or `THREAD_CREATE_FAILED`), **PostChannelMessageAction** (`post_channel_message`; send target = deliveryChannelId or channelId), legacy `GatheringState` and `CursorCloudGatheringWorkflow` test harness |
| `com.vinekeepers.core.cursor` | Cursor Cloud Agent API adapter (`CursorCloudAdapter`, `CursorCloudAdapterImpl`), **CursorCloudRunMonitor** (polls status, relays to Discord; when `LifecycleRunRecord.deliveryChannelId` set, sends to thread), lifecycle run record (`LifecycleRunRecord`, optional `deliveryChannelId`), `CursorInstructionComposer` (prompt for Cursor runs, includes /nova-code) |
| `com.vinekeepers.bot` | Bot definitions, `RuntimeBotInstance` (provisioned per-channel from template, e.g. Arrietty), conversation/runtime options, normalized routing, persona; `Router` uses `LifecycleContextStore` and per-bot `handlesOwnedSpaces` for ownership-based routing |
| `com.vinekeepers.events` | Event bus, sources, subscribers |
| `com.vinekeepers.connectors` | Event sources (e.g. GitHub, Discord); JDA-backed Discord gateway (`createTextChannel`, `createThreadChannel` for lifecycle rooms); **OutboundDeliveryRouter** (resolves sender via `LifecycleContextStore.getByDeliveryTargetId` for channel or thread id; per-bot senders when `discordTokenEnvKey` is set); `DiscordReplySender` (legacy) and `DiscordAppReplySink` for workflow replies and rich interactions |
| `com.vinekeepers.interactions` | Platform-neutral reply model: `OutboundResponse`, `ResponseIntent`, `ReplyTarget`, `Capabilities`, and connector contract `AppReplySink` (lifecycle operations) |
| `com.vinekeepers.state` | State store; `LifecycleContext` (optional `deliveryChannelId`; `THREAD_CREATE_FAILED` excluded), `LifecycleContextStore` (per-run context by channelId and externalRunId; `getByDeliveryTargetId` for router) |
| `com.vinekeepers.providers` | Dynamic choice providers (e.g. `GitHubReposChoiceProvider` for guided repo selection) |
| `com.vinekeepers.env` | `EnvLoader` (load .env into system properties), `Env.get(key, default)` |

Source: `src/main/java`, tests: `src/test/java`. Docs (MkDocs) live under the configured docs root (currently `mkdoc`); see **Documentation** above for serving locally.

## Environment

The app loads a `.env` file (if present) into system properties at startup. A sample contract lives in `.env.example`.

Required for Luna on Discord:

- **Discord token:** Set env vars whose *names* match the keys in `config/bots.yaml`: each bot's `discordTokenEnvKey` (e.g. `LUNA_DISCORD_TOKEN`, `ARRIETTY_DISCORD_TOKEN` in the default config), or top-level `defaultDiscordTokenEnvKey` when a bot has no key. Put the actual token values in `.env`; never commit raw tokens or put them in config. If a bot's env var is blank, that bot is skipped for Discord. In the [Discord Developer Portal](https://discord.com/developers/applications), open your app → Bot → **Privileged Gateway Intents** and enable **Message Content Intent** (required for reading message text and mentions).
- `CURSOR_API_KEY`: Cursor API key used for Cloud Agent launches and status polling.

Optional:

- `HEALTH_PORT`: port for the health HTTP server (default 8080); used by Prometheus blackbox for Grafana status dashboard. Set to 0 or omit to disable.
- `CURSOR_API_BASE_URL`: defaults to `https://api.cursor.com`
- `CURSOR_MODEL`: explicit model id for cloud launches
- `CURSOR_BASE_BRANCH`: default base branch when Luna launches a run
- `CURSOR_POLL_INTERVAL_MS`: how often Vinekeepers polls Cursor for feedback updates

## Logging and troubleshooting

Cursor API failures are visible in application logs (WARN level: transport exceptions and non-2xx responses with status, error code, and message) and surface as `CursorCloudException` with messages suitable for debugging (e.g. missing key, request/response errors). At DEBUG level the adapter logs safe per-request diagnostics (URI, key configured, model, repo, branch) without secrets. When no bot matches an event, the router logs at DEBUG: source, kind, authorId, actorUsername, mentions, channelId—use this to troubleshoot routing (e.g. discordAuthors or discordMention not matching).

## Environment and Cursor wiki

The app loads a `.env` file (if present) into system properties at startup. For the **Cursor wiki** step, `WIKIJS_*` variables must be available: either export them in the orchestrator environment or put them in a `.env` file in the working directory.

## Cursor / skills

Spec and code workflow rules live under `.cursor/rules/`. Reusable skills (e.g. update-readme, run-tests, update_tests, static-analysis) are under `.cursor/skills/` (common, nova-code, vinekeepers-code, etc.). The nova-code workflow (`.cursor/workflows/nova-code.yml`) runs guardrails (orchestrator-applied), update_tests, update_readme, and when `specs/specs.yml` exists, schema/drift gates and specs bootstrap.

Cloud runs should also follow the repo-level guidance in `AGENTS.md`, which summarizes the spec workflow, validation commands, and documentation expectations for Cursor Cloud Agents.
