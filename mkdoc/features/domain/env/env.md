# Environment and .env loading

# Status

active

# Summary

Load .env-backed configuration and optional health checks (REQ-ENV-001). EnvLoader loads a .env file from the project root into system properties; Env.get(key, default) provides configuration access; Bootstrap may start HealthServer from `HEALTH_PORT` so `GET /` and `GET /health` return `200 OK` for uptime probes. Assets: EnvLoader, Env, HealthServer, Bootstrap, App.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ENV-LOADER | Load .env file into system properties | src/main/java/com/vinekeepers/env/EnvLoader.java |
| ASSET-ENV | Read config from system properties | src/main/java/com/vinekeepers/env/Env.java |
| ASSET-HEALTH-SERVER | Minimal HTTP server for GET / and GET /health uptime probes | src/main/java/com/vinekeepers/env/HealthServer.java |
| ASSET-BOOTSTRAP | Bootstraps optional health server from HEALTH_PORT env configuration | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-APP | Application entrypoint; loads .env and bootstraps engine | src/main/java/com/vinekeepers/VinekeepersApp.java |

# Sub-pages

- [How it works](env/how-it-works.md)
- [Change log](env/change-log.md)
- [Known issues](env/known-issues.md)
- [Decisions](env/decisions.md)
- [Contracts](env/contracts.md)
- [Tests](env/tests.md)
- [Diagrams](env/diagrams.md)

