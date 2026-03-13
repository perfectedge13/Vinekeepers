# Nova-code run report — Lifecycle delivery follow-up fix

**Workflow:** nova-code  
**Request:** Implementation of lifecycle delivery follow-up fix (discordTokenEnvKey for luna/arrietty, gateway null when lifecycle bot has no gateway, getDiscordUserIdForBot/getSelfUserId/addPermissionOverride, CreateChannelAction with lifecycleOwnerBotId and permission overwrite, outbound-only gateway for non-routed bots).

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

- **discovery:** Pass. Index and registry specs loaded; scope set for lifecycle delivery follow-up fix.
- **schema_gate:** Pass. All impacted specs valid against JSON Schema.
- **drift_gate:** Pass. Index and registry integrity; paths, refs, and traceability validated.
- **plan_change:** Pass. Impact set and change context established for lifecycle delivery follow-up (discordTokenEnvKey, gateway resolution, CreateChannelAction permission overwrite, outbound-only gateway).
- **branch_removal_rename:** Skip. No removal or rename requested.
- **pre_change_lock:** Pass. Re-validated impacted specs before implement.
- **implement:** Pass. Added `discordTokenEnvKey` for luna/arrietty in config; `getGatewayForChannel` returns null when lifecycle bot has no gateway; `getDiscordUserIdForBot`, `getSelfUserId`, `addPermissionOverride` on DiscordGateway/JdaDiscordGateway; CreateChannelAction with `lifecycleOwnerBotId` and permission overwrite after create; outbound-only gateway for non-routed bots.
- **update_tests:** Pass. Tests updated for new gateway/router and CreateChannelAction behavior.
- **update_specs:** Pass. Registry specs updated for REQ-CONNECTORS-DISCORD-001, REQ-CONFIG-001, REQ-BOT-001; assets, validation tests, traceability.
- **update_readme:** Pass. README and docs updated for lifecycle delivery and per-bot identity.
- **post_schema:** Pass. Post-change schema validation passed.
- **traceability:** Pass. Impacted requirements traced; traceability complete.
- **run_tests:** Pass. Tests run: 284, Passed: 284, Failed: 0.
- **build_check:** Pass. `mvn compile` succeeded.
- **reconcile:** Pass. Specs and code reconciled; no dangling refs.
- **mk:** Pass. Docs dir synced from specs and handoff; index, architecture, runbooks, Discord/config feature dossiers updated.
- **docs_gate:** Pass. `npm run validate-docs` passed (run by orchestrator); docs-dir and mkdocs navigation validated.
- **output:** Pass. Final report produced.

---

## 3. Workflow validation

| Check | Result |
|-------|--------|
| Schema Gate | **Pass** |
| Drift Gate | **Pass** |
| Pre-change lock | **Pass** |
| Post-change schema | **Pass** |
| Tests | **Pass** (run: 284, passed: 284, failed: 0) |
| Static analysis | **Pass** (build_check: mvn compile) |
| Reconcile | **OK** |
| Mk | **Pass** (docs-dir sync from specs and handoff) |
| No unresolved spec drift or blocked tests | **Yes** |

---

## 4. Detail sections

### Summary of change

Implemented the **lifecycle delivery follow-up** so lifecycle rooms use the correct bot identity for creation and delivery:

- **discordTokenEnvKey for luna/arrietty:** Bot definitions in `config/bots.yaml` set optional `discordTokenEnvKey` (e.g. `DISCORD_BOT_TOKEN`, `DISCORD_ARRIETTY_TOKEN`); Bootstrap registers one sender/gateway per bot when the key is set and token is present.
- **getGatewayForChannel returns null when lifecycle bot has no gateway:** OutboundDeliveryRouter no longer falls back to a default sender for lifecycle channels; when the lifecycle channel’s configured bot has no registered gateway, `getGatewayForChannel(channelId)` returns null (no default).
- **getDiscordUserIdForBot / getSelfUserId / addPermissionOverride:** DiscordGateway contract extended with `getSelfUserId()`; JdaDiscordGateway implements it; OutboundDeliveryRouter exposes `getDiscordUserIdForBot(botId)`. Gateway supports `addPermissionOverride(channelId, userId, allow, deny)` for channel permission overwrites.
- **CreateChannelAction with lifecycleOwnerBotId and permission overwrite:** CreateChannelAction uses `lifecycleOwnerBotId` from step bind and applies a permission overwrite after channel creation so the lifecycle-owner bot can access the channel.
- **Outbound-only gateway for non-routed bots:** Bots not in routing (e.g. Arrietty) may have an outbound-only JDA gateway—no event listeners, used only for sending and for `getGatewayForChannel`/`getDiscordUserIdForBot` when that bot owns a lifecycle channel.

