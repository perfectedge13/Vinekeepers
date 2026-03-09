# Change log

# Entries

## 2026-03-09

- **bots.yaml and configured workflow:** `config/bots.yaml` updated for Luna and configured workflow references; aligns with Discord intake components and edit-reprompt (clearKeys) behavior in workflow runner/state.
- **discordAuthors in routing:** ConfigLoader parses optional `discordAuthors` from routing filter in YAML; Luna can be restricted to specific Discord users (e.g. `novawilde13_72571`) so only those authors trigger the bot when `discordMention` and `discordAuthors` are both configured.

## 2026-03-07

ConfigLoader parses workflow type and params, conversation runtime options such as `conversationMode` and `sessionKeyStrategy`, and the top-level `workflows:` section for configured workflow definitions. Bootstrap uses that data to create per-bot runners, including conversational workflow sessions such as Luna's `luna_cursor` flow.
Config docs now note that routing filters can include optional `discordMention` keys while keeping `discordTrigger` backward-compatible, and that Luna is activated from `@Luna` references instead of a slash-style trigger string.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

