# Decisions

# Entries

## 2026-03-13 — Generic ReplySender and OutboundGateway for outbound delivery

Context: Core and workflow actions need a connector-agnostic way to send replies and to obtain gateway operations (e.g. getSelfUserId for permission overwrites). Discord-specific types were previously the only contract.

Decision: Introduce **ReplySender** (send(channelId, messageId, content)) and **OutboundGateway** (connector execution surface with Discord-shaped API: send, getSelfUserId, createTextChannel, createThreadChannel, addPermissionOverride) in the connectors package. Core and engine use ReplySender and ReplyTargetResolver; OutboundGateway is used only by connector-owned code and OutboundDeliveryRouter (routing + gateway resolution). OutboundDeliveryRouter implements ReplySender and exposes getSelfUserIdForBot(botId); gateway = execution surface for channel/thread/permission and send. Discord implements both (DiscordReplySender, DiscordGateway). DiscordAppReplySink casts to DiscordGateway when calling interaction-specific methods (defer, update).

Consequence: Core stays connector-agnostic; gateways are the connector execution surface; router handles routing and gateway resolution. Lifecycle and workflow actions use the router for sender resolution and getSelfUserIdForBot for overwrites.

## 2026-03-07 — Preserve Discord mentions in connector events

Context: Mention-based bot routing needs more than raw message text because Discord payloads can already identify referenced users or bots.

Decision: Keep mention metadata on the internal Discord event payload and document reply delivery through the same connector abstraction.

Consequence: Routing can match configured bot mentions from payload metadata or normalized text, and connector tests now treat the `mentions` payload field as part of the contract.

## 2026-03-23 — User/bot mentions only (gateway + normalized text)

Context: Role pings (`<@&roleId>`) and non-snowflake angle-bracket bodies must not satisfy `discordMention` filters; server nicknames should not let a human mimic a bot's configured mention string.

Decision: **NormalizedEventContext** parses only `<@` / `<@!` plus numeric snowflakes from message text; **JdaDiscordGateway** fills `mentions` from mentioned Discord users (id, username, global display name) and does not add member server nicknames.

Consequence: Ops-channel allowlists and `discordMention` routing stay aligned with real Discord user/bot mentions; Router logs a structured INFO line when no routing rule matches (including `ingestBotId` and a short text prefix).

