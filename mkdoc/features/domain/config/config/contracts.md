# Contracts

# APIs

None. `ConfigLoader` loads YAML and returns bot config.

# Schemas

YAML: `bots[]` with `id`, `persona`, optional `model` (`provider`, `modelId`), `toolPolicy`, `memory`, `workflow`, `conversationMode`, and `sessionKeyStrategy`; `routing[]` with `botId` and `filter`; and top-level `workflows` definitions. Workflow definitions may set `defaultModel` and individual LLM call_action steps may set `model`/`modelOverride` to override. Routing filters can include `discordAuthors`, `discordChannels`, optional `discordTrigger`, optional `discordMention`, `repos`, `prLabels`, and `prAuthors`. See `BotConfig`.

# Interfaces

`ConfigLoader`: load from path/stream to `BotConfig`; build router and bot definitions from parsed YAML, including mention-aware routing filters.

