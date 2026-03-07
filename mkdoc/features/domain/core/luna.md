# Luna bot — Discord /Luna, multi-turn gather, Cursor Cloud API

# Status

active

# Summary

Luna bot is registered with id `luna` and triggered by `/Luna` on Discord. Luna uses the **configured** workflow **luna_cursor** (defined in config/bots.yaml): it asks for project and code change, then invokes the **cursor.fullRun** action, which uses the Cursor Cloud API adapter (create branch, nova-commit, push, optional PR). The engine delivers workflow replies to Discord when the source is Discord.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-LUNA-STATE | Luna per-conversation state (project, code change, step) | src/main/java/com/vinekeepers/workflow/GatheringState.java |
| ASSET-LUNA-WORKFLOW | Multi-turn workflow to gather project and code change then invoke Cursor adapter (legacy; Luna now uses configured workflow luna_cursor) | src/main/java/com/vinekeepers/workflow/CursorCloudGatheringWorkflow.java |
| ASSET-CURSOR-ADAPTER | Cursor Cloud API adapter interface | src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapter.java |
| ASSET-CURSOR-ADAPTER-IMPL | Cursor Cloud API adapter implementation (env-based config) | src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapterImpl.java |
| ASSET-ENGINE | Route events to bots; deliver workflow replies to Discord when source is Discord | src/main/java/com/vinekeepers/core/VinekeepersEngine.java |
| ASSET-BOOTSTRAP | Wire engine, config, connectors (including Luna and Discord) | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-BOTS-YAML | Bot definitions YAML (including Luna routing and trigger) | config/bots.yaml |

# Sub-pages

- [How it works](luna/how-it-works.md)
- [Change log](luna/change-log.md)
- [Known issues](luna/known-issues.md)
- [Decisions](luna/decisions.md)
- [Contracts](luna/contracts.md)
- [Tests](luna/tests.md)
- [Diagrams](luna/diagrams.md)
