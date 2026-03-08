# Tests

# Coverage

ConfigLoaderTest verifies YAML config loading, workflow parsing, and mention-aware routing filter parsing.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-CONFIG-LOADER | ConfigLoaderTest | com.vinekeepers.config.ConfigLoaderTest | (various) | Verify YAML config loading |
| UNIT-CONFIG-LOADER-DISCORD-MENTION | ConfigLoaderTest | com.vinekeepers.config.ConfigLoaderTest | buildRouterParsesDiscordMentionRouting | Verify `discordMention` routing filters are parsed from YAML and produce a matching router |

