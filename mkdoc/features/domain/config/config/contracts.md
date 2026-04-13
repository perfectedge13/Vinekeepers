# Contracts

# APIs

None. `ConfigLoader` loads YAML and returns bot config.

# Schemas

YAML: `bots[]` with `id`, `persona`, optional `model`, `toolPolicy`, `memory`, `workflow`, `conversationMode`, and `sessionKeyStrategy`; `routing[]` with `botId` and `filter`; and top-level `workflows` definitions. Routing filters can include `discordAuthors`, `discordChannels`, optional `discordTrigger`, optional `discordMention`, `repos`, `prLabels`, and `prAuthors`. For workflow `call_action` steps, optional `model` may be either a string model id (for example `gpt-4.1-mini`) or a map `{ provider, modelId }`; when provider is provided it must be `openai`. See `BotConfig`.

# Interfaces

`ConfigLoader`: load from path/stream to `BotConfig`; build router and bot definitions from parsed YAML, including mention-aware routing filters.

