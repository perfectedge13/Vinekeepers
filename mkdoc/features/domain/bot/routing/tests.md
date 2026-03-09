# Tests

# Coverage

Unit tests cover base router matching, Discord mention matching from both message text and mention metadata, and Discord author filtering (`discordAuthors`) via ConfigLoader and Router.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ROUTER | RouterTest | com.vinekeepers.bot.RouterTest | â€” | Verify event routing and filter matching |
| UNIT-ROUTER-DISCORD-MENTION-TEXT | RouterTest | com.vinekeepers.bot.RouterTest | routeMatchesDiscordMentionFromTextCaseInsensitively | Verify `discordMention` matches `@mention` text case-insensitively |
| UNIT-ROUTER-DISCORD-MENTION-METADATA | RouterTest | com.vinekeepers.bot.RouterTest | routeMatchesDiscordMentionFromMetadataListCaseInsensitively | Verify `discordMention` matches connector mention metadata case-insensitively |
| UNIT-ROUTER-DISCORD-MENTION-MISSING | RouterTest | com.vinekeepers.bot.RouterTest | routeDoesNotMatchWhenDiscordMentionIsMissing | Verify routing rejects events when the configured mention is absent |
| UNIT-ROUTER-DISCORD-AUTHORS | ConfigLoaderTest | com.vinekeepers.config.ConfigLoaderTest | buildRouterParsesDiscordAuthorsRouting | Verify `discordAuthors` filter is parsed from YAML and router matches by actorId/actorUsername |

