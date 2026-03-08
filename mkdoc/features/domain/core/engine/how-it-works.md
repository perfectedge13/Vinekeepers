# How it works

# Overview

`VinekeepersEngine` is the event-driven execution loop. For each incoming event it resolves matching bots, runs the registered workflow runner, passes workflow context plus session state into the reasoner, executes approved tool proposals, records audit activity, and emits replies through connector-specific paths when available.

# Flow

1. The engine receives an event from the event bus.
2. Routing returns the bot ids that should handle the event.
3. For each bot, the engine runs the registered `WorkflowRunner` and obtains `WorkflowRunResult`.
4. If a reasoner is present, the engine builds `ReasonerInput`, applies state patches from `ReasonerOutput`, and executes approved proposed tools through `ToolRunner`.
5. Audit is recorded, and replies are delivered when the source connector supports them.

# Inputs and outputs

- **Inputs:** Incoming `Event`, matched bot ids, workflow state, reasoner output, and tool policies.
- **Outputs:** Persisted state updates, tool side effects, audit records, and optional connector replies.

