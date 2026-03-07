# Vinekeepers

Vinekeepers is an AI-based bots collection. The bot core provides bootstrap, configuration (YAML), engine, tool registry, and audit logging.

## Build and run

- **Compile:** `mvn compile`
- **Tests:** `mvn test` (unit tests cover core components: config, core, env, events, state, bot, workflow, connectors).
- **Main class:** `com.vinekeepers.VinekeepersApp` — run from your IDE or after packaging (e.g. `mvn package` then run the JAR with dependencies on the classpath).

Requirements: Java 21, Maven.

Bot definitions and routing are in `config/bots.yaml`. Each bot can set `workflow.type` (e.g. `stub`, `configured`); Bootstrap creates a WorkflowRunner per bot via WorkflowRunnerFactory and the Engine uses those runners. For `configured`, use `workflow.params.workflowRef` to reference a workflow id from the YAML `workflows:` section, which defines a step DSL (steps: `ask_input`, `call_action`, `branch`, `done`). ConfigurableWorkflowRunner runs these steps; CallActionStep uses WorkflowActionRegistry (Bootstrap registers Cursor actions such as `cursor.fullRun`, `cursor_cloud`, `echo`). Example: Luna bot has `workflow.type: configured` and `workflow.params.workflowRef: luna_cursor`, trigger `/Luna` on Discord; workflow luna_cursor gathers project and code change then invokes `cursor.fullRun` (Cursor Cloud API).

## Documentation

Docs (MkDocs) live in `mkdoc/`. To build and serve the docs locally (including Mermaid diagrams on the Architecture page), install the docs stack then run MkDocs from the project root:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

Then open http://127.0.0.1:8000 (or the URL MkDocs prints).

## Project layout

| Package | Purpose |
|---------|--------|
| `com.vinekeepers` | Entrypoint: `VinekeepersApp` wires `Bootstrap` |
| `com.vinekeepers.core` | Engine (uses WorkflowRunners per bot), `Bootstrap` (loadConfig → factory creates runners, `registerRunner`) |
| `com.vinekeepers.config` | `ConfigLoader` (YAML), `BotConfig` |
| `com.vinekeepers.tools` | `Tool`, `ToolRegistry`, `ToolRunner`, stub tools |
| `com.vinekeepers.audit` | `AuditLog`, `AuditRecorder` |
| `com.vinekeepers.util` | Shared utilities |
| `com.vinekeepers.reasoner` | Reasoner interface and stubs |
| `com.vinekeepers.workflow` | Workflow interface, `WorkflowRunner`, `WorkflowRunnerFactory`, `StubWorkflowRunner`, `ConfigurableWorkflowRunner`, `WorkflowActionRegistry`, `WorkflowDefinition`, `ConfigurableWorkflowState`, step types (`AskForInputStep`, `CallActionStep`, `BranchStep`, `DoneStep`), `GatheringState`, `CursorCloudGatheringWorkflow` |
| `com.vinekeepers.core.cursor` | Cursor Cloud API adapter (`CursorCloudAdapter`, `CursorCloudAdapterImpl`) |
| `com.vinekeepers.bot` | Bot definitions, routing, persona |
| `com.vinekeepers.events` | Event bus, sources, subscribers |
| `com.vinekeepers.connectors` | Event sources (e.g. GitHub, Discord); `DiscordReplySender` for workflow replies |
| `com.vinekeepers.state` | State store |
| `com.vinekeepers.env` | `EnvLoader` (load .env into system properties), `Env.get(key, default)` |

Source: `src/main/java`, tests: `src/test/java`. Docs (MkDocs) live in `mkdoc/`; see **Documentation** above for serving locally.

## Environment and Cursor wiki

The app loads a `.env` file (if present) into system properties at startup. For the **Cursor wiki** step, `WIKIJS_*` variables must be available: either export them in the orchestrator environment or put them in a `.env` file in the working directory.

## Cursor / skills

Spec and code workflow rules live under `.cursor/rules/`. Reusable skills (e.g. update-readme, run-tests, update_tests, static-analysis) are under `.cursor/skills/` (common, nova-code, vinekeepers-code, etc.). The nova-code workflow (`.cursor/workflows/nova-code.yml`) runs guardrails (orchestrator-applied), update_tests, update_readme, and when `specs/specs.yml` exists, schema/drift gates and specs bootstrap.
