# How it works

# Overview

Router.match(event) returns bots whose Routing/EventFilter accept the event. ToolPolicy enforces allow/deny and approval-required tools per bot. BotDefinition composes Persona, ModelProfile, Workflow, ToolPolicy, Routing, MemoryPolicy; config loader produces definitions from YAML.

# Flow

1. Engine receives event; calls Router.match(event).
2. Router evaluates each bot's routing/filter; returns matching BotDefinitions.
3. For each bot, ToolPolicy filters proposed actions before execution.

# Inputs and outputs

- **Inputs:** Event; bot config. **Outputs:** List of matching bots; policy-approved actions.
