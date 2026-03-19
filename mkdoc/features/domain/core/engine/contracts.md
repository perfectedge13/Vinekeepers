# Contracts

# APIs

None. The engine is an internal orchestration component.

# Schemas

`ReasonerInput` carries workflow context, current state, and the last normalized user message. `ReasonerOutput` carries reply text, optional state patches, and proposed tool calls. `WorkflowRunResult` carries workflow reply and lifecycle flags.

# Interfaces

- **`VinekeepersEngine`:** subscribes to events and coordinates bot execution. Obtains **ReplyTarget** via **ReplyTargetResolver** registered by connector id (`registerReplyTargetResolver(connectorId, resolver)`); when no resolver is registered or resolver returns empty, does not deliver reply (fail closed); reply-sender delivery uses **target.channelId()** and **target.messageId()**. Bootstrap registers resolvers per connector (e.g. Discord: `registerReplyTargetResolver("discord", new DiscordReplyTargetResolver())`).
- **`WorkflowRunner`:** engine-facing workflow execution contract.
- **`ToolRunner`:** executes approved proposed tool calls.
- **`Reasoner`:** supplies reply text, state patches, and proposed tool calls.

