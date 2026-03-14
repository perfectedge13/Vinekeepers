# Change log

# Entries

## 2026-03-13

- **Routing policy (RoutingRule, ordered list):** Routing is now an **ordered policy list** of **RoutingRule** instances (filter + botId). The former `Routing` type was removed; **RoutingRule** is the single rule model. Router matches events by evaluating rules in order; lifecycle owner precedence (handlesOwnedSpaces) still applies when a channel has a lifecycle context. ConfigLoader builds the ordered list from YAML routing entries.
- **Router ownership warning:** When a Discord channel has a lifecycle context but the owner bot does *not* have **handlesOwnedSpaces** in config, the Router logs a warning and falls back to filter-based routing instead of single-owner precedence. Ensures operators are informed when a lifecycle room is not exclusively owned by the expected bot.

## 2026-03-12

- **Ownership-based routing (handlesOwnedSpaces):** Router now applies **single-owner precedence** when a Discord channel has a lifecycle context and the context's owner bot has `handlesOwnedSpaces: true` in config: only that bot is returned for events in that channel. ConfigLoader loads optional `handlesOwnedSpaces` per bot; Bootstrap passes `LifecycleContextStore` and the per-bot map to Router via `setHandlesOwnedSpacesByBotId`. Enables Arrietty (and similar lifecycle room bots) to exclusively handle inbound room events (messages, interactions) without Luna or other bots being routed in those channels. BotDefinition extended with `handlesOwnedSpaces`; Router depends on LifecycleContextStore. Arrietty template uses `workflowRef: arrietty_room` for room commands (status, retry, close, echo).

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

