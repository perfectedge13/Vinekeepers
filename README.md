# Vinekeepers

Vinekeepers is an AI-based bots collection. The bot core provides bootstrap, configuration (YAML), engine, tool registry, and audit logging.

## Build and run

- **Compile:** `mvn compile`
- **Tests:** `mvn test` (unit tests cover core components: config, core, env, events, state, bot, workflow, connectors).
- **Spec schema gate:** `npm run validate-specs`
- **Spec drift gate:** `npm run validate-drift`
- **Main class:** `com.vinekeepers.VinekeepersApp` — run from your IDE or after packaging (e.g. `mvn package` then run the JAR with dependencies on the classpath).

Requirements: Java 21, Maven, Node 18+ for the spec gates.

### Docker

Build and run in a container (Java 21 runtime, Alpine-based):

```bash
docker build -t vinekeepers .
docker run --rm -e DISCORD_BOT_TOKEN=your_token vinekeepers
```

Or use Compose (optionally add a `.env` file with `DISCORD_BOT_TOKEN=...` and uncomment `env_file: .env` in `compose.yaml`):

```bash
docker compose up -d
```

To override config or env from the host when using `docker run`, mount volumes:

```bash
docker run --rm -v "$(pwd)/config:/app/config" -v "$(pwd)/.env:/app/.env" -e DISCORD_BOT_TOKEN=... vinekeepers
```

Optional: set `JAVA_OPTS` (e.g. `-Xmx512m`) via `-e JAVA_OPTS=...`.

Bot definitions and routing are in `config/bots.yaml`. Each bot can set `workflow.type` (for example `stub` or `configured`) plus runtime options such as `conversationMode` and `sessionKeyStrategy`; Bootstrap creates a `WorkflowRunner` per bot via `WorkflowRunnerFactory`, and the engine uses `runResult(event, stateStore, botId)` to execute it. Routing filters support keys such as `discordTrigger`, `discordMention`, and `discordAuthors`; `discordMention` matches case-insensitively against normalized Discord `@mentions` from message text or connector-provided mention metadata; optional `discordAuthors` restricts which Discord users can trigger the bot (by user id or normalized username). Follow-up messages in the same session (e.g. without a mention) continue the workflow when the bot is waiting for input for that session key. For `configured`, use `workflow.params.workflowRef` to reference a workflow id from the YAML `workflows:` section, which defines a step DSL with `ask_input`, `prompt_for_field`, `capture_field`, `call_action`, `branch`, and `done`. **Rich interactions:** intent model (e.g. `intent: present_choices`, `confirm_action`) and step options (`choices`, `confirmLabel`, `cancelLabel`, `fields`); engine delivers replies via the connector **sink registry** (lifecycle operations: respondImmediately, sendFollowUp, updateMessage); **Discord lifecycle** is adapter-owned (ack/defer within platform window, auto-defer when needed). Channel replies use the gateway **send** overload so initial messages can include **components** (buttons, select menus) when the response has an intent (e.g. present_choices, confirm_action). See [Architecture](mkdoc/architecture.md) and [Discord](mkdoc/features/domain/connectors/discord.md) in the docs.

Configured workflows persist per-session `ConfigurableWorkflowState`. `CallActionStep` prefers `ToolRunner` when a tool is registered and falls back to `WorkflowActionRegistry` for legacy actions; for tool-backed steps it also forwards event metadata and the session key so tools can launch external work and correlate replies. After the workflow runs, the engine can invoke a bot reasoner with the workflow reply context, current session state, and the last normalized user message; any proposed tool calls are executed through `ToolRunner` under the bot's `ToolPolicy`.

