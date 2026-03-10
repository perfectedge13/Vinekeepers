# Change context (for plan_change / implement)

## Scope

**Request-derived:** Cursor API error visibility. Primary code: `CursorCloudAdapterImpl.java`, `CursorCloudAdapterImplTest.java`. Requirements: REQ-LUNA-001. Tools/connectors: ASSET-CURSOR-ADAPTER-IMPL, ASSET-CURSOR-FULL-RUN-TOOL; core-registry.yml and connectors-registry.yml (connectors do not define Cursor adapter assets; core-registry owns Cursor adapter and tools).

**Impacted registry slice:** core-registry.yml — REQ-LUNA-001, FEAT-CURSOR-GATHERING, FEAT-TOOLS; assets ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-CURSOR-CLOUD-TRANSPORT.

---

## Per feature

### FEAT-CURSOR-GATHERING (cursor-gathering)

- **Feature:** id FEAT-CURSOR-GATHERING, slug cursor-gathering, title Cursor-backed gathering workflow, status active, doc_path features/domain/workflow/cursor-gathering.md, summary: Luna gathers repository and feature input over Discord, launches official Cursor cloud agent run, reports progress and PR results back to chat.
- **Requirements:** REQ-LUNA-001 — Luna bot — Discord mention trigger, multi-turn gather, Cursor Cloud API. Statement (one line): Luna bot is registered with id luna; Discord activation uses discordMention: luna and optional discordAuthors; workflowRef luna_cursor; luna_cursor uses guided repo selection, codeChange prompt/capture, confirmation step, then cursor.fullRun; Vinekeepers stores in-memory run state and luna:lastRepo per author, polls Cursor, reports launch/progress/completion to Discord. Acceptance criteria (short): Bot id luna and routing discordMention luna; optional discordAuthors; Luna activates when messages reference @Luna; bots.yaml workflowRef luna_cursor; workflow prompts/captures project and codeChange; cursor.fullRun launches Cursor Cloud Agent API, stores LunaCloudRunState, acknowledges to Discord; CursorCloudRunMonitor polls and relays updates; engine sends workflow reply to Discord; confirmation branch clear (edit_request/edit_repo). Validation tests: UNIT-CURSOR-ADAPTER (CursorCloudAdapterImplTest — Verify Cursor Cloud API adapter implementation), UNIT-CURSOR-FULL-RUN-TOOL (CursorFullRunToolTest), UNIT-CURSOR-RUN-MONITOR, others. Traceability assets: ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-FULL-RUN-TOOL, etc. **Anti_patterns:** Hardcoding Discord channel in workflow; storing secrets in state; do not rely on discordTrigger alone for Luna activation.
- **Assets (relevant):** ASSET-CURSOR-ADAPTER (path: src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapter.java, role: Cursor Cloud Agents API adapter interface); ASSET-CURSOR-ADAPTER-IMPL (path: src/main/java/com/vinekeepers/core/cursor/CursorCloudAdapterImpl.java, role: Cursor Cloud Agents API HTTP client implementation, env-based config); ASSET-CURSOR-CLOUD-EXCEPTION (path: src/main/java/com/vinekeepers/core/cursor/CursorCloudException.java, role: Exception type for transport and adapter failures); ASSET-CURSOR-CLOUD-TRANSPORT (path: src/main/java/com/vinekeepers/core/cursor/CursorCloudTransport.java); ASSET-CURSOR-FULL-RUN-TOOL (path: src/main/java/com/vinekeepers/tools/CursorFullRunTool.java, role: Tool wrapper that launches Cursor cloud run and stores launch state and last repo per author).

### FEAT-TOOLS (tools)

- **Feature:** id FEAT-TOOLS, slug tools, title Tool registry and execution, status active, doc_path features/domain/tools/tools.md, summary: Tools registered by name; ToolRunner executes approved tool calls.
- **Relevant asset:** ASSET-CURSOR-FULL-RUN-TOOL uses CursorCloudAdapter; errors from adapter propagate to tool callers (audit/engine). No additional anti_patterns on tools for this change.

---

## Doc excerpts

**features/domain/workflow/cursor-gathering.md:** Summary and key assets table present; ASSET-CURSOR-ADAPTER-IMPL documented as "Cursor Cloud Agents API HTTP client implementation".

**cursor-gathering/decisions:** Luna remains config-driven; Discord mention activation in routing; repository operations delegated through cursor.fullRun and Cursor adapter.

**cursor-gathering/contracts:** CursorCloudAdapter abstraction over launch, status, conversation, follow-up. Official API: POST /v0/agents, GET /v0/agents/{id}, etc. No key/body logging in contracts.

**cursor-gathering/known-issues:** Depends on external Cursor Cloud; run tracking in-memory for v1. Not present: explicit "error visibility" or "no key leak" — implement should add clarity in code and tests only; no doc change required unless behavior is extended.

---

## Implementation intent (from user request)

- **CursorCloudAdapterImpl.java:** (1) Transport catch: improve logging and ensure exception message includes cause (e.g. IOException message) so callers see actionable error. (2) send(): add logging for non-2xx responses (e.g. status and safe error message; no response body leak of secrets). (3) Constructor: add startup log with maskKey (e.g. log that adapter is configured with baseUrl and key presence, never log raw key or body). (4) No key/body leak anywhere: do not log apiKey, request body, or response body in full.
- **CursorCloudAdapterImplTest.java:** Add or extend test so that when transport throws IOException (or similar), the thrown CursorCloudException message includes the cause message (transport IOException surfaces in exception message).
- **Guardrails:** Do not delete requirements; do not remove required functionality; avoid anti_patterns (no secrets in state, no hardcoded channel); stay within existing schema; repair drift before coding.

---

## Schema constraints (optional)

- core-registry.yml: requirement keys (id, title, statement, status, acceptance, traceability, validation, anti_patterns); asset keys (id, kind, path, role, requires, feature_ids). Do not invent new spec keys.
