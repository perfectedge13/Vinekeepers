# Tests

# Coverage

RouterTest covers general routing plus `discordMention` matching from message text and mention metadata. ToolPolicyTest covers policy enforcement. Manual coverage keeps the bot definition contract traceable at compile time.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ROUTER | RouterTest | com.vinekeepers.bot.RouterTest | (various) | Verify event routing and filter matching |
| UNIT-ROUTER-DISCORD-MENTION-TEXT | RouterTest | com.vinekeepers.bot.RouterTest | routeMatchesDiscordMentionFromTextCaseInsensitively | Verify `discordMention` routing matches `@mentions` in message text case-insensitively |
| UNIT-ROUTER-DISCORD-MENTION-METADATA | RouterTest | com.vinekeepers.bot.RouterTest | routeMatchesDiscordMentionFromMetadataListCaseInsensitively | Verify `discordMention` routing matches mention metadata case-insensitively |
| UNIT-ROUTER-DISCORD-MENTION-MISSING | RouterTest | com.vinekeepers.bot.RouterTest | routeDoesNotMatchWhenDiscordMentionIsMissing | Verify mention routing does not match when the configured mention is absent |
| UNIT-TOOL-POLICY | ToolPolicyTest | com.vinekeepers.bot.ToolPolicyTest | (various) | Verify tool policy enforcement |
| MANUAL-BOT-DEF | Bot definition structure | — | — | Verify BotDefinition and related models |

