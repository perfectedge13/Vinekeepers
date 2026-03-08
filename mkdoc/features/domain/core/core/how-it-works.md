# How it works

# Overview

The Vinekeepers core implements an event-driven loop: events from Discord, GitHub, or other sources are published to the event bus; the engine receives them and routes each event to bots whose normalized routing filters match; for each bot, the engine runs the configured workflow, then optionally calls the reasoner with workflow context and session state, executes approved tools, persists state and audit, and emits responses. Bot definitions are loaded from YAML via `ConfigLoader`. The application entrypoint loads `.env`, then `Bootstrap` wires the engine, config loader, state store, audit log, tool registry, and event sources.

# Flow

1. Application starts: `VinekeepersApp` loads `.env` through `EnvLoader`, then `Bootstrap` creates the engine, config loader, state store, audit log, tool registry, and connectors; the engine subscribes to the event bus.
2. Event on bus: `VinekeepersEngine` receives an event; `Router.route(event)` evaluates routing filters over `NormalizedEventContext` fields and returns matching bot ids.
3. Per bot: the engine runs the registered workflow runner, which loads session-scoped workflow state and returns `WorkflowRunResult`.
4. If a reasoner is registered, the engine builds `ReasonerInput` from the event, workflow reply context, current state, and last normalized user message; it applies any state patch and runs proposed tool calls through `ToolRunner`.
5. `AuditRecorder` records workflow and reasoner activity, and Discord replies are sent through the reply path when the event source is Discord.

# Inputs and outputs

- **Inputs:** Events (`sourceId`, `kind`, `payload`), bot config YAML, and optional `.env`. Reasoner input includes event, workflow context, current state, and last user message.
- **Outputs:** Persisted workflow state, audit records, tool side effects (for example Discord replies), and optional reasoner reply text.