Example: Luna has `workflow.type: configured` with `workflow.params.workflowRef: luna_cursor` and a `discordMention: "luna"` routing rule (optionally `discordAuthors`). The shipped workflow is multi-turn: **guided repo selection** (choiceProvider `githubRepos` — GitHub API + "Use last repo" + "Custom repo"), code change prompt/capture, **confirmation step** (Launch / Edit repo / Edit request / Cancel), then `cursor.fullRun`; Discord can show buttons or a select menu for choices. Edit repo and Edit request use branch steps with `clear` (clearKeys) so the runner clears those state keys before advancing, then reprompts for the cleared fields. Set `GITHUB_TOKEN` in `.env` for repo listing. Status, feedback, and PR links are reported back to Discord while the run is active.

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
| `com.vinekeepers.core` | Engine (workflow first, then reasoner/tool execution), `Bootstrap` (loadConfig → factory creates runners, `registerRunner`) |
| `com.vinekeepers.config` | `ConfigLoader` (YAML), `BotConfig` |
| `com.vinekeepers.tools` | `Tool`, `ToolRegistry`, `ToolRunner`, Luna launch tool (`CursorFullRunTool`) |
| `com.vinekeepers.audit` | `AuditLog`, `AuditRecorder` |
| `com.vinekeepers.util` | Shared utilities |
| `com.vinekeepers.reasoner` | Reasoner interface and stubs |
| `com.vinekeepers.workflow` | Workflow interface, `WorkflowRunner`, `WorkflowRunnerFactory`, `StubWorkflowRunner`, `ConfigurableWorkflowRunner`, `WorkflowActionRegistry`, `WorkflowDefinition`, `ConfigurableWorkflowState`, session key helpers, step types (`AskForInputStep`, `PromptForFieldStep`, `CaptureFieldFromEventStep`, `CallActionStep`, `BranchStep`, `DoneStep`), legacy `GatheringState` and `CursorCloudGatheringWorkflow` test harness |
| `com.vinekeepers.core.cursor` | Cursor Cloud Agent API adapter (`CursorCloudAdapter`, `CursorCloudAdapterImpl`), run monitor, in-memory Luna run state |
| `com.vinekeepers.bot` | Bot definitions, conversation/runtime options, normalized routing, persona |
| `com.vinekeepers.events` | Event bus, sources, subscribers |
| `com.vinekeepers.connectors` | Event sources (e.g. GitHub, Discord); JDA-backed Discord gateway; `DiscordReplySender` (legacy) and `DiscordAppReplySink` for workflow replies and rich interactions |
| `com.vinekeepers.interactions` | Platform-neutral reply model: `OutboundResponse`, `ResponseIntent`, `ReplyTarget`, `Capabilities`, and connector contract `AppReplySink` (lifecycle operations) |
| `com.vinekeepers.state` | State store |
| `com.vinekeepers.providers` | Dynamic choice providers (e.g. `GitHubReposChoiceProvider` for guided repo selection) |
| `com.vinekeepers.env` | `EnvLoader` (load .env into system properties), `Env.get(key, default)` |

Source: `src/main/java`, tests: `src/test/java`. Docs (MkDocs) live under the configured docs root (currently `mkdoc`); see **Documentation** above for serving locally.

## Environment

The app loads a `.env` file (if present) into system properties at startup. A sample contract lives in `.env.example`.

Required for Luna on Discord:

- `DISCORD_BOT_TOKEN`: Discord bot token used by the JDA connector. In the [Discord Developer Portal](https://discord.com/developers/applications), open your app → Bot → **Privileged Gateway Intents** and enable **Message Content Intent** (required for reading message text and mentions).
- `CURSOR_API_KEY`: Cursor API key used for Cloud Agent launches and status polling.

Optional:

- `HEALTH_PORT`: port for the health HTTP server (default 8080); used by Prometheus blackbox for Grafana status dashboard. Set to 0 or omit to disable.
- `CURSOR_API_BASE_URL`: defaults to `https://api.cursor.com`
- `CURSOR_MODEL`: explicit model id for cloud launches
- `CURSOR_BASE_BRANCH`: default base branch when Luna launches a run
- `CURSOR_POLL_INTERVAL_MS`: how often Vinekeepers polls Cursor for feedback updates

## Environment and Cursor wiki

The app loads a `.env` file (if present) into system properties at startup. For the **Cursor wiki** step, `WIKIJS_*` variables must be available: either export them in the orchestrator environment or put them in a `.env` file in the working directory.

## Cursor / skills

Spec and code workflow rules live under `.cursor/rules/`. Reusable skills (e.g. update-readme, run-tests, update_tests, static-analysis) are under `.cursor/skills/` (common, nova-code, vinekeepers-code, etc.). The nova-code workflow (`.cursor/workflows/nova-code.yml`) runs guardrails (orchestrator-applied), update_tests, update_readme, and when `specs/specs.yml` exists, schema/drift gates and specs bootstrap.

Cloud runs should also follow the repo-level guidance in `AGENTS.md`, which summarizes the spec workflow, validation commands, and documentation expectations for Cursor Cloud Agents.
