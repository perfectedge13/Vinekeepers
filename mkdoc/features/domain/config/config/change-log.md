# Change log

# Entries

## 2026-03-21

- **Docs:** Config feature page notes optional **`persona.model`** as the bot default Cursor model for configured workflow runners and points to workflow docs for per-step **`call_action.model`** and env fallback.

## 2026-03-13

- **Blank branch in step bind:** `config/bots.yaml` supports branch value `""` (blank/empty) in workflow step bindings (e.g. for branch steps). ConfigLoader and workflow runner treat empty string as blank branch; Arrietty template and Luna workflow can use this for UX (e.g. clear branch selection).
- **create_thread and deliveryChannelId in step bind:** Workflow step bind keys support `deliveryChannelId`; step action `create_thread` with `storeIn` (e.g. `deliveryChannelId`) is supported in workflow config. Arrietty template may include a `create_thread` step in `arrietty_room` so Cursor run updates are delivered to the thread.

## 2026-03-12

- **discordTokenEnvKey per bot:** Bot definitions may set optional `discordTokenEnvKey` (env var name for that bot's Discord token). Used for per-bot Discord identity (e.g. luna, arrietty in `config/bots.yaml`); Bootstrap registers one sender/gateway per bot when the key is set and token is present. Enables lifecycle delivery and outbound-only gateways for bots not in routing.

## 2026-03-10

- **discordAuthors in routing:** Optional `discordAuthors` in routing filter accepts numeric Discord user id (matches event `actorId`) or username (matches `actorUsername`, case-insensitive). Config comment in `config/bots.yaml` documents this; prefer numeric id when available.

## 2026-03-09

- **bots.yaml and configured workflow:** `config/bots.yaml` updated for Luna and configured workflow references; aligns with Discord intake components and edit-reprompt (clearKeys) behavior in workflow runner/state.
- **discordAuthors in routing:** ConfigLoader parses optional `discordAuthors` from routing filter in YAML; Luna can be restricted to specific Discord users (e.g. `novawilde13_72571`) so only those authors trigger the bot when `discordMention` and `discordAuthors` are both configured.

## 2026-03-07

ConfigLoader parses workflow type and params, conversation runtime options such as `conversationMode` and `sessionKeyStrategy`, and the top-level `workflows:` section for configured workflow definitions. Bootstrap uses that data to create per-bot runners, including conversational workflow sessions such as Luna's `luna_cursor` flow.
Config docs now note that routing filters can include optional `discordMention` keys while keeping `discordTrigger` backward-compatible, and that Luna is activated from `@Luna` references instead of a slash-style trigger string.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

