# Change context (for plan_change / implement)

## Scope

**User request:** Add the ability to filter Discord messages we listen to by user; set the filter for Luna bot to **novawilde13_72571** (Discord username or id format).

**Request-derived scope:**
- **Features:** FEAT-ROUTING (event routing and normalized context), FEAT-CONFIG (bot config from YAML), FEAT-CURSOR-GATHERING (Luna), FEAT-CONNECTORS-DISCORD (Discord event source).
- **Requirements:** REQ-BOT-001 (Route events to bots by routing rules), REQ-CONFIG-001 (Load bot config from YAML), REQ-LUNA-001 (Luna bot — Discord mention trigger, etc.), REQ-CONNECTORS-DISCORD-001 (Discord event source and reply).
- **Assets:** ASSET-ROUTER, ASSET-ROUTING, ASSET-ROUTING-FILTER, ASSET-EVENT-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-CONFIG-LOADER, ASSET-BOTS-YAML, ASSET-DISCORD-GATEWAY (JdaDiscordGateway).
- **Registry specs:** specs/core-registry.yml, specs/connectors-registry.yml.

---

## Per feature / requirement

### FEAT-ROUTING (Event routing and normalized context)

- **Feature:** id FEAT-ROUTING, slug routing, title "Event routing and normalized context", status active, doc_path features/domain/bot/routing.md. Summary: Router matches events to bots through routing filters over a normalized event view.
- **Requirements:** REQ-BOT-001 — Route events to bots by routing rules. Statement: Router matches incoming events to bots using Routing and EventFilter/RoutingFilter over a normalized event view; events are dispatched only to bots whose filters accept the event, including Discord mention-based routing from payload metadata or @mention text when configured. Acceptance: Router.match(event) returns list of matching BotDefinitions; filters apply Discord/GitHub and other criteria; event routing can normalize actor, channel, text, mentions, repo, and label fields; discordMention filters match case-insensitively. Validation tests: RouterTest (routeMatchesDiscordMentionFromTextCaseInsensitively, routeMatchesDiscordMentionFromMetadataListCaseInsensitively, routeDoesNotMatchWhenDiscordMentionIsMissing). Anti_patterns: (none listed in spec for REQ-BOT-001).
- **Assets:** ASSET-ROUTER (Router.java — match events to bots by routing rules), ASSET-ROUTING (Routing.java), ASSET-EVENT-FILTER (EventFilter.java), ASSET-ROUTING-FILTER (RoutingFilter.java — discordTrigger and discordMention; already has discordAuthors Set), ASSET-NORMALIZED-EVENT-CONTEXT (NormalizedEventContext.java — actor, channel, text, mentions; currently actorId from payload authorId/author/user; no separate author-username field).
- **Doc excerpts (routing):** Contracts — NormalizedEventContext exposes connector-neutral fields (actor, channel, text, mentions, repo, labels, interaction payload). How it works — Router evaluates Event against Routing via NormalizedEventContext; criteria include discordTrigger, discordMention. Decisions — Routing evaluated over normalized view; Discord mention activation from payload or @mention text.

**Implementation notes:** RoutingFilter already has `discordAuthors` (Set<String>) and ConfigLoader parses `discordAuthors` from YAML. Router already applies discordAuthors by checking `context.getActorId()` against the set. The gap: Discord gateway currently puts only `authorId` (snowflake) in the payload; NormalizedEventContext maps actorId from first of "authorId","author","user". So actorId is the snowflake. To support filtering by username (e.g. "novawilde13_72571"), either (1) add author username to Discord payload and a distinct normalized field (e.g. actorUsername), and match discordAuthors against both actorId and actorUsername in Router; or (2) document that discordAuthors accepts only Discord snowflake IDs and user must configure ID. User asked for "username or id format", so implement (1): add "author" (username) to Discord message (and interaction) payload in JdaDiscordGateway; add actorUsername to NormalizedEventContext from payload "author"; in Router for Discord branch, when discordAuthors is non-empty, accept if either actorId or actorUsername (case-insensitive) is in the set.

---

### FEAT-CONFIG (Bot config from YAML)

- **Feature:** id FEAT-CONFIG, slug config, doc_path features/domain/config/config.md. Summary: ConfigLoader loads bot definitions from YAML; BotConfig models the structure.
- **Requirements:** REQ-CONFIG-001 — Load bot config from YAML. Statement: ConfigLoader loads bot definitions, routing, and workflows from YAML; routing filter keys include optional discordTrigger and discordMention. Acceptance: ConfigLoader parses routing filters including optional discordTrigger and discordMention keys from YAML. Traceability: ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML. Validation: ConfigLoaderTest, buildRouterParsesDiscordMentionRouting.
- **Assets:** ASSET-CONFIG-LOADER (ConfigLoader.java — already parses discordAuthors via toSet((List<String>) f.get("discordAuthors"))), ASSET-BOTS-YAML (config/bots.yaml).
- **Doc excerpts:** (config feature docs — use existing structure; no new schema keys per guardrails.)

