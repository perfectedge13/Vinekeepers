# How it works

# Overview

The application calls EnvLoader.load(".env") before bootstrap. The loader reads the file and sets system properties. Env.get(key, default) reads from system properties or returns the default. Optional OpenAI planning keys (API URL, model, timeouts), observability keys for Discord progress lines and server-side body logging, and optional **planning RAG** keys (Qdrant URL, collection, embedding model/dimensions, batch and retrieval limits) are read the same way—see **Contracts** and **README** / **`.env.example`**.

# Flow

1. VinekeepersApp starts; calls EnvLoader.load(".env").
2. EnvLoader parses .env and sets each key=value into system properties.
3. Code uses Env.get(key, default) for configuration access.
4. Connectors and workflow actions (for example **`OpenAiChatClient`**, **`OpenAiPlanningProgressPoster`**, **`PlanningRagConfig`**) read planning, observability, and optional RAG keys via **`Env`** when those features are enabled.

# Inputs and outputs

- **Inputs:** .env file at project root (optional). Keys and defaults for Env.get.
- **Outputs:** System properties populated; Env.get returns values or defaults.

