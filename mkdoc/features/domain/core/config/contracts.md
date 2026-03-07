# Contracts

# APIs

None. ConfigLoader loads YAML and returns bot config.

# Schemas

YAML: botId, persona, model, workflow, stateSchema, tools (allow/deny, approval-required), routing (discord, git filters), memory. See BotConfig.

# Interfaces

ConfigLoader: load from path/stream → BotConfig or equivalent.
