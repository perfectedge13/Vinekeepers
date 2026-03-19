# Event-driven engine orchestration

# Status

active

# Summary

Event-driven engine routes events to bots (REQ-CORE-003). `VinekeepersEngine` receives events, routes them to matching bots; for Discord message events it also adds any registered bot that has a workflow runner and has state with `WAITING_INPUT` for the event's session key (waiting-session routing) so workflows resume on follow-up messages. It runs the registered `WorkflowRunner`, builds `ReasonerInput` from workflow context and session state, applies `ReasonerOutput` patches and proposed tool calls, records audit activity, builds `OutboundResponse` from workflow or reasoner (rich or text-only), **obtains ReplyTarget** via connector-owned **ReplyTargetResolver** by connector id (source prefix; Bootstrap registers Discord resolver); when no resolver is registered for the source prefix or the resolver returns empty, the engine does not deliver the reply (fail closed); when delivering via reply-sender uses **target.channelId()** and **target.messageId()**, and delivers replies via the connector **sink registry** (lifecycle: respondImmediately, sendFollowUp, updateMessage); no defer in engine. **Reply senders are connector-keyed:** the engine holds a map of reply senders by connector id; Bootstrap registers senders via **setReplySender(connectorId, sender)**. When no sink is registered, the engine uses the **source prefix** (e.g. segment before `":"` in `sourceId`) to look up the sender—**no connector-specific literals** in the engine.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ENGINE | Route events to bots (for Discord message events add bots with WAITING_INPUT for event session key—waiting-session routing); run workflow and reasoner; apply tool/state side effects; obtain ReplyTarget via ReplyTargetResolver by connector id; fail closed when no resolver or resolver returns empty; deliver replies via connector sink registry (uses target.channelId()/messageId()); build OutboundResponse | src/main/java/com/vinekeepers/core/VinekeepersEngine.java |
| ASSET-WORKFLOW-RUNNER | Interface to run workflow for an event and return a structured workflow result | src/main/java/com/vinekeepers/workflow/WorkflowRunner.java |
| ASSET-TOOL-RUNNER | Execute approved tool calls for workflows and reasoners under `ToolPolicy` | src/main/java/com/vinekeepers/tools/ToolRunner.java |
| ASSET-REASONER-INPUT | Reasoner input model including workflow context, current state, and last user message | src/main/java/com/vinekeepers/reasoner/ReasonerInput.java |
| ASSET-REASONER-OUTPUT | Reasoner output model including reply text, optional OutboundResponse richReply, state patch, and proposed tool calls | src/main/java/com/vinekeepers/reasoner/ReasonerOutput.java |
| ASSET-OUTBOUND-RESPONSE | Platform-neutral outbound reply (optional text, optional single ResponseIntent); at least one present | src/main/java/com/vinekeepers/interactions/OutboundResponse.java |
| ASSET-APP-REPLY-SINK | Connector contract with lifecycle operations (respondImmediately, sendFollowUp, updateMessage, openModal, getCapabilities); defer is adapter-internal only | src/main/java/com/vinekeepers/interactions/AppReplySink.java |
| ASSET-REPLY-TARGET | Sealed ReplyTarget (ChannelTarget, InteractionTarget) for where to send replies | src/main/java/com/vinekeepers/interactions/ReplyTarget.java |
| ASSET-CAPABILITIES | Typed connector capabilities (supportedIntents, supportsInteractions, deferRequiredWithinMs, etc.) | src/main/java/com/vinekeepers/interactions/Capabilities.java |
| ASSET-RESPONSE-INTENT | Platform-neutral response intents (PresentChoices, ConfirmAction, CollectText, CollectForm, ShowActions, ShowStatus) | src/main/java/com/vinekeepers/interactions/ResponseIntent.java |

# Sub-pages

- [How it works](engine/how-it-works.md)
- [Change log](engine/change-log.md)
- [Known issues](engine/known-issues.md)
- [Decisions](engine/decisions.md)
- [Contracts](engine/contracts.md)
- [Tests](engine/tests.md)
- [Diagrams](engine/diagrams.md)

