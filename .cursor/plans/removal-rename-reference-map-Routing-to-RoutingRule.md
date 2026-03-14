# Removal/rename reference map: Routing → RoutingRule

**Scope:** Replace type `Routing` with `RoutingRule` (filter + botId). Remove `Routing.java`; add `RoutingRule.java`; update all usages and spec/docs references.

---

## 1. Path / asset

| Location | Current | Becomes |
|----------|---------|---------|
| **Source file** | `src/main/java/com/vinekeepers/bot/Routing.java` | **Remove.** Add `src/main/java/com/vinekeepers/bot/RoutingRule.java` (same API: filter, botId). |
| **bot-registry.yml** | ASSET-ROUTING, path: `.../Routing.java` | Replace asset: path → `.../RoutingRule.java`; id can stay ASSET-ROUTING or become ASSET-ROUTING-RULE per project convention. |

---

## 2. Spec index (specs/specs.yml)

- **primary_assets:** No entry for Routing.java. No change.
- **change_triggers:** Paths are dirs (src, config, specs, …). No change.
- **interfaces.cli.command:** No reference to Routing. No change.

---

## 3. Registry specs

### bot-registry.yml

- **assets:** ASSET-ROUTING `path: src/main/java/com/vinekeepers/bot/Routing.java` → set path to `src/main/java/com/vinekeepers/bot/RoutingRule.java`; role: e.g. "One rule in routing policy (filter + botId)".
- **REQ-BOT-001** statement: "using **Routing** and EventFilter/RoutingFilter" → "using **RoutingRule** and EventFilter/RoutingFilter".
- **REQ-BOT-003** statement: "ToolPolicy, **Routing**, MemoryPolicy" → "ToolPolicy, **RoutingRule**, MemoryPolicy".
- **REQ-BOT-003** acceptance: "tool policy, **routing**" → keep as "routing" (concept) or "routing rules".
- **traceability.assets** (REQ-BOT-001): ASSET-ROUTING → keep id (path already updated) or ASSET-ROUTING-RULE if id renamed.
- **FEAT-ROUTING** feature.asset_ids: ASSET-ROUTING → same as above.
- **features[].doc_path** routing.md: summary text "through Routing" → "through RoutingRule" (see docs below).

### config-registry.yml

- No asset path for Routing.java. Narrative only: ConfigLoader "routing" / "routing filters"; plan says "Build **RoutingRule** from routing[]". Optional: state in role/statement that ConfigLoader builds RoutingRule from YAML.
- **validation.tests:** buildRouterParsesDiscordMentionRouting, buildRouterParsesDiscordAuthorsRouting — test methods stay; they assert router behavior (which will use RoutingRule).

### workflow-registry.yml

- Narrative and test references to "routing" / "discordMention routing"; testMethod buildRouterParsesDiscordMentionRouting. No asset path. No path change; optional doc wording "RoutingRule" where appropriate.

### core-registry.yml, connectors-registry.yml, events-registry.yml

- Only conceptual "routing"; no type or path. No change required.

---

## 4. README

- "Bot definitions and routing" and "routing filters" are conceptual. No path or type name. Optional: mention that routing policy is implemented with RoutingRule (e.g. in project layout or build section).

---

## 5. Docs (mkdoc)

- **mkdoc/features/domain/bot/routing.md:**
  - Summary: "through `Routing`, `EventFilter`" → "through `RoutingRule`, `EventFilter`".
  - Table: ASSET-ROUTING row path `.../Routing.java` → `.../RoutingRule.java`; role "Routing and filter configuration" → e.g. "One routing policy rule (filter + botId)".
- **mkdoc/features/domain/workflow/cursor-gathering/** (how-it-works, tests, change-log): Only "routing" as concept; no type path. Optional: "RoutingRule" in technical wording.

---

## 6. Java usages

| File | Change |
|------|--------|
| **Router.java** | `List<Routing>` → `List<RoutingRule>`; `addRouting(Routing)` → `addRouting(RoutingRule)`; `for (Routing r : routings)` → `RoutingRule`; Javadoc "from Routing" → "from RoutingRule". |
| **ConfigLoader.java** | `import ...Routing` → `...RoutingRule`; `new Routing(...)` → `new RoutingRule(...)`. |
| **RouterTest.java** | `import ...Routing` → `...RoutingRule`; every `new Routing(...)` → `new RoutingRule(...)`. |
| **ConfigLoaderTest.java** | No type reference to Routing (only config.getRouting() YAML). No change. |
| **VinekeepersEngineTest.java** | `import ...Routing` → `...RoutingRule`; every `new Routing(...)` → `new RoutingRule(...)`. |
| **Bootstrap.java** | Only config.getRouting() (YAML key). No type change. |
| **BotConfig.java** | getRouting/setRouting = YAML structure. No change. |

---

## 7. Validation tests

- **bot-registry** REQ-BOT-001 validation tests: RouterTest, ConfigLoaderTest methods unchanged (they test behavior; implementation will use RoutingRule).
- **config-registry** validation tests: buildRouterParsesDiscordMentionRouting, buildRouterParsesDiscordAuthorsRouting — no path or type in spec; no change.

---

## Summary

- **Remove:** `src/main/java/com/vinekeepers/bot/Routing.java`.
- **Add:** `src/main/java/com/vinekeepers/bot/RoutingRule.java` (same fields: filter, botId).
- **Update:** Router, ConfigLoader, RouterTest, VinekeepersEngineTest (type and imports); bot-registry.yml (path + statements); routing.md (summary + table). Optional: README/config/workflow registry wording.
