# Change log

# Entries

## 2026-03-07

- Bot runtime configuration now documents `conversationMode` and `sessionKeyStrategy`, and routing uses `NormalizedEventContext` so workflow session selection and filter matching can share the same normalized event view.
- Routing docs now cover optional `discordMention` filters while keeping `discordTrigger` backward-compatible, including case-insensitive matching of `@Luna`-style references from normalized Discord message text, mention metadata, or Discord-style mention tokens.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

