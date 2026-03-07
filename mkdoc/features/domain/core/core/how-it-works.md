# How it works

# Overview

The Vinekeepers core implements an event-driven loop: events from Discord, GitHub, or other sources are published to the event bus; the engine receives them and routes each event to bots whose routing filters match; for each bot, the engine loads workflow state, calls the reasoner (LLM or rules), enforces tool policy on proposed actions, executes approved tools, persists state and audit, and emits responses. Bot definitions (persona, model, workflow, tool policy, routing) are loaded from YAML via ConfigLoader. The application entrypoint loads .env, then Bootstrap wires the engine, config loader, state store, audit log, tool registry, and event sources.

# Flow

1. Application starts: VinekeepersApp loads .env (EnvLoader), then Bootstrap creates engine, config loader, state store, audit log, tool registry; engine subscribes to event bus; connectors (Discord, GitHub) start and publish events.
2. Event on bus: VinekeepersEngine receives event; Router.match(event) returns list of BotDefinitions whose Routing/EventFilter accept the event.
3. Per bot: load state from StateStore(botId, conversationKey); call Reasoner.think(input) → statePatch, proposedActions, nextState; ToolPolicy enforces allow/deny and approval; ToolRunner executes approved actions; StateStore.save and AuditLog record; respond (e.g. Discord reply) if applicable.
4. Config: ConfigLoader reads YAML; each bot definition references workflow, tools, routing; routing filter criteria (e.g. Discord channel, GitHub PR labels) determine which events the bot sees.

# Inputs and outputs

- **Inputs:** Events (sourceId, kind, payload); bot config YAML; .env (optional). Reasoner input: event, context, current state.
- **Outputs:** Persisted state; audit records; tool side effects (e.g. Discord message, GitHub comment). Reasoner output: statePatch, proposedActions, nextState.
