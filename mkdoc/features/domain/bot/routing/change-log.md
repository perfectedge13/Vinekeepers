# Change log

# Entries

## 2026-03-09

- **Discord filter by user:** Routing supports optional `discordAuthors` filter: events match when the event author (by `actorId` or normalized `actorUsername`) is in the configured list. `NormalizedEventContext` exposes `actorId` and `actorUsername` from payload author/authorId for routing. Luna can be restricted to specific Discord users (e.g. `novawilde13_72571`) via `discordAuthors` in `config/bots.yaml`.

## 2026-03-08

- **Interaction payload:** `NormalizedEventContext` now carries optional interaction payload (`interactionId`, `token`, `customId`, `values`) for events with `kind: interaction`, so workflow capture steps and routing can consume Discord (and other) interaction responses.

## Prior

- Split routing into its own feature dossier so domain navigation follows `feature.domain_slug` from `specs/core-registry.yml`.
- Documented normalized event context and Discord mention matching as routing concerns instead of bot-definition concerns.

