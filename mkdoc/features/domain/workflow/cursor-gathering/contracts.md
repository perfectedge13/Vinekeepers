# Contracts

# APIs

This feature calls the official Cursor Cloud Agents API:

- `POST /v0/agents`
- `GET /v0/agents/{id}`
- `GET /v0/agents/{id}/conversation`
- `POST /v0/agents/{id}/followup` (supported by the adapter contract)

# Schemas

Luna configuration in `config/bots.yaml` supplies bot routing, `workflowRef: luna_cursor`, and the ordered gathering steps. `ConfigurableWorkflowState` stores the conversational gather state, while `LunaCloudRunState` stores the launched run identity and Cursor feedback snapshot.

# Interfaces

- **`CursorCloudAdapter`:** abstraction over Cursor Cloud Agent launch, status, conversation, and follow-up operations.
- **`CursorFullRunTool`:** tool surface that launches the remote run and stores `LunaCloudRunState`.
- **`CursorCloudRunMonitor`:** polling component that turns Cursor state changes into Discord replies.
- **`CursorCloudGatheringWorkflow`:** legacy workflow class retained for tests while production uses the configured flow.

