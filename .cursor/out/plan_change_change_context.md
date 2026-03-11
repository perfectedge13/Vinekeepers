# Change context (for plan_change / implement)

## Scope

**Request:** Luna Discord routing bug fix (already implemented). Router supports numeric `discordAuthors` (match actorId) and non-numeric (match actorUsername); DEBUG log when no bot matched; config comment; RouterTest updates.

**Impacted registry slice:** core-registry.yml — routing (FEAT-ROUTING), config (FEAT-CONFIG), Luna (FEAT-CURSOR-GATHERING).

**Features:** FEAT-ROUTING, FEAT-CONFIG, FEAT-CURSOR-GATHERING.

**Requirements:** REQ-BOT-001, REQ-CONFIG-001, REQ-LUNA-001.

**Assets:** ASSET-ROUTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-BOTS-YAML.

---

## Per feature

### FEAT-ROUTING (Event routing and normalized context)

- **Feature:** id FEAT-ROUTING, slug routing, title "Event routing and normalized context", status active, doc_path features/domain/bot/routing.md. Summary: Router matches events to bots through routing filters over a normalized event view.
- **Requirements:** REQ-BOT-001 — Route events to bots by routing rules. Statement: Router matches incoming events using Routing and EventFilter/RoutingFilter over a normalized event view; optional discordAuthors by actor id or normalized actor username. Criteria: Router.match returns matching BotDefinitions; discordAuthors filters match when event author (actorId or actorUsername normalized) is in the configured list. Validation tests: UNIT-ROUTER (RouterTest), UNIT-ROUTER-DISCORD-AUTHORS (ConfigLoaderTest.buildRouterParsesDiscordAuthorsRouting), UNIT-ROUTER-INTERACTION-ALLOWED-AUTHOR, UNIT-ROUTER-INTERACTION-OTHER-USER. Anti_patterns: (none on REQ-BOT-001 in registry).
- **Assets:** ASSET-ROUTER (src/main/java/com/vinekeepers/bot/Router.java — match events by routing rules; Discord mention/trigger only for message events; interaction uses author/channel). ASSET-ROUTING-FILTER (RoutingFilter — discordAuthors and discordChannels apply to all Discord events). ASSET-NORMALIZED-EVENT-CONTEXT (actorId, actorUsername from payload author/authorId).
- **Doc excerpts:**  
  **Decisions:** discordAuthors added for restricting activation to specific Discord users; match when event author in list; NormalizedEventContext exposes actorId/actorUsername. Discord interaction routing: trigger/mention only for message events; interactions use author and channel only.  
  **Contracts:** discordAuthors and discordChannels apply to all Discord events. NormalizedEventContext exposes actorId, actorUsername (from author/authorId).  
  **Known-issues:** Routing fields depend on connector-published context; new predicates require filter code changes.

### FEAT-CONFIG (Bot config from YAML)

- **Feature:** FEAT-CONFIG, slug config, doc_path features/domain/config/config.md. Summary: ConfigLoader loads bot definitions from YAML.
- **Requirements:** REQ-CONFIG-001 — Load bot config from YAML. Parses routing filters including optional discordTrigger, discordMention, discordAuthors. Validation: ConfigLoaderTest.buildRouterParsesDiscordAuthorsRouting.
- **Assets:** ASSET-BOTS-YAML (config/bots.yaml — Luna routing, optional discordAuthors; comment: numeric Discord user id matches actorId, username matches actorUsername case-insensitive).

### FEAT-CURSOR-GATHERING (Luna)

- **Feature:** FEAT-CURSOR-GATHERING, slug cursor-gathering, doc_path features/domain/workflow/cursor-gathering.md. Luna routing uses discordMention and optional discordAuthors.
- **Requirements:** REQ-LUNA-001 — Luna bot; optional discordAuthors restricts to listed Discord users (e.g. novawilde13_72571). Criteria: routing activates when message references @Luna and satisfies discordAuthors when configured.
- **Assets:** ASSET-BOTS-YAML (Luna routing with discordAuthors). ASSET-ROUTER, ASSET-NORMALIZED-EVENT-CONTEXT (author data for routing).
- **Doc excerpts (cursor-gathering change-log):** Luna discordAuthors in config so only listed users can trigger Luna; gateway and NormalizedEventContext supply author data.

---

## Implementation notes (already done)

- **Router:** Numeric `discordAuthors` entry → match by actorId; non-numeric → match by actorUsername (case-insensitive). When no bot matched, log at DEBUG: source, kind, authorId, actorUsername, mentions, channelId.
- **config/bots.yaml:** Comment on discordAuthors: numeric Discord user id (matches actorId) or username (matches actorUsername, case-insensitive).
- **RouterTest:** Tests for match by actorId (numeric), match by actorUsername (including case-insensitive), no match when author not in filter; interaction from allowed/other user; mention + author in filter.

---

## Schema constraints

- Requirement keys: id, title, statement, status, acceptance.criteria, traceability.assets, validation.tests. No new spec keys; stay within req-registry schema.
- Guardrails: Do not delete requirements; avoid anti_patterns on requirements/assets; repair drift before coding.
