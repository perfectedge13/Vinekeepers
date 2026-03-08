# How it works

# Overview

`Router.route(event)` returns bot ids whose `RoutingFilter` accepts the normalized event context. `NormalizedEventContext` extracts actor, channel, text, and mention tokens from the raw payload so routing and session decisions use the same view of the event. `ToolPolicy` enforces allow and deny rules per bot. `BotDefinition` composes persona, model, workflow configuration, tool policy, memory policy, and runtime conversation settings produced from YAML.

# Flow

1. Engine receives an event and calls `Router.route(event)`.
2. `Router` builds a `NormalizedEventContext` from the raw event payload and evaluates each routing filter against normalized fields such as actor, channel, text, mentions, repo, and labels.
3. For Discord messages, normalized mentions come from either payload metadata (`mentions`), plain-text `@name` references, or Discord-style `<@...>` tokens, and `discordMention` matches them case-insensitively.
4. Matching bot ids are dispatched through the engine, which looks up the registered `BotDefinition`, workflow runner, and reasoner for each bot.
5. `ToolPolicy` is applied before workflow or reasoner tool execution.

# Inputs and outputs

- **Inputs:** Event plus bot config YAML. **Outputs:** Matching bot ids and bot-specific tool policy decisions, including Discord mention-based matches such as `discordMention: "luna"` when a message references `@Luna` or includes matching mention metadata.

