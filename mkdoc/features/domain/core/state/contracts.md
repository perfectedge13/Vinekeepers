# Contracts

# APIs

None. StateStore load/save.

# Schemas

State: JSON-shaped per workflow (e.g. TicketDraft, ReviewDraft). Key: (botId, conversationKey).

# Interfaces

StateStore: load(botId, key), save(botId, key, state).
