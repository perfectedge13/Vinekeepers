# Decisions

# Entries

## 2026-03-07 — Add mention-aware routing keys to YAML

Context: Luna needed to activate from ordinary Discord mentions without forcing a literal trigger substring in message text.

Decision: Extend config parsing so routing filters can read optional `discordMention` values while preserving existing `discordTrigger` support and the configured workflow structure.

Consequence: Bot YAML can express mention-based activation directly, and config-driven routing now maps more closely to Discord message semantics.

