# Change log

# Entries

## 2026-03-10

- **discordAuthors: numeric id and username:** Router `discordAuthors` now supports both **numeric Discord user id** (stable, matches `actorId`) and **username** (matches `actorUsername`, case-insensitive). Prefer numeric id when available. Config comment in `config/bots.yaml` documents the behavior.
- **DEBUG when no bot matched:** When no bot matches an event, the router logs at DEBUG: source, kind, authorId, actorUsername, mentions, channelId—for troubleshooting routing (e.g. discordAuthors or discordMention not matching).

## 2026-03-09

- **Discord filter by user:** Routing supports optional `discordAuthors` filter: events match when the event author (by `actorId` or normalized `actorUsername`) is in the configured list. `NormalizedEventContext` exposes `actorId` and `actorUsername` from payload author/authorId for routing. Luna can be restricted to specific Discord users (e.g. `novawilde13_72571`) via `discordAuthors` in `config/bots.yaml`.
- **Discord interaction routing:** Discord trigger and mention filters apply only to message events. For Discord interaction events (`kind: interaction`), routing uses author, channel, and other criteria only—no trigger or mention check—so button/modal responses from allowed users route to the bot without requiring an @mention. Router and RoutingFilter updated; new tests: `messageWithMentionRoutesToBot`, `interactionWithoutMentionRoutesToBot`, `interactionFromOtherUserDoesNotRoute`, `interactionFromAllowedAuthorRoutesToBot`.

## 2026-03-08

- **Interaction payload:** `NormalizedEventContext` now carries optional interaction payload (`interactionId`, `token`, `customId`, `values`) for events with `kind: interaction`, so workflow capture steps and routing can consume Discord (and other) interaction responses.

## Prior

- Split routing into its own feature dossier so domain navigation follows `feature.domain_slug` from `specs/core-registry.yml`.
- Documented normalized event context and Discord mention matching as routing concerns instead of bot-definition concerns.

