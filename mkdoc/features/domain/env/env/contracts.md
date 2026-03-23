# Contracts

# APIs

None. EnvLoader.load(path); Env.get(key, default).

# Schemas

.env file: key=value lines. Env.get: string key, string default → string.

Planning-related keys consumed via Env (non-exhaustive; see **`.env.example`**): **`OPENAI_API_KEY`**, **`OPENAI_BASE_URL`** or **`OPENAI_API_BASE_URL`**, **`OPENAI_PLANNING_MODEL`**, **`OPENAI_PLANNING_TIMEOUT_MS`** or **`OPENAI_HTTP_REQUEST_TIMEOUT_MS`**, **`OPENAI_HTTP_CONNECT_TIMEOUT_MS`**. Observability: **`OPENAI_PLANNING_DISCORD_PROGRESS`** (default true; false disables **Update:** Discord posts for planning OpenAI calls), **`OPENAI_LOG_PLANNING_BODIES`** (default true; false suppresses truncated system/user/assistant body logs while still logging failure outcomes), **`OPENAI_LOG_BODY_MAX_CHARS`** (default 4096, clamped 256–32768). Optional **planning RAG:** **`VINEKEEPERS_PLANNING_RAG`** (default true; false skips RAG prep), **`QDRANT_URL`** or **`VINEKEEPERS_QDRANT_URL`**, optional **`QDRANT_API_KEY`** / **`VINEKEEPERS_QDRANT_API_KEY`**, **`QDRANT_COLLECTION`** (default **`vinekeepers_planning_grounding`**), **`OPENAI_EMBEDDING_MODEL`** (default **`text-embedding-3-small`**), **`OPENAI_EMBEDDING_DIMENSIONS`** (default **1536**), **`PLANNING_RAG_EMBED_BATCH_SIZE`**, **`PLANNING_RAG_MAX_CHUNKS`**, **`PLANNING_RAG_TOP_K`**. Missing or unusable Qdrant/OpenAI pairing leaves planning non-RAG without failing the workflow.

# Interfaces

EnvLoader: static load(String path). Env: static get(String key, String default).

