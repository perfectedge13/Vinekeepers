# How it works

# Overview

The project maintains a spec index (specs.yml) and requirements registries for traceability; schema and drift checks apply when schemas are present. VinekeepersApp is the entrypoint: it loads .env and bootstraps the engine, config, connectors, and shared tool execution components.

# Flow

1. VinekeepersApp loads .env via EnvLoader and creates Bootstrap.
2. Bootstrap wires engine, config loader, ConnectorRegistry; creates Discord adapter and calls adapter.registerBots(bots, context).
3. Bootstrap creates WorkflowRunner per bot via WorkflowRunnerFactory and registerRunner(botId, runner).
4. Bootstrap registers shared tools and passes ToolRunner into configured workflows.

# Inputs and outputs

Entrypoint: main class runs Bootstrap and starts engine. No external inputs beyond .env and config paths.
