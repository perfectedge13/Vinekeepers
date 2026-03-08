# How it works

# Overview

`ConfigLoader` reads YAML configuration and returns bot definitions. `BotConfig` maps to the `BotDefinition` structure, including persona, model, workflow, tools, routing, memory, and runtime conversation settings. Routing filters now support optional `discordMention` values in addition to existing `discordTrigger` strings.

# Flow

1. `Bootstrap` calls `ConfigLoader` with the config path.
2. `ConfigLoader` parses `bots`, `routing`, and top-level `workflows` from YAML into `BotConfig`.
3. Each bot entry is mapped into `BotDefinition`, including `workflow.type`, optional `workflow.params`, routing filter keys such as backward-compatible `discordTrigger` and optional `discordMention`, `conversationMode`, and `sessionKeyStrategy`.
4. The engine and runner factory use those definitions for routing, workflow creation, and session scoping.

# Inputs and outputs

- **Inputs:** YAML file path or stream. **Outputs:** `BotConfig`, bot definitions, routing rules including Discord trigger and mention filters, and named workflow definitions.

