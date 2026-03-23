# Change log

# Entries

## 2026-03-22

- **Optional planning RAG (Qdrant + embeddings):** Documented env toggles and limits (**`VINEKEEPERS_PLANNING_RAG`**, Qdrant URL/API key/collection, **`OPENAI_EMBEDDING_MODEL`**, **`OPENAI_EMBEDDING_DIMENSIONS`**, **`PLANNING_RAG_*`**) read by **`PlanningRagConfig`**, **`QdrantPlanningClient`**, and **`PlanningRepoGroundingService`**. Specs: **`env-registry`**; dossiers: **env**, **workflow-steps**, **architecture**; **README** / **`.env.example`**.
- Documented optional OpenAI planning observability env keys (**`OPENAI_PLANNING_DISCORD_PROGRESS`**, **`OPENAI_LOG_PLANNING_BODIES`**, **`OPENAI_LOG_BODY_MAX_CHARS`**) alongside existing OpenAI planning client keys (**REQ-ENV-001** acceptance). Specs: **`env-registry`**; runtime docs: **README**, **`.env.example`**; workflow dossiers: **workflow-steps**, **cursor-gathering**.

## 2025-03-05

Feature dossier added from nova-spec (per-area features).

