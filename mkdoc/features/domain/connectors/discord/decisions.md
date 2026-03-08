# Decisions

# Entries

## 2026-03-07 — Preserve Discord mentions in connector events

Context: Mention-based bot routing needs more than raw message text because Discord payloads can already identify referenced users or bots.

Decision: Keep mention metadata on the internal Discord event payload and document reply delivery through the same connector abstraction.

Consequence: Routing can match configured bot mentions from payload metadata or normalized text, and connector tests now treat the `mentions` payload field as part of the contract.

