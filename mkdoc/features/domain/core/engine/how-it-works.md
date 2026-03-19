# How it works

# Overview

`VinekeepersEngine` is the event-driven execution loop. For each incoming event it resolves matching bots, runs the registered workflow runner, passes workflow context plus session state into the reasoner, executes approved tool proposals, records audit activity, and emits replies through connector-specific paths when available.

# Flow

1. The engine receives an event from the event bus.
2. Routing returns the bot ids that should handle the event. For Discord message events, the engine additionally adds any registered bot that has a workflow runner and has state with `WAITING_INPUT` for the event's session key (waiting-session routing), so follow-up messages in the same conversation resume the workflow.
3. For each bot, the engine runs the registered `WorkflowRunner` and obtains `WorkflowRunResult`.
4. If a reasoner is present, the engine builds `ReasonerInput`, applies state patches from `ReasonerOutput`, and executes approved proposed tools through `ToolRunner`.
5. Audit is recorded, and replies are delivered when the source connector supports them. The engine obtains **ReplyTarget** via the connector-owned **ReplyTargetResolver** registered by connector id (source prefix); when no resolver is registered or the resolver returns empty, the engine does not deliver the reply (fail closed); when delivering it uses **target.channelId()** and **target.messageId()**. Reply delivery uses a **connector-keyed reply sender map** (registered via `setReplySender(connectorId, sender)`) and optionally a **sink** for the source; when no sink is registered for the source, the engine looks up the sender by **source prefix** (e.g. the part of `sourceId` before `":"`) and has no connector-specific literals.

# Inputs and outputs

- **Inputs:** Incoming `Event`, matched bot ids, workflow state, reasoner output, and tool policies.
- **Outputs:** Persisted state updates, tool side effects, audit records, and optional connector replies.

