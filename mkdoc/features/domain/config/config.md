# Bot config from YAML

# Status

active

# Summary

Load bot config from YAML (REQ-CONFIG-001). `ConfigLoader` loads bot definitions from YAML; `BotConfig` models the structure. Each bot may specify `workflow.type` (for example `stub` or `configured`), optional `workflow.params` (`workflowRef` or inline `steps`), routing filter keys such as backward-compatible `discordTrigger`, optional `discordMention`, and optional `discordAuthors`, and runtime options such as `conversationMode` and `sessionKeyStrategy`. Luna uses `type: configured`, `workflowRef: luna_cursor`, and `discordMention: "luna"` (optionally `discordAuthors`) so Discord `@Luna` references from allowed users can activate the bot. A top-level `workflows:` section defines the workflow DSL (id to steps such as `ask_input`, `prompt_for_field`, `capture_field`, `call_action`, `branch`, and `done`). For `call_action` steps, optional `model` can be configured per step as either a string model id or map `{ provider, modelId }`; only OpenAI provider is accepted for per-step model overrides. `Bootstrap` uses `WorkflowRunnerFactory` to create the runner per bot. Assets: `ConfigLoader`, `BotConfig`.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-CONFIG-LOADER | Load bot definitions, workflow refs, and routing filters from YAML | src/main/java/com/vinekeepers/config/ConfigLoader.java |
| ASSET-BOT-CONFIG | Bot configuration model | src/main/java/com/vinekeepers/config/BotConfig.java |
| ASSET-BOTS-YAML | Bot definitions YAML, including Luna mention activation and configured workflows | config/bots.yaml |

# Sub-pages

- [How it works](config/how-it-works.md)
- [Change log](config/change-log.md)
- [Known issues](config/known-issues.md)
- [Decisions](config/decisions.md)
- [Contracts](config/contracts.md)
- [Tests](config/tests.md)
- [Diagrams](config/diagrams.md)

