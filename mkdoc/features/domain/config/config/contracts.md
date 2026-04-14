# Contracts

# APIs

None. `ConfigLoader` loads YAML and returns bot config.

# Schemas

YAML: `bots[]` with `id`, `persona`, optional `model`, `toolPolicy`, `memory`, `workflow`, `conversationMode`, and `sessionKeyStrategy`; `routing[]` with `botId` and `filter`; and top-level `workflows` definitions. Workflow steps support DSL fields including optional `model` for `call_action` steps to override the launch model for that step. Routing filters can include `discordAuthors`, `discordChannels`, optional `discordTrigger`, optional `discordMention`, `repos`, `prLabels`, and `prAuthors`. See `BotConfig`.

# Interfaces

`ConfigLoader`: load from path/stream to `BotConfig`; build router and bot definitions from parsed YAML, including mention-aware routing filters.

