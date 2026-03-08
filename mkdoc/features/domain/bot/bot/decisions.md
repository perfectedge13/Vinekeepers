# Decisions

# Entries

## 2026-03-07 — Use normalized mention matching for Discord activation

Context: Luna needed to activate from normal Discord `@Luna` references instead of relying on a trigger substring alone.

Decision: Route Discord mention activation through `NormalizedEventContext` and `RoutingFilter.discordMention`, so the router can match lower-cased mentions from payload metadata, plain-text `@name` tokens, and Discord-style mention tokens while leaving `discordTrigger` available for existing configs.

Consequence: Bot routing documentation and tests now treat mention activation as a first-class routing behavior, and Discord connectors should preserve mention metadata when available.

