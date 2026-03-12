# Nova-code run report — Outbound delivery router (lifecycle rooms)

**Workflow:** nova-code  
**Request:** Implementation of outbound delivery router (real configured bot identity for lifecycle rooms).

---

## 1. Workflow executed

| Step | Status |
|------|--------|
| discovery | **Pass** |
| schema_gate | **Pass** |
| drift_gate | **Pass** |
| plan_change | **Pass** |
| branch_removal_rename | **Skip** |
| pre_change_lock | **Pass** |
| implement | **Pass** |
| update_tests | **Pass** |
| update_specs | **Pass** |
| update_readme | **Pass** |
| post_schema | **Pass** |
| traceability | **Pass** |
| run_tests | **Pass** |
| build_check | **Pass** |
| reconcile | **Pass** |
| mk | **Pass** |
| docs_gate | **Pass** |
| output | **Pass** |

All steps in `run_order` were executed. **branch_removal_rename** skipped (removal_or_rename not set). Workflow complete.

---

## 2. Per-step outcome

- **discovery:** Pass. Index and registry specs loaded; primary assets and README summarized; scope from context.
- **schema_gate:** Pass. All impacted specs valid against JSON Schema.
- **drift_gate:** Pass. Index and registry integrity; paths, refs, and traceability validated.
- **plan_change:** Pass. Impact set and change context established for outbound delivery router and per-bot Discord identity.
- **branch_removal_rename:** Skip. No removal or rename requested.
- **pre_change_lock:** Pass. Re-validated impacted specs before implement.
- **implement:** Pass. Added `OutboundDeliveryRouter`; added `discordTokenEnvKey` to bot config; Bootstrap wiring for per-bot senders; `DiscordAppReplySink` uses router for sender resolution.
- **update_tests:** Pass. Added `OutboundDeliveryRouterTest`; updated `ConfigLoaderTest` for `discordTokenEnvKey`.
- **update_specs:** Pass. Registry specs updated for REQ-CONNECTORS-DISCORD-001, REQ-CONFIG-001, REQ-BOT-001; assets, validation tests, traceability.
- **update_readme:** Pass. README and artifact categories updated for OutboundDeliveryRouter and per-bot identity.
- **post_schema:** Pass. Post-change schema validation passed.
- **traceability:** Pass. Impacted requirements traced; traceability complete.
- **run_tests:** Pass. Tests run: 279, Passed: 279, Failed: 0.
- **build_check:** Pass. `mvn compile` succeeded.
- **reconcile:** Pass. Specs and code reconciled; no dangling refs.
- **mk:** Pass. Docs dir synced from specs and handoff; index, architecture, runbooks, Discord feature dossiers updated.
- **docs_gate:** Pass. `npm run validate-docs` passed; docs-dir and mkdocs navigation validated.
- **output:** Pass. Final report produced.

---

## 3. Workflow validation

| Check | Result |
|-------|--------|
| Schema Gate | **Pass** |
| Drift Gate | **Pass** |
| Pre-change lock | **Pass** |
| Post-change schema | **Pass** |
| Tests | **Pass** (run: 279, passed: 279, failed: 0) |
| Static analysis | **Pass** (build_check: mvn compile) |
| Reconcile | **OK** |
| Mk | **Pass** (docs-dir sync from specs and handoff) |
| No unresolved spec drift or blocked tests | **Yes** |

---

## 4. Detail sections

### Summary of change

Implemented the **outbound delivery router** so lifecycle rooms use a real configured bot identity for Discord delivery:

- **OutboundDeliveryRouter:** Routes outbound Discord delivery; resolves sender from `ReplyTarget` and lifecycle context (`configuredBotId`). For non-lifecycle channels uses a default sender; for lifecycle channels uses only the configured bot’s sender—no silent fallback when that sender is unavailable (error logged, message not sent).
- **Per-bot Discord identity:** Optional `discordTokenEnvKey` in bot config (env var name for that bot’s Discord token). Bootstrap registers one sender per bot when the key is set and the env var is present.
- **Bootstrap:** Wires `OutboundDeliveryRouter`; registers per-bot senders from config; provides router to the Discord sink.
- **DiscordAppReplySink:** Uses `OutboundDeliveryRouter` for ChannelTarget delivery (sender resolution and send).

### Changed files

**Added**

- `src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java`

**Modified**

- `src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java` — uses OutboundDeliveryRouter for sender resolution and delivery
- `src/main/java/com/vinekeepers/core/Bootstrap.java` — builds OutboundDeliveryRouter; registers per-bot senders; passes router to Discord sink
- `src/main/java/com/vinekeepers/config/ConfigLoader.java` — parses `discordTokenEnvKey` from bot YAML
- Bot model (e.g. `BotDefinition` / config DTO) — optional `discordTokenEnvKey` field
- `config/bots.yaml` — optional `discordTokenEnvKey` per bot (structure/docs)
- `specs/core-registry.yml` — requirements, assets, validation tests, traceability for OutboundDeliveryRouter and discordTokenEnvKey
- `README.md` — project layout and bot config (OutboundDeliveryRouter, discordTokenEnvKey)
- `mkdoc/` — architecture, runbooks, Discord feature dossiers (discord.md, contracts, how-it-works, tests, change-log, etc.)

**Deleted**

- None

### Specs updated

- **specs/specs.yml** — (index; no structural change if none required)
- **specs/core-registry.yml** — REQ-CONNECTORS-DISCORD-001 (lifecycle sender resolution, no silent fallback); REQ-CONFIG-001 / REQ-BOT-001 (discordTokenEnvKey); assets ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-DISCORD-REPLY-SINK; validation tests UNIT-OUTBOUND-DELIVERY-ROUTER; traceability and acceptance criteria

### Schema validation results

- **core-registry.yml:** Pass (valid against req-registry schema).
- **connectors-registry.yml:** Pass (or skipped if unchanged).
- Schema gate (`npm run validate-specs`) passed pre- and post-change.

### Drift Gate result

**Pass.** Index and registry paths, refs, and traceability valid. No spec drift issues reported.

### Test results

**Pass.**

| Count | Value |
|-------|--------|
| Tests run | 279 |
| Passed | 279 |
| Failed | 0 |
| Skipped | (as reported by mvn test) |

Relevant test classes:

- `OutboundDeliveryRouterTest` — lifecycle context → configuredBotId sender resolution; default sender when no context; no fallback when lifecycle channel has no sender for that bot
- `ConfigLoaderTest` — parsing of `discordTokenEnvKey` from bot YAML
- Existing engine and Discord tests remain passing

### Static analysis

**Pass.** `mvn compile` (build_check) completed successfully. No compile errors or static-analysis failures.

### Reconcile results

**OK.** Specs and code reconciled; no dangling refs; traceability consistent. Reconcile step reported no issues.

### Mk results

**Pass.** Docs-dir sync from specs and handoff completed. Updated index, architecture, runbooks, and Discord feature dossiers (e.g. discord.md, contracts, how-it-works, tests, change-log). docs_gate (`npm run validate-docs`) passed.

### README changes

- Project layout table: `com.vinekeepers.connectors` now references **OutboundDeliveryRouter** (resolves sender from lifecycle context; per-bot senders when `discordTokenEnvKey` is set) and DiscordAppReplySink.
- Bot config: `discordTokenEnvKey` documented; OutboundDeliveryRouter and lifecycle sender behavior (no silent fallback for lifecycle rooms) summarized.

### Issues raised

None. No spec drift issues, blocked tests, or unmet requirements. All gates and steps passed.
