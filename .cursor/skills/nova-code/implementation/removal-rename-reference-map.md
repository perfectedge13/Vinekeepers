# Reference map: getDiscordUserIdForBot → getSelfUserIdForBot

**Rename:** Method on `OutboundDeliveryRouter`: `getDiscordUserIdForBot` → `getSelfUserIdForBot`.

**Scope:** Index, registries, README, docs, code, tests.

---

## Spec index (specs/specs.yml)

- **primary_assets / change_triggers / interfaces.cli.command:** No direct reference to the method name. Index lists registry files and paths only; no update needed for this rename.

---

## Registry specs

| File | Location | Reference |
|------|----------|-----------|
| **specs/connectors-registry.yml** | REQ-CONNECTORS-DISCORD-001 `statement` | "getGatewayForChannel returns null when lifecycle bot has no gateway; **getDiscordUserIdForBot**; gateway createTextChannel/..." |
| **specs/connectors-registry.yml** | REQ-CONNECTORS-DISCORD-001 acceptance_criteria | "**getDiscordUserIdForBot(botId)** returns the bot's Discord user id from that bot's gateway getSelfUserId, or null if the bot has no gateway or getSelfUserId returns null" |
| **specs/workflow-registry.yml** | ~line 933 | "bot's Discord user id (resolved via gateway/**getDiscordUserIdForBot**) when gateway supports it" |

**Asset:** ASSET-OUTBOUND-DELIVERY-ROUTER (path: `src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java`) — no method name in asset path; update requirement/acceptance text only.

**Validation tests:** connectors-registry validation references `OutboundDeliveryRouterTest`; test class renames (method names) are below.

---

## README

- **README.md:** Table mentions `OutboundDeliveryRouter` and connector context; no occurrence of `getDiscordUserIdForBot`. No change required for this rename.

---

## Docs (mkdoc)

| File | Reference |
|------|-----------|
| **mkdoc/features/domain/connectors/discord/contracts.md** | "**getDiscordUserIdForBot(botId)** returns that bot's Discord user id from its gateway getSelfUserId(), or null." |
| **mkdoc/features/domain/connectors/discord/change-log.md** | "**getDiscordUserIdForBot(botId)** returns that bot's Discord user id from its gateway getSelfUserId, or null." |
| **mkdoc/features/domain/connectors/discord.md** | Table: "getGatewayForChannel; **getDiscordUserIdForBot**; no silent fallback for lifecycle" |
| **mkdoc/features/domain/connectors/discord/how-it-works.md** | "**getDiscordUserIdForBot(botId)** returns that bot's Discord user id from its gateway's getSelfUserId(), or null..." and "getGatewayForChannel/**getDiscordUserIdForBot** when that bot owns a lifecycle channel" |
| **mkdoc/features/domain/workflow/cursor-gathering/change-log.md** | "resolved via router **getDiscordUserIdForBot(lifecycleOwnerBotId)**" |

---

## Code

| File | Reference |
|------|-----------|
| **src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java** | Method definition: `public String getDiscordUserIdForBot(String botId)` |
| **src/main/java/com/vinekeepers/workflow/actions/CreateChannelAction.java** | Call site: `router.getDiscordUserIdForBot(lifecycleOwnerBotId)` |

---

## Tests

| File | Reference |
|------|-----------|
| **src/test/java/com/vinekeepers/connectors/OutboundDeliveryRouterTest.java** | Test methods: `getDiscordUserIdForBot_returnsUserIdWhenBotHasGateway`, `getDiscordUserIdForBot_returnsNullWhenBotHasNoGateway`, `getDiscordUserIdForBot_returnsNullWhenBotIdNullOrBlank`; assertions: `router.getDiscordUserIdForBot("luna")`, `router.getDiscordUserIdForBot("unknown-bot")`, `router.getDiscordUserIdForBot(null)`, `router.getDiscordUserIdForBot("")`, `router.getDiscordUserIdForBot("   ")` |
| **src/test/java/com/vinekeepers/workflow/actions/CreateChannelActionTest.java** | Comment: "Do not register any gateway for \"arrietty\" so getDiscordUserIdForBot(\"arrietty\") returns null" |

---

## Cursor workflows/plans/reports (context only; optional to update)

- `.cursor/workflows/change_context_generic-outbound-delivery.md` — plan_change and REQ/ASSET text
- `.cursor/plans/generic-outbound-delivery-abstraction.plan.md` — rename section and asset list
- `.cursor/plans/change_context_bot-identity-connectorregistry.md` — getDiscordUserIdForBot in feature list
- `.cursor/workflows/reports/nova-code-lifecycle-delivery-followup-report.md` — historical report refs

---

## Summary

| Category | Files / locations to update |
|----------|-----------------------------|
| **Code** | OutboundDeliveryRouter.java (method name), CreateChannelAction.java (call) |
| **Tests** | OutboundDeliveryRouterTest.java (3 test method names + 5 assertion calls), CreateChannelActionTest.java (1 comment) |
| **Specs** | connectors-registry.yml (statement + 1 acceptance criterion), workflow-registry.yml (1 line) |
| **Docs** | contracts.md, change-log.md (discord), discord.md, how-it-works.md, cursor-gathering/change-log.md |
| **Index/README** | No change for method rename |
