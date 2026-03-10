# Change context (for plan_change / implement)

## Scope

**Request:** Cursor Cloud adapter bug-fix: robust non-2xx error parsing (multiple response shapes), auth header verification + test, safe request diagnostics (debug: URI, key configured, model, repo, branch), payload null-serialization guard, add/update tests. Scope: CursorCloudAdapterImpl, CursorCloudAdapterImplTest.

**Impacted registry slice:** core-registry.yml (FEAT-CURSOR-GATHERING, REQ-LUNA-001, ASSET-CURSOR-ADAPTER-IMPL).

**Primary assets:**  
- `src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapterImpl.java`  
- `src/test/java/com/vinekeepers/core/cursor/CursorCloudAdapterImplTest.java`

**Referenced in request:** CursorCloudAdapterImpl, CursorCloudAdapterImplTest; REQ-LUNA-001; ASSET-CURSOR-ADAPTER-IMPL; FEAT-CURSOR-GATHERING.

---

## Per feature: FEAT-CURSOR-GATHERING

**Feature:** Cursor-backed gathering workflow. Status: active. Doc path: `mkdoc/features/domain/workflow/cursor-gathering.md`. Summary: Luna gathers repository and feature input over Discord, launches an official Cursor cloud agent run, and reports progress and PR results back to chat.

**Requirements in scope**

- **REQ-LUNA-001** — Luna bot — Discord mention trigger, multi-turn gather, Cursor Cloud API  
  - Statement: Luna bot is registered with id luna; Discord activation uses discordMention: luna and optional discordAuthors; workflowRef luna_cursor; luna_cursor uses guided repo selection, codeChange prompt/capture, confirmation step, then cursor.fullRun; Vinekeepers stores in-memory run state and luna:lastRepo per author, polls Cursor, reports launch/progress/completion to Discord.  
  - Acceptance (relevant): Cursor adapter logs transport exceptions, non-2xx responses (status and safe error message), and startup config (baseUrl, key presence); never logs API key, request body, or full response body.  
  - Traceability (adapter): ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE, ASSET-CURSOR-CLOUD-EXCEPTION.  
  - Validation test: UNIT-CURSOR-ADAPTER — CursorCloudAdapterImplTest — Verify Cursor Cloud API adapter implementation.  
  - **Anti-patterns:** Do not log Cursor API key, request body, or full response body because it leaks secrets.

**Assets in scope**

- **ASSET-CURSOR-ADAPTER-IMPL** — path: `src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapterImpl.java` — role: Cursor Cloud Agents API HTTP client implementation (env-based config); logs transport exceptions, non-2xx responses, and startup config without leaking key or body. requires: [REQ-LUNA-001].

**Schema constraints (implement)**

- Requirement keys: id, title, statement, status, priority, type, behavior, acceptance, traceability, validation, anti_patterns.  
- Asset keys: id, kind, path, role, requires, feature_ids.  
- Do not add new spec keys; stay within req-registry schema.

---

## Doc excerpts

**Decisions** (cursor-gathering/decisions.md): Luna remains config-driven in YAML; Discord mention activation belongs to routing; repository operations are delegated through cursor.fullRun and the Cursor adapter instead of being embedded in workflow steps.

**Contracts** (cursor-gathering/contracts.md): Cursor Cloud Agents API — POST /v0/agents, GET /v0/agents/{id}, GET /v0/agents/{id}/conversation, POST /v0/agents/{id}/followup. CursorCloudAdapter: abstraction over launch, status, conversation, follow-up. CursorCloudTransport used by adapter.

**Known issues** (cursor-gathering/known-issues.md): Flow depends on external Cursor Cloud and repository integrations; run tracking is in-memory for v1, not recovered after restart.

---

## Implementation checklist (from request)

1. **Robust non-2xx error parsing** — Handle multiple response shapes (error as string, nested error.message/code, top-level message, plain text body, empty body). Adapter already has `extractErrorMessageAndCode`; verify coverage and add tests for any missing shapes.  
2. **Auth header verification + test** — Ensure Authorization: Bearer is sent; add or extend test that verifies header presence/value (without logging raw key).  
3. **Safe request diagnostics (debug)** — When debug enabled: log URI, key configured (masked), model, repo, branch only; never log key, body, or full response.  
4. **Payload null-serialization guard** — Guard against null payload or null fields when serializing request body (e.g. objectMapper.writeValueAsString); avoid NPE and ensure NON_NULL inclusion is applied.  
5. **Add/update tests** — CursorCloudAdapterImplTest: error parsing variants, auth header verification, diagnostics not leaking secrets, null payload guard behavior.
