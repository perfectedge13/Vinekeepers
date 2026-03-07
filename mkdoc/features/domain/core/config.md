# Bot config from YAML

# Status

active

# Summary

Load bot config from YAML (REQ-CONFIG-001). ConfigLoader loads bot definitions from YAML; BotConfig models the structure. Each bot may specify `workflow.type` (e.g. `stub`, `configured`) and optional `workflow.params` (for `configured`: `workflowRef` to reference a workflow id, or inline `steps`). Luna uses `type: configured` and `workflowRef: luna_cursor`. A top-level `workflows:` section defines the workflow DSL (id → steps: ask_input, call_action, branch, done). Bootstrap uses WorkflowRunnerFactory to create the runner per bot. Assets: ConfigLoader, BotConfig.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-CONFIG-LOADER | Load bot definitions from YAML | src/main/java/com/vinekeepers/config/ConfigLoader.java |
| ASSET-BOT-CONFIG | Bot configuration model | src/main/java/com/vinekeepers/config/BotConfig.java |
| ASSET-BOTS-YAML | Bot definitions YAML (including workflow type and workflows section) | config/bots.yaml |

# Sub-pages

- [How it works](config/how-it-works.md)
- [Change log](config/change-log.md)
- [Known issues](config/known-issues.md)
- [Decisions](config/decisions.md)
- [Contracts](config/contracts.md)
- [Tests](config/tests.md)
- [Diagrams](config/diagrams.md)
