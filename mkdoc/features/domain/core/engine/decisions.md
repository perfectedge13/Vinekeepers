# Decisions

# Selected decisions

## 2026-03-09 — Waiting-session routing for Discord

For Discord message events, the engine adds any registered bot that has a workflow runner and has state with `WAITING_INPUT` for the event's session key (in addition to bots matched by routing filters). This allows follow-up messages in the same conversation to resume the workflow without requiring a new @mention. Session key is resolved from the event (e.g. channel_user); only the same user in the same channel continues the session.

- Workflow execution runs before reasoner execution so structured workflow context can shape the reasoner input.
- Proposed tool calls are executed by `ToolRunner` under policy control instead of directly by the reasoner.
- Engine orchestration is documented separately from bootstrap because startup wiring and per-event execution have different responsibilities.

