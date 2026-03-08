# Bot runtime definition and tool policy

# Status

active

# Summary

Tool policy allow/deny lists (REQ-BOT-002) and bot definition/runtime options (REQ-BOT-003). `BotDefinition` composes persona, model profile, workflow settings, memory settings, conversation mode, optional session key strategy, and `ToolPolicy` so the engine can execute each configured bot consistently. Routing filters are documented separately in the Routing feature.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-TOOL-POLICY | Allow/deny policy for bot tools | src/main/java/com/vinekeepers/bot/ToolPolicy.java |
| ASSET-BOT-DEFINITION | Bot composition for persona, model, workflow, tools, and runtime conversation settings | src/main/java/com/vinekeepers/bot/BotDefinition.java |
| ASSET-CONVERSATION-MODE | Runtime mode enum for single-event versus conversational bots | src/main/java/com/vinekeepers/bot/ConversationMode.java |
| ASSET-PERSONA | Bot persona and tone | src/main/java/com/vinekeepers/bot/Persona.java |
| ASSET-MODEL-PROFILE | LLM/model settings per bot | src/main/java/com/vinekeepers/bot/ModelProfile.java |
| ASSET-MEMORY-POLICY | Session and memory policy per bot | src/main/java/com/vinekeepers/bot/MemoryPolicy.java |

# Sub-pages

- [How it works](bot/how-it-works.md)
- [Change log](bot/change-log.md)
- [Known issues](bot/known-issues.md)
- [Decisions](bot/decisions.md)
- [Contracts](bot/contracts.md)
- [Tests](bot/tests.md)
- [Diagrams](bot/diagrams.md)