**Implementation notes:** No schema or spec key changes. Add to Luna routing filter in config/bots.yaml: `discordAuthors: ["novawilde13_72571"]`. ConfigLoader already reads discordAuthors; no code change needed in ConfigLoader for this key.

---

### FEAT-CURSOR-GATHERING (Luna)

- **Feature:** id FEAT-CURSOR-GATHERING, slug cursor-gathering, doc_path features/domain/workflow/cursor-gathering.md. Summary: Luna gathers repository and feature input over Discord, launches Cursor cloud agent run, reports progress back.
- **Requirements:** REQ-LUNA-001 — Luna bot Discord mention trigger, multi-turn gather, Cursor Cloud API. Acceptance: Bot id luna and routing filter discordMention luna; Luna activates when Discord messages reference @Luna in text or mention metadata; bots.yaml specifies workflow and routing. Anti_patterns: Hardcoding Discord channel; storing secrets in state; do not rely on discordTrigger alone for Luna (mentions would be missed).
- **Assets:** ASSET-BOTS-YAML (Luna routing and workflow config).
- **Implementation notes:** Add discordAuthors: ["novawilde13_72571"] under routing[0].filter for botId luna so Luna only reacts to messages from that user (in addition to existing discordMention: "luna").

---

### FEAT-CONNECTORS-DISCORD (Discord event source)

- **Feature:** id FEAT-CONNECTORS-DISCORD, slug discord, doc_path features/domain/connectors/discord.md. Summary: Discord event source and reply; JDA gateway receives messages, preserves mention metadata, delivers replies.
- **Requirements:** REQ-CONNECTORS-DISCORD-001 — Discord event source and reply. Acceptance: DiscordEventSource emits events; can pass mention metadata through payload; gateway handles Interaction Create; DiscordAppReplySink implements AppReplySink; replies delivered to Discord. Traceability: ASSET-DISCORD-GATEWAY (JdaDiscordGateway), ASSET-DISCORD-SOURCE, etc.
- **Assets:** ASSET-DISCORD-GATEWAY (JdaDiscordGateway.java — builds Event payload for messages and interactions; message payload currently has authorId, channelId, threadId, content/text, mentions; no "author" username field).
- **Implementation notes:** In JdaDiscordGateway.toEvent(MessageReceivedEvent), add to the payload map: "author", event.getAuthor().getName() (or getGlobalName() for display name; prefer getName() for username to match "novawilde13_72571"). For interaction events, ensure author/username is also present in the payload if the gateway builds one (authorId is already set from interaction.getUser().getId(); add "author", interaction.getUser().getName()). Use a single key "author" for the username so NormalizedEventContext can read it as actorUsername.

---

## Schema constraints

- Do not add new requirement or asset keys to the registry schema; stay within existing req-registry structure. REQ-CONFIG-001 and REQ-BOT-001 already reference routing filters; discordAuthors is already in code and can be documented in spec statement/criteria if needed without adding new keys.
- Guardrails: No deletion of requirements; no new spec keys; avoid anti_patterns (for Luna: do not rely on discordTrigger alone; do not hardcode channel).

---

## Summary of changes for implement

1. **JdaDiscordGateway:** In `toEvent(MessageReceivedEvent)` add payload key `"author"` with the message author's username (e.g. `event.getAuthor().getName()`). In the interaction-to-Event path (if any) add `"author"` from `interaction.getUser().getName()`.
2. **NormalizedEventContext:** Add a field and getter for author username, e.g. `actorUsername`, derived from payload `"author"` (or `"authorUsername"`). Keep `actorId` from `authorId` so both are available for routing.
3. **Router:** In the Discord branch of `matches()`, when `!f.getDiscordAuthors().isEmpty()`, accept the event if either `context.getActorId()` or `context.getActorUsername()` (normalized, e.g. trim and toLowerCase) is in the set; if both are null and discordAuthors is non-empty, reject.
4. **config/bots.yaml:** Under `routing[0].filter` for Luna add `discordAuthors: ["novawilde13_72571"]`.
5. **Tests:** Add or extend ConfigLoaderTest to assert discordAuthors is parsed from YAML. Add or extend RouterTest to assert Discord events are matched when author or actorUsername is in discordAuthors and rejected when not. Optionally DiscordEventSourceTest/JdaDiscordGateway to assert payload includes "author" for messages.
6. **Docs/specs:** Update REQ-CONFIG-001 / REQ-BOT-001 statement or acceptance criteria to mention discordAuthors if not already. Update mkdoc routing and config (and optionally Discord connector) docs to describe discordAuthors filter. Update ASSET-ROUTING-FILTER role in core-registry if needed to mention discordAuthors.

---

## Doc excerpts (reference)

- **routing/contracts:** NormalizedEventContext exposes connector-neutral fields (actor, channel, text, mentions, repo, label, etc.) and, for interaction events, interactionId, token, customId, values.
- **routing/how-it-works:** Criteria include discordTrigger, discordMention; flow: connector publishes Event → NormalizedEventContext derives fields → RoutingFilter evaluates → Router returns matching bot ids.
- **routing/decisions:** Discord mention activation from payload metadata or @mention text; routing over normalized view.
- **discord.md:** Preserves mention metadata in payload for downstream routing.
