# Tests

# Coverage

Unit tests cover base router matching, Discord mention matching from both message text and mention metadata, Discord author filtering (`discordAuthors`) via ConfigLoader and Router, and Discord interaction routing (interactions route without trigger/mention check; author and channel only).

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ROUTER | RouterTest | com.vinekeepers.bot.RouterTest | â€” | Verify event routing and filter matching |
| UNIT-ROUTER-DISCORD-MENTION-TEXT | RouterTest | com.vinekeepers.bot.RouterTest | routeMatchesDiscordMentionFromTextCaseInsensitively | Verify `discordMention` matches `@mention` text case-insensitively |
| UNIT-ROUTER-DISCORD-MENTION-METADATA | RouterTest | com.vinekeepers.bot.RouterTest | routeMatchesDiscordMentionFromMetadataListCaseInsensitively | Verify `discordMention` matches connector mention metadata case-insensitively |
| UNIT-ROUTER-DISCORD-MENTION-MISSING | RouterTest | com.vinekeepers.bot.RouterTest | routeDoesNotMatchWhenDiscordMentionIsMissing | Verify routing rejects events when the configured mention is absent |
| UNIT-ROUTER-DISCORD-AUTHORS | ConfigLoaderTest | com.vinekeepers.config.ConfigLoaderTest | buildRouterParsesDiscordAuthorsRouting | Verify `discordAuthors` filter is parsed from YAML and router matches by actorId/actorUsername |
| UNIT-ROUTER-MENTION-MESSAGE | RouterTest | com.vinekeepers.bot.RouterTest | messageWithMentionRoutesToBot | Verify message with @mention routes to bot |
| UNIT-ROUTER-INTERACTION-NO-MENTION | RouterTest | com.vinekeepers.bot.RouterTest | interactionWithoutMentionRoutesToBot | Verify Discord interaction events route without trigger/mention check (author and channel only) |
| UNIT-ROUTER-INTERACTION-ALLOWED-AUTHOR | RouterTest | com.vinekeepers.bot.RouterTest | interactionFromAllowedAuthorRoutesToBot | Verify interaction from allowed author (discordAuthors) routes to bot |
| UNIT-ROUTER-INTERACTION-OTHER-USER | RouterTest | com.vinekeepers.bot.RouterTest | interactionFromOtherUserDoesNotRoute | Verify interaction from other user does not route when discordAuthors is set |
| UNIT-ROUTER-OWNERSHIP-WARNING | RouterTest | com.vinekeepers.bot.RouterTest | routeWithLifecycleStore_ownedChannel_logsOwnershipMismatchWarningWhenOwnerDoesNotHaveHandlesOwnedSpaces | Verify Router logs WARN when channel has lifecycle owner bot but that bot does not have handlesOwnedSpaces; uses filter-based routing |

