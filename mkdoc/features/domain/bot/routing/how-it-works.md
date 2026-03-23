# How it works

# Overview

`Router` evaluates each incoming `Event` against configured `Routing` rules through `EventFilter` and `RoutingFilter`. `NormalizedEventContext` extracts connector-specific payload details into a shared view (including `actorId`, `actorUsername`, and optional `parentChannelId` from Discord thread payloads) so filters can match fields such as actor, channel, text, mentions, repository, and labels consistently. For Discord **messages**, `discordMention` uses payload `mentions` plus text tokens that are numeric user/bot snowflakes after `<@` or `<@!` only (not `<@&…>` roles). For Discord, `discordTrigger` and `discordMention` are enforced only for message events; for Discord interaction events routing uses author, channel, and other criteria only (no trigger or mention check). Discord channel allowlists/excludes match the event channel id or, for thread events, the parent channel id. When the filter pass yields no bots, Router emits a structured **INFO** log (channelId, parentChannelId, authorId, actorUsername, mentions, ingestBotId, rolePingDetected, routingHint, truncated message prefix) for operations visibility.

# Flow

1. A connector publishes an internal `Event`.
2. `NormalizedEventContext` derives normalized routing fields from the payload.
3. **Filter pass:** For each configured `RoutingRule`, if `RoutingFilter` accepts the event, append that rule’s `botId` to `filterBotIds` (order preserved). For Discord **message** events, filters include trigger, mention, authors, and channel/parent-channel allowlists; for Discord **interaction** events, `discordTrigger` / `discordMention` are skipped (author, channel, and other criteria still apply).
4. **Discord channel overrides** (only when `sourceType` is `discord` and `channelId` is non-blank), evaluated **after** the filter pass and **before** returning `filterBotIds`:
   - When **`PlanningIntakeBindingResolver`** and plan/room stores are wired: **active planning intake** (bound plan with **`PlanningIntakeStage`** not **DONE**) on the event channel or thread yields **coordinator-only** routing before generic feature-room and lifecycle paths; **`Binding.exclusiveCoordinatorThread`** drives **`Router.isCoordinatorExclusivePlanningDiscordEvent`** for engine waiting-session merge. Missing coordinator under exclusive intake uses configured **`setPlanningCoordinatorFallbackBotId`** or returns empty (fail-closed).
   - If `FeatureRoomStateStore` is non-null: **intake/spec thread** first — `getByIntakeThreadId(channelId)`; if present, resolve the primary **coordinator** via `FeatureRoomStateStore.resolveCoordinatorConfiguredBotId`. For **message** and **interaction** events, when the coordinator is present, return a singleton list with that bot id. When the coordinator is missing (degraded state), log a warning for interactions and return participant **configuredBotIds** in stable **PlanningRole** order (non-blank ids only). Else **room channel** — `getByRoomChannelId(channelId)`; if present, return a singleton list with the primary **coordinator** when resolved. These paths **replace** the filter list for that event.
   - Else if `LifecycleContextStore` is non-null: resolve context by `channelId` or `deliveryTargetId`. If the owner’s `configuredBotId` has `handlesOwnedSpaces` in config, return only that owner. If context exists but the owner does **not** have `handlesOwnedSpaces`, log a warning and **fall through** to step 5.
5. **Default:** Return **dedupe**(`filterBotIds`) preserving first-seen order.

# Inputs and outputs

- **Inputs:** Incoming `Event`, bot routing configuration, and normalized payload fields.
- **Outputs:** Matching bot ids for downstream workflow and reasoner execution.

