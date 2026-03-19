# Bot config from YAML

# Status

active

# Summary

Load bot config from YAML (REQ-CONFIG-001). `ConfigLoader` dual-reads **identities.discord** (tokenEnvKey, handlesOwnedSpaces) and legacy top-level discordTokenEnvKey/handlesOwnedSpaces (**deprecated**, still accepted); normalizes into ConnectorIdentity per connector; new shape preferred, new wins if both present. **defaultDiscordTokenEnvKey** at root is a **retained transitional** setting (when a bot has no identities.discord.tokenEnvKey), not the preferred long-term identity model. Each bot may specify workflow.type/params, routing filter keys (discordTrigger, discordMention, discordAuthors), and runtime options (conversationMode, sessionKeyStrategy). For multi-bot feature room bots (e.g. arrietty, architect, auditor, scribe), set **sessionKeyStrategy: thread** so conversation keys use the thread id and follow-up events in the same thread resolve to the same session. A top-level `workflows:` section defines the workflow DSL. Optional workflow steps **provision_room_participants** (storeIn: featureRoomParticipants) and **initialize_feature_room_state** support multi-bot feature room flows. `Bootstrap` uses `WorkflowRunnerFactory` to create the runner per bot. Assets: ConfigLoader, BotConfig.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-CONFIG-LOADER | Load bot definitions, workflow refs, and routing filters from YAML | src/main/java/com/vinekeepers/config/ConfigLoader.java |
| ASSET-BOT-CONFIG | Bot configuration model | src/main/java/com/vinekeepers/config/BotConfig.java |
| ASSET-BOTS-YAML | Bot definitions YAML, including Luna mention activation and configured workflows | config/bots.yaml |
| ASSET-WORK-PROFILES-YAML | Work profile definitions (artifacts, sections, required fields) | config/work-profiles.yaml |
| ASSET-WORK-PROFILE-LOADER | Load work-profiles.yaml into WorkProfileRegistry | src/main/java/com/vinekeepers/profile/WorkProfileLoader.java |

# Sub-pages

- [How it works](config/how-it-works.md)
- [Change log](config/change-log.md)
- [Known issues](config/known-issues.md)
- [Decisions](config/decisions.md)
- [Contracts](config/contracts.md)
- [Tests](config/tests.md)
- [Diagrams](config/diagrams.md)

