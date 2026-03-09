# Cursor-backed gathering workflow

# Status

active

# Summary

Luna bot gathering workflow and Cursor Cloud execution (REQ-LUNA-001). The shipped `luna` bot stays config-driven in `config/bots.yaml`, activates from `discordMention: luna` (and optional `discordAuthors`), runs configured workflow `luna_cursor` to gather repository and feature inputs across multiple Discord turns, then delegates execution to `cursor.fullRun` and the official Cursor Cloud Agents API. **Guided repo selection:** the first step uses `choiceProvider: githubRepos` to present GitHub repos (from GitHub API, sort=updated), "Use last repo", and "Custom repo"; last repo is stored per author after a successful launch. A **confirmation step** (Launch / Edit repo / Edit request / Cancel) runs before `cursor.fullRun`. Discord can show buttons or a select menu for choices. Vinekeepers stores `LunaCloudRunState` and `luna:lastRepo:{authorId}`, polls Cursor, and relays launch/progress/PR updates back to Discord.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-LUNA-STATE | Luna per-conversation state (project, code change, step) | src/main/java/com/vinekeepers/workflow/GatheringState.java |
| ASSET-LUNA-WORKFLOW | Luna workflow class used by tests while production Luna uses configured workflow `luna_cursor` | src/main/java/com/vinekeepers/workflow/CursorCloudGatheringWorkflow.java |
| ASSET-CURSOR-ADAPTER | Cursor Cloud Agents API adapter interface | src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapter.java |
| ASSET-CURSOR-ADAPTER-IMPL | Cursor Cloud Agents API HTTP client implementation | src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapterImpl.java |
| ASSET-LUNA-RUN-STATE | In-memory state for the active Cursor run and Discord reply target | src/main/java/com/vinekeepers/core/cursor/LunaCloudRunState.java |
| ASSET-CURSOR-RUN-MONITOR | Poll Cursor status/conversation and relay updates to Discord | src/main/java/com/vinekeepers/core/cursor/CursorCloudRunMonitor.java |
| ASSET-CURSOR-AGENT-LAUNCH-REQUEST | Request payload model for launching a Cursor Cloud agent run | src/main/java/com/vinekeepers/core/cursor/CursorAgentLaunchRequest.java |
| ASSET-CURSOR-AGENT-CONVERSATION | Conversation response model for Cursor Cloud agent feedback | src/main/java/com/vinekeepers/core/cursor/CursorAgentConversation.java |
| ASSET-CURSOR-AGENT-DETAILS | Details model for Cursor Cloud agent status lookups | src/main/java/com/vinekeepers/core/cursor/CursorAgentDetails.java |
| ASSET-CURSOR-AGENT-LAUNCH-RESULT | Launch result model returned after starting a Cursor Cloud agent run | src/main/java/com/vinekeepers/core/cursor/CursorAgentLaunchResult.java |
| ASSET-CURSOR-AGENT-MESSAGE | Message model representing Cursor Cloud agent conversation entries | src/main/java/com/vinekeepers/core/cursor/CursorAgentMessage.java |
| ASSET-CURSOR-CLOUD-TRANSPORT | HTTP transport abstraction for Cursor Cloud adapter calls | src/main/java/com/vinekeepers/core/cursor/CursorCloudTransport.java |
| ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE | Transport response model used by Cursor Cloud adapter calls | src/main/java/com/vinekeepers/core/cursor/CursorCloudTransportResponse.java |
| ASSET-CURSOR-CLOUD-EXCEPTION | Exception type for Cursor Cloud transport and adapter failures | src/main/java/com/vinekeepers/core/cursor/CursorCloudException.java |
| ASSET-CURSOR-FULL-RUN-TOOL | Tool wrapper that launches a Cursor Cloud Agent run and stores launch state and last repo per author | src/main/java/com/vinekeepers/tools/CursorFullRunTool.java |
| ASSET-GITHUB-REPOS-CHOICE-PROVIDER | Fetches user repos from GitHub API for guided repo selection; uses GITHUB_TOKEN | src/main/java/com/vinekeepers/providers/GitHubReposChoiceProvider.java |
| ASSET-BOTS-YAML | Bot definitions YAML including Luna routing and configured workflow reference | config/bots.yaml |
| ASSET-BOOTSTRAP | Register Luna workflow runner, tools, and adapter dependencies | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-ENGINE | Deliver workflow replies to Discord when the source is Discord | src/main/java/com/vinekeepers/core/VinekeepersEngine.java |

# Sub-pages

- [How it works](cursor-gathering/how-it-works.md)
- [Change log](cursor-gathering/change-log.md)
- [Known issues](cursor-gathering/known-issues.md)
- [Decisions](cursor-gathering/decisions.md)
- [Contracts](cursor-gathering/contracts.md)
- [Tests](cursor-gathering/tests.md)
- [Diagrams](cursor-gathering/diagrams.md)

