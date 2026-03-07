# Environment and .env loading

# Status

active

# Summary

Load .env at startup (REQ-ENV-001). EnvLoader loads a .env file from the project root into system properties; Env.get(key, default) provides configuration access. Assets: EnvLoader, Env, App.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ENV-LOADER | Load .env file into system properties | src/main/java/com/vinekeepers/env/EnvLoader.java |
| ASSET-ENV | Read config from system properties | src/main/java/com/vinekeepers/env/Env.java |
| ASSET-APP | Application entrypoint; loads .env and bootstraps engine | src/main/java/com/vinekeepers/VinekeepersApp.java |

# Sub-pages

- [How it works](env/how-it-works.md)
- [Change log](env/change-log.md)
- [Known issues](env/known-issues.md)
- [Decisions](env/decisions.md)
- [Contracts](env/contracts.md)
- [Tests](env/tests.md)
- [Diagrams](env/diagrams.md)
