# How it works

# Overview

The engine builds `ReasonerInput` from the current event, bot id, workflow reply context, current session state, and the last normalized user message, then calls `Reasoner.reason(input)`. `ReasonerOutput` may include reply text, a state patch, and proposed tool calls. `StubReasoner` or a real LLM/rules implementation can be used by the engine.

# Flow

1. The engine resolves the bot's session key and loads the current workflow state.
2. The engine builds `ReasonerInput` with workflow context and the last normalized user message.
3. `reasoner.reason(input)` returns `ReasonerOutput`.
4. The engine applies any state patch and persists it.
5. Proposed tool calls are executed only through `ToolRunner` under the bot's `ToolPolicy`.
6. If the workflow was silent, the reasoner reply can become the outgoing response.

# Inputs and outputs

- **Inputs:** `ReasonerInput` (`event`, `botId`, workflow context, current state, `lastUserMessage`). **Outputs:** `ReasonerOutput` (`replyText`, `statePatch`, `proposedToolCalls`, completed flag).

