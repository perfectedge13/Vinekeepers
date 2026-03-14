# plan_change output — Bot identity + ConnectorRegistry

**Result:** Pass

**Summary:** Plan change complete; impact set and change_context produced for implement step; no removal/rename of types.

---

## removal_or_rename

`false` — Adding ConnectorIdentity, ConnectorRegistry, ConnectorAdapter, ConnectorContext, DiscordConnectorConfig, DiscordConnectorAdapter; refactoring BotDefinition and ConfigLoader/Bootstrap. No deletion or rename of an existing type.

---

## Impact set

### Impacted registry spec file paths

- `specs/bot-registry.yml`
- `specs/config-registry.yml`
- `specs/core-registry.yml`
- `specs/connectors-registry.yml`

### Impacted asset paths / ids

**Modified (existing):**
- `src/main/java/com/vinekeepers/bot/BotDefinition.java` (ASSET-BOT-DEFINITION)
- `src/main/java/com/vinekeepers/config/ConfigLoader.java` (ASSET-CONFIG-LOADER)
- `src/main/java/com/vinekeepers/core/Bootstrap.java` (ASSET-BOOTSTRAP)
- `src/main/java/com/vinekeepers/bot/Router.java` (ASSET-ROUTER) — consumes handlesMap from getConnectorIdentity
- `config/bots.yaml` (ASSET-BOTS-YAML)
- `src/main/java/com/vinekeepers/config/BotConfig.java` (ASSET-BOT-CONFIG) — retains defaultDiscordTokenEnvKey transitional

**New (to add):**
- `src/main/java/com/vinekeepers/bot/ConnectorIdentity.java` (register in bot-registry)
- `src/main/java/com/vinekeepers/core/ConnectorContext.java` (core-registry or connectors-registry)
- `src/main/java/com/vinekeepers/core/ConnectorAdapter.java`
- `src/main/java/com/vinekeepers/core/ConnectorRegistry.java`
- `src/main/java/com/vinekeepers/connectors/DiscordConnectorConfig.java`
- `src/main/java/com/vinekeepers/connectors/DiscordConnectorAdapter.java`

**Referenced (behavior unchanged, may need traceability):**
- VinekeepersEngine (ASSET-ENGINE), DiscordEventSource (ASSET-DISCORD-SOURCE), OutboundDeliveryRouter (ASSET-OUTBOUND-DELIVERY-ROUTER), DiscordAppReplySink (ASSET-DISCORD-REPLY-SINK), JdaDiscordGateway (ASSET-DISCORD-GATEWAY)

---

## change_context

Path: `.cursor/plans/change_context_bot-identity-connectorregistry.md`

The implement step should read this file for spec excerpts, anti_patterns, scope, and mkdoc references.
