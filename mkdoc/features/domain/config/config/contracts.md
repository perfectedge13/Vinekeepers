# Contracts

# APIs

None. `ConfigLoader` loads YAML and returns bot config.

# Schemas

YAML: `bots[]` with `id`, `persona`, optional `model`, `toolPolicy`, `memory`, `workflow`, `conversationMode`, and `sessionKeyStrategy`; **connector-scoped identity** preferred under `identities.discord` with `tokenEnvKey` and `handlesOwnedSpaces` (legacy top-level `discordTokenEnvKey`/`handlesOwnedSpaces` supported; new shape wins if both present). Top-level `defaultDiscordTokenEnvKey` is retained transitional. `routing[]` with `botId` and `filter`; top-level `workflows` definitions. Routing filters: `discordAuthors`, `discordChannels`, optional `discordTrigger`, optional `discordMention`, `repos`, `prLabels`, `prAuthors`. See `BotConfig`.

# Interfaces

- **`ConfigLoader`:** Load from path/stream to `BotConfig`; build router and bot definitions from parsed YAML; dual-read `identities.discord` and legacy keys; normalize to **ConnectorIdentity** per connector (keyed by connector id, e.g. `discord`); include mention-aware routing filters.
- **`ConnectorIdentity`:** Immutable connector-scoped attributes; `getAttribute(key)` / `getAttributes()` so adapters read `tokenEnvKey`, `handlesOwnedSpaces` without core depending on connector names.
- **`BotDefinition`:** `getConnectorIdentity(connectorId)` returns the ConnectorIdentity for that connector, or null.

