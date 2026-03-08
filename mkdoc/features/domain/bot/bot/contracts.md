# Contracts

# APIs

None. The router evaluates an incoming `Event` against configured routing filters and bot runtime settings. `ToolPolicy` remains a per-bot execution gate.

# Schemas

`BotDefinition`: id, persona, model profile, workflow type plus params, `toolPolicy`, `memoryPolicy`, `conversationMode`, and optional `sessionKeyStrategy`. `RoutingFilter` supports Discord filters such as `discordAuthors`, `discordChannels`, optional `discordTrigger`, and optional `discordMention`, plus repository, PR label, and PR author filters. `NormalizedEventContext` exposes normalized event fields such as actor, channel, text, mentions, repository, and labels. `ToolPolicy` defines allow, deny, and approval rules per bot.

# Interfaces

- **`Router`:** matches events to configured bots based on normalized routing criteria.
- **`NormalizedEventContext`:** extracts connector-specific payload data into a shared routing and session-key view.
- **`RoutingFilter`:** stores optional Discord trigger and mention filters alongside other routing criteria.
- **`BotDefinition`:** the composed runtime model consumed by routing, workflow, and reasoner stages.
- **`ConversationMode`:** distinguishes single-event bots from conversational ones.
- **`ToolPolicy`:** per-bot policy for allowed, denied, and approval-gated tools.

