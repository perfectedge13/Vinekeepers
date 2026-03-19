# Change log

# Entries

## 2026-03-13

- **Reply target resolution refactor:** Engine obtains **ReplyTarget** via connector-owned **ReplyTargetResolver** by connector id (source prefix); Discord resolver lives in connectors (**DiscordReplyTargetResolver**); when no resolver is registered or resolver returns empty, the engine does not deliver the reply (fail closed); when delivering the engine uses **target.channelId()** and **target.messageId()**. Bootstrap registers the Discord resolver (e.g. `engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver())`).
- **Connector-keyed reply sender map:** Engine uses **setReplySender(connectorId, sender)** and a connector-keyed map for reply senders. When no sink is registered for the event source, fallback resolves the sender by **source prefix** (e.g. part of `sourceId` before `":"`); the engine contains **no connector-specific literals** for delivery. Bootstrap registers the Discord sender with the engine (e.g. `engine.setReplySender("discord", outboundDeliveryRouter)`).

## 2026-03-09

- **Discord waiting-session routing fix:** Engine correctly adds bots with a workflow runner and state `WAITING_INPUT` for the event's session key on Discord message events, so follow-up messages in the same conversation (same user and channel) resume the workflow without requiring an @mention. Session key is derived from the event (e.g. channel_user) so messages from a different user or channel do not continue the session. Tests added: discordMessageWithoutMentionAndNoWaitingSession_doesNotRouteToLuna, discordMessageWithMention_routesToLuna, followUpMessageWithoutMention_sameUserAndChannel_continuesWorkflowWhenLunaHasWaitingInput, messageFromDifferentUserOrChannel_doesNotContinueSession.

## 2026-03-08

- **Connector sink and rich replies:** Engine delivers replies via connector **sink registry** (by sourceId prefix). Sink contract `AppReplySink` provides lifecycle operations: respondImmediately, sendFollowUp, updateMessage, openModal, getCapabilities. Engine builds `OutboundResponse` from workflow or reasoner (text and/or `ResponseIntent`), resolves `ReplyTarget` (ChannelTarget, InteractionTarget) from the event, and calls the registered sink. Defer is adapter-owned only; engine never requests or triggers defer.

## Prior

- Split engine orchestration into its own core-domain feature dossier rather than describing it inside the bootstrap/specs page.
- Captured workflow, reasoner, tool, and reply sequencing directly from `REQ-CORE-003`.