### Changed files

**Added**

- (Tests/assets as per update_tests and specs.)

**Modified**

- `config/bots.yaml` — `discordTokenEnvKey` for luna and arrietty (e.g. `DISCORD_BOT_TOKEN`, `DISCORD_ARRIETTY_TOKEN`).
- `src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java` — `getGatewayForChannel` returns null when lifecycle bot has no gateway; `getDiscordUserIdForBot` using gateway `getSelfUserId()`.
- `src/main/java/com/vinekeepers/connectors/DiscordGateway.java` — `getSelfUserId()`, `addPermissionOverride(channelId, userId, allow, deny)`.
- `src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java` — implementation of `getSelfUserId`, `addPermissionOverride`.
- `src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java` — `lifecycleOwnerBotId` from bind; apply permission overwrite after create using gateway.
- `src/main/java/com/vinekeepers/core/Bootstrap.java` — outbound-only gateway for non-routed bots when `discordTokenEnvKey` is set.
- `specs/core-registry.yml`, `specs/connectors-registry.yml` — requirements, assets, validation tests, traceability for lifecycle delivery and discordTokenEnvKey.
- `README.md` — project layout and bot config (discordTokenEnvKey, getGatewayForChannel, outbound-only gateway).
- `mkdoc/` — architecture, runbooks, Discord and config feature dossiers (contracts, how-it-works, change-log, etc.).

**Deleted**

- None

### Specs updated

- **specs/specs.yml** — (index; no structural change if none required.)
- **specs/core-registry.yml** — REQ-CONFIG-001, REQ-BOT-001 (discordTokenEnvKey); assets and validation tests for config/bot; traceability.
- **specs/connectors-registry.yml** — REQ-CONNECTORS-DISCORD-001 (getGatewayForChannel null when no gateway, getDiscordUserIdForBot, getSelfUserId, addPermissionOverride, CreateChannelAction lifecycleOwnerBotId and overwrite, outbound-only gateway); assets ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-DISCORD-GATEWAY, ASSET-CREATE-CHANNEL-ACTION; validation tests; traceability and acceptance criteria.

### Schema validation results

- **core-registry.yml:** Pass (valid against req-registry schema).
- **connectors-registry.yml:** Pass (valid against req-registry schema).
- Schema gate (`npm run validate-specs`) passed pre- and post-change.

### Drift Gate result

**Pass.** Index and registry paths, refs, and traceability valid. No spec drift issues reported.

### Test results

**Pass.**

| Count | Value |
|-------|--------|
| Tests run | 284 |
| Passed | 284 |
| Failed | 0 |
| Skipped | (as reported by mvn test) |

Relevant test classes:

- `OutboundDeliveryRouterTest` — gateway resolution, getGatewayForChannel null when lifecycle bot has no gateway, getDiscordUserIdForBot.
- `CreateChannelActionTest` — CreateChannelAction with lifecycleOwnerBotId and permission overwrite.
- `ConfigLoaderTest` — parsing of `discordTokenEnvKey` from bot YAML.
- Existing engine and Discord tests remain passing.

### Static analysis

**Pass.** `mvn compile` (build_check) completed successfully. No compile errors or static-analysis failures.

### Reconcile results

**OK.** Specs and code reconciled; no dangling refs; traceability consistent. Reconcile step reported no issues.

### Mk results

**Pass.** Docs-dir sync from specs and handoff completed. Updated index, architecture, runbooks, and Discord/config feature dossiers (e.g. discord.md contracts, how-it-works, change-log; config change-log, runbooks). docs_gate (`npm run validate-docs`) passed.

### README changes

- Project layout: `com.vinekeepers.connectors` references **OutboundDeliveryRouter** (getGatewayForChannel, getDiscordUserIdForBot, per-bot senders when `discordTokenEnvKey` is set) and Discord gateway (getSelfUserId, addPermissionOverride).
- Bot config: `discordTokenEnvKey` documented for luna/arrietty; outbound-only gateway for non-routed bots; lifecycle delivery and null gateway behavior summarized.

### Issues raised

None. No spec drift issues, blocked tests, or unmet requirements. All gates and steps passed.
