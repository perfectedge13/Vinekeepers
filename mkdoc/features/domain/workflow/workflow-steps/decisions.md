# Decisions

# Entries

## 2026-03-23 — Canonical ask beats stay direct

- **Context:** The live planning flow needed a single canonical question source and should not rely on a late copy-rewrite pass to repair ASK_ONE_QUESTION output after decisioning.
- **Decision:** Prompt directly from **`planningClarificationQuestionText`** in YAML, keep canonical question backfill in **`PlanningCyclePipeline`**, and remove the late **`applyAskOneQuestionUserVisibleCopy`** rewrite path. **`RunRequestExpansionLlmAction`** still injects **`planner_instruction`** when workspace path or non-trivial evidence JSON is present so expansion JSON stays grounded in observed repo state.
- **Consequence:** Clarification beats stay direct, canonical, and single-question without a post-decision copy repair layer; specs and **`PlanningCyclePipelineCanonicalClarificationTest`** / **`RunRequestExpansionLlmActionJsonTest`** lock the behavior.

## 2026-03-22 — Optional Qdrant planning RAG (degrade, do not block)

- **Context:** Coordinator planning benefits from repo-local retrieval during synthesis, but not every deployment runs Qdrant or wants embedding cost on every intake.
- **Decision:** Implement **`prep_planning_repo_grounding`** as an opt-in graph step (ingress **`legacy_action`**) after **`ensure_repo_workspace`**. Use Parquet (**.vinekeepers/repo-grounding.parquet**) as a content-hash cache, Qdrant as the vector store with repo/branch payload filters, and OpenAI embeddings only for missing hashes. Spread **`planningRag*`** state for diagnostics and evidence; **`run_llm_planning_synthesis`** includes **`planningRagRetrievalText`** only when **`planningRagAvailable`**. **`VINEKEEPERS_PLANNING_RAG=false`** or invalid Qdrant/OpenAI config must leave planning non-RAG without failing the workflow.
- **Consequence:** Operators enable RAG via env + YAML; **`ArriettyV2WorkflowYamlTest`**-style guards document where the step is declared in production config.

## 2026-03-22 — Planning OpenAI progress in Discord and safe payload logs

- **Context:** Coordinator planning makes several OpenAI HTTP calls; operators need thread-visible progress without exposing secrets in Discord or logs.
- **Decision:** Pass **`OpenAiCallContext`** (including **`activitySummary`**) into **`OpenAiChatClient`** for expansion, synthesis, and structured role passes; register **`OpenAiPlanningProgressPoster`** as **`OpenAiPlanningProgressSink`** in **`Bootstrap`** so enabled runs post a short **Update:** line before each call. Log durations at INFO always for contextualized calls; log truncated, `sk-*`-redacted message bodies only when **`OPENAI_LOG_PLANNING_BODIES`** is not false, with length capped by **`OPENAI_LOG_BODY_MAX_CHARS`**.
- **Consequence:** Env toggles **`OPENAI_PLANNING_DISCORD_PROGRESS`** and **`OPENAI_LOG_PLANNING_BODIES`** control Discord chatter and verbose server logs independently.

## 2026-03-08 — Intent-based steps and connector sink

- **Context:** Workflows need to present choices, confirmations, or forms on Discord and other connectors without hardcoding platform APIs.
- **Decision:** Introduce platform-neutral `ResponseIntent` (PresentChoices, ConfirmAction, CollectText, CollectForm, ShowActions, ShowStatus) and `OutboundResponse`; workflow steps may return optional `richReply`; engine delivers via `AppReplySink` lifecycle (respondImmediately, sendFollowUp, updateMessage). Connector adapters own timing and defer; engine never requests defer.
- **Consequence:** Step config accepts optional `intent`, `choices`, `confirmLabel`, `cancelLabel`, `fields`; `NormalizedEventContext` carries interaction payload (interactionId, token, customId, values) for capture steps.

## Selected decisions (prior)

- Step execution uses explicit `StepOutcome` values so waiting, completion, and errors are first-class states.
- `CallActionStep` can resolve either a registered workflow action or a `ToolRunner`-backed tool, which keeps the DSL small.
- Runner lifecycle and session persistence are documented separately because the same steps can run under different session-key strategies.

