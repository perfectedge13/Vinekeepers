# How it works

# Overview

The application calls EnvLoader.load(".env") before bootstrap. The loader reads the file and sets system properties. Env.get(key, default) reads from system properties or returns the default. Optional OpenAI planning keys (API URL, model, timeouts), observability keys for Discord progress lines and server-side body logging, optional **planning RAG** keys (Qdrant URL, collection, embedding model/dimensions, batch and retrieval limits), and the optional health endpoint port all flow through the same environment access path.

# Flow

1. VinekeepersApp starts; calls EnvLoader.load(".env").
2. EnvLoader parses .env and sets each key=value into system properties.
3. Code uses Env.get(key, default) for configuration access.
4. Bootstrap.withHealthServer reads `HEALTH_PORT` through Env, defaults to `8080`, and skips startup when the parsed port is `<= 0`.
5. When enabled, HealthServer binds `GET /` and `GET /health` and returns `OK` for uptime probes.
6. Connectors and workflow actions (for example **`OpenAiChatClient`**, **`OpenAiPlanningProgressPoster`**, **`PlanningRagConfig`**) read planning, observability, and optional RAG keys via **`Env`** when those features are enabled.

# Inputs and outputs

- **Inputs:** .env file at project root (optional), including `HEALTH_PORT` when the health endpoint should listen.
- **Outputs:** System properties populated; Env.get returns values or defaults; optional health probe responses on `/` and `/health`.

