# Change context (for plan_change / implement)

## Scope

**Request-derived:** Discord interaction routing fix. Change `Router.matches()` so `discordTrigger` and `discordMention` apply only when event kind is `"message"`; for `"interaction"` events skip those checks so author/channel alone determine routing. Add RouterTest: interaction from allowed author routes to bot; interaction from other user does not.

**Impacted registry slice:** core-registry.yml (feature FEAT-ROUTING, requirement REQ-BOT-001, assets ASSET-ROUTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT).

**Impacted code:** `src/main/java/com/vinekeepers/bot/Router.java`, `src/test/java/com/vinekeepers/bot/RouterTest.java`. Config and connectors/engine unchanged per request.

---

## Per feature: Event routing and normalized context (FEAT-ROUTING)

**Feature:** Event routing and normalized context. Status: active. Doc path: features/domain/bot/routing.md. Summary: Router matches events to bots through routing filters over a normalized event view.

**Requirements (REQ-BOT-001):**
- **id:** REQ-BOT-001  
- **title:** Route events to bots by routing rules  
- **statement:** Router matches incoming events to bots using Routing and EventFilter/RoutingFilter over a normalized event view; events are dispatched only to bots whose filters accept the event, including Discord mention-based routing from either payload metadata or @mention text when configured, and optional Discord author filtering (discordAuthors) by actor id or normalized actor username.  
- **Acceptance criteria:** Router.match(event) returns list of matching BotDefinitions; Filters apply Discord/GitHub and other criteria from routing config; Event routing can normalize actor, channel, text, mentions, repo, and label fields from incoming payloads; Normalized event context exposes actorId and actorUsername for routing; discordMention filters match case-insensitively against normalized mentions from payload metadata or message text; discordAuthors filters match when event author is in the configured list.  
- **Traceability assets:** ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT.  
- **Validation tests:** UNIT-ROUTER (RouterTest, verify event routing and filter matching); UNIT-ROUTER-DISCORD-MENTION-TEXT (routeMatchesDiscordMentionFromTextCaseInsensitively); UNIT-ROUTER-DISCORD-MENTION-METADATA (routeMatchesDiscordMentionFromMetadataListCaseInsensitively); UNIT-ROUTER-DISCORD-MENTION-MISSING (routeDoesNotMatchWhenDiscordMentionIsMissing); UNIT-ROUTER-DISCORD-AUTHORS (ConfigLoaderTest.buildRouterParsesDiscordAuthorsRouting).  
- **Anti-patterns (requirement):** none listed. Luna feature anti_pattern: "Do not rely on discordTrigger alone for Luna activation because @mentions in Discord channels would be missed."

**Assets:**
- **ASSET-ROUTER** — path: src/main/java/com/vinekeepers/bot/Router.java. Role: Match events to bots by routing rules, including Discord mention and discordAuthors filters. Requires: REQ-BOT-001.  
- **ASSET-ROUTING-FILTER** — path: src/main/java/com/vinekeepers/bot/RoutingFilter.java. Role: Apply routing filters to events, including discordTrigger, discordMention, and discordAuthors matching. Requires: REQ-BOT-001.  
- **ASSET-NORMALIZED-EVENT-CONTEXT** — path: src/main/java/com/vinekeepers/bot/NormalizedEventContext.java. Role: Normalize event payloads for routing and session decisions; exposes actorId, actorUsername, and for kind interaction optional interaction payload (interactionId, token, customId, values). Requires: REQ-BOT-001.

**Doc excerpts:**  
- **Decisions (routing/decisions.md):** Discord filter by user (discordAuthors) — Add optional discordAuthors; NormalizedEventContext exposes actorId and actorUsername; RoutingFilter matches when event author is in list. Routing evaluated over normalized view; Discord mention activation from payload metadata or @mention text.  
- **Contracts (routing/contracts.md):** NormalizedEventContext exposes connector-neutral fields including for events with kind: interaction (interactionId, token, customId, values). Router returns bot ids whose routing rules match the event. RoutingFilter is built-in filter for configured predicates.  
- **Known issues (routing/known-issues.md):** Available routing fields depend on connector payloads; adding new predicates requires code in filter layer.

---

## Implementation summary for implement step

- **Router.matches() (Discord branch):** When `kind` is `"interaction"`, do not evaluate `discordTrigger` or `discordMention`; only discordAuthors and discordChannels apply. When `kind` is `"message"` (or any other), keep current behavior (e.g. trigger and mention checks). Use `context.getEventType()` (already available as `kind`) to branch.  
- **RouterTest:** Add (1) test that an event with kind `"interaction"`, source discord, and author in discordAuthors (and optional channel in discordChannels) routes to the bot. Add (2) test that an event with kind `"interaction"`, same filter with discordAuthors set, but different author (not in list) does not route. Do not add or change config/bots.yaml or connector/engine code.

---

## Schema constraints

Requirement and asset keys in core-registry follow req-registry schema (id, title, statement, acceptance.criteria, traceability.assets, validation.tests). No new spec keys; no removal of requirements.
