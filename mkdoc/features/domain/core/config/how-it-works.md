# How it works

# Overview

ConfigLoader reads YAML configuration and returns bot definitions. BotConfig maps to BotDefinition structure (persona, model, workflow, tools, routing, memory).

# Flow

1. Bootstrap or engine calls ConfigLoader with config path.
2. ConfigLoader parses YAML and maps to BotConfig/bot definitions.
3. Engine uses loaded definitions for routing and bot instantiation.

# Inputs and outputs

- **Inputs:** YAML file path or stream. **Outputs:** Bot config / list of bot definitions.
