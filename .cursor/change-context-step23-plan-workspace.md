# Change context (for plan_change / implement)

## Scope

**User request (Step 2–3):** Implement feature-plan persistence and repo workspace materialization: `FeaturePlanState` + `FeaturePlanStateStore` + workflow actions for init/append; `RepoWorkspaceState` + store + `RepoRefResolver` + `RepoWorkspaceService` + `EnsureRepoWorkspaceAction`; link plan state to workspace state (and to `FeatureRoomState` where appropriate); extend Luna `luna_cursor` in `config/bots.yaml` with steps **after** `initialize_feature_room_state`; wire stores/services/actions in `Bootstrap`; sync **specs** (`state-registry`, `workflow-registry`, `core-registry`, `config-registry`); add **unit tests**; update **mkdoc**, **README**, **`.env.example`**.

**Requirement IDs in scope:** `REQ-STATE-001`, `REQ-WORKFLOW-001`, `REQ-LUNA-001` (primary). Implementation also touches **`REQ-CORE-002`** (Bootstrap wiring) and **`REQ-CONFIG-001`** (`config/bots.yaml` / loader expectations).

**Feature slugs (registry):** `state`, `workflow`, `workflow-steps`, `cursor-gathering`, `core`, `config`.

**Asset IDs to add or extend (implement phase):** New assets for plan store, workspace store, resolver, service, ensure action, init/append plan actions—declare under existing `assets[]` with `requires`, `feature_ids`, and traceability on the requirements above; **do not invent new top-level spec keys.**

---

## Consolidated anti_patterns (implement must avoid)

**From REQ-LUNA-001 (workflow-registry):**

- Hardcoding Discord channel in workflow.
- Storing secrets in state.
- Do not rely on `discordTrigger` alone for Luna activation (@mentions in channels would be missed).
- Do not log Cursor API key, request body, or full response body.
- Do not assume a single Cursor API error response shape; support nested/plain/empty variants.
- Do not serialize null fields in Cursor API request payload (NON_NULL discipline).

**From guardrails / project standards:**

- Do not delete requirements; do not invent **new YAML keys** outside `req-registry` schema.
- Feature slugs in registry must not equal bot ids from `config/bots.yaml`.

**REQ-STATE-001 / REQ-WORKFLOW-001:** No `anti_patterns` blocks in registry today—still avoid: putting API tokens or clone URLs with embedded credentials in persisted state; bypassing `WorkflowCapabilitySupport` / fail-closed patterns for connector operations.

---

## File checklist (expected touch set)

| Area | Paths / artifacts |
|------|-------------------|
| **State models & stores** | `src/main/java/com/vinekeepers/state/planning/` or adjacent package for `FeaturePlanState`, `FeaturePlanStateStore`; workspace types + `RepoWorkspaceStateStore` (package consistent with existing `state/` layout). |
| **Repo resolution & workspace** | `RepoRefResolver`, `RepoWorkspaceService`, `EnsureRepoWorkspaceAction` (likely `workflow/actions/` or `state/` + actions). |
| **Plan actions** | Init + append workflow actions (register names aligned with YAML `call_action` / step DSL). |
| **Linking** | Extend `FeatureRoomState` (or correlation via `contextId` / `featureId`) so plan and workspace tie to the room; avoid orphan stores. |
| **Bootstrap** | `Bootstrap.java`: construct stores, register actions on `WorkflowActionRegistry`, inject into runner/router as existing patterns require. |
| **Luna YAML** | `config/bots.yaml`: `luna_cursor` — add steps **after** `initialize_feature_room_state` (ensure repo clone/path env usage documented). |
| **Specs** | `specs/state-registry.yml`, `specs/workflow-registry.yml`, `specs/core-registry.yml`, `specs/config-registry.yml` — new `assets[]`, updated `requirements[].traceability`, `acceptance.criteria`, `validation.tests` (existing schema keys only). |
| **Tests** | `src/test/java/...` mirroring new types (store, resolver, service, actions, Bootstrap wiring if needed). |
| **Docs** | `mkdoc/features/domain/state/state.md` (+ subpages if contracts change), workflow / cursor-gathering pages; `README.md`; `.env.example` for any new env vars (e.g. workspace root, GitHub token usage already documented—extend only if new vars). |

---

## Schema constraints (no new keys)

Registries use **`schema.id: req-registry`** and must validate against `specs/schema/req-registry.schema.json`.

- **Allowed top-level keys:** `schema`, `project`, `enums`, `dependencies`, `assets`, `requirements`, optional `features`, optional `agent`.
- **Per requirement:** use only defined blocks (`id`, `title`, `statement`, `status`, `priority`, `type`, `behavior`, `acceptance`, `traceability`, `validation`, `anti_patterns`, etc. as in schema)—**no ad-hoc keys**.
- **Per asset:** `id`, `kind`, `path`, `role`, `requires`, optional `feature_ids`—paths must exist after implement or drift gate fails.
- **Features:** optional `doc_path`, `summary`, `requirement_ids`, `asset_ids`, `status`, `domain_slug`—same rule: no extra properties.

---

## Per feature / requirement group

### FEAT-STATE (`state`) — REQ-STATE-001

- **Feature:** id `FEAT-STATE`, slug `state`, title *State store for workflow state*, status **active**, doc_path `features/domain/state/state.md`, summary: StateStore persists per-bot, per-conversation workflow state; feature room types and `FeatureRoomStateStore` support multi-bot routing.
- **Requirement REQ-STATE-001:** title *State store for bot workflow state*; statement: StateStore persists and loads per-bot, per-conversation workflow state (structured state objects).
- **Acceptance criteria (excerpt):** `StateStore.load/save` behavior; `FeatureRoomStateStore.getParticipantBotIds` returns configured bot ids in stable **PlanningRole** order (Orchestrator, Architect, Auditor, Scribe).
- **Tests (existing):** `StateStoreTest`; `FeatureRoomStateStoreTest` (incl. `getParticipantBotIds_returnsStableOrderFromParticipantList`).
- **Implement note:** Extend acceptance/traceability to cover **feature plan** and **repo workspace** persistence without breaking existing criteria; add assets + tests for new stores.

**Doc excerpts (mkdoc):**

- `state.md` Summary describes FeatureRoomState, participants, `InitializeFeatureRoomStateAction` id/slug generation.
- `state/decisions.md`: placeholder (“Add entries as needed.”).
- `state/contracts.md`: StateStore API; FeatureRoomState / FeatureRoomStateStore contracts—**update after** adding plan/workspace contracts.

---

### FEAT-WORKFLOW / FEAT-WORKFLOW-STEPS — REQ-WORKFLOW-001

- **Feature:** `FEAT-WORKFLOW` / `FEAT-WORKFLOW-STEPS`, doc paths `features/domain/workflow/workflow.md`, `workflow-steps.md`; configurable runners, `WorkflowActionRegistry`, `CallActionStep`.
- **Requirement REQ-WORKFLOW-001:** title *Workflow state machine and config-driven runners*; statement: `Workflow` / `WorkflowRunner` / `WorkflowRunnerFactory`; configurable workflows with session keys; step DSL including `call_action`, branching, extract_event, post_channel_message, create_thread, etc.
- **Acceptance criteria (excerpt):** Runner factory types; configurable pause/resume; `CallActionStep` invokes registered action or tool; bind/state merge for actions; post_channel_message and create_thread behaviors with sentinels and lifecycle store updates.
- **Tests (existing samples):** `WorkflowActionRegistryTest`, `CallActionStepTest`, `InitializeFeatureRoomStateActionTest`, `ConfigurableWorkflowRunnerTest`, etc.
- **Implement note:** Register **new actions** in registry + Bootstrap; document action names in acceptance and in `config/bots.yaml` steps.

**Doc excerpts:** `workflow.md` summarizes runners and session lifecycle. Sub-pages standard set.

---

### FEAT-CURSOR-GATHERING — REQ-LUNA-001

- **Feature:** slug `cursor-gathering`, doc_path `features/domain/workflow/cursor-gathering.md`, status **active**; Luna + Cursor Cloud + lifecycle room Phase 1.
- **Requirement REQ-LUNA-001:** title *Luna bot — Discord mention trigger, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1*; statement: config-driven Luna; `luna_cursor`; guided repo selection; `launch_cursor_run` as authoritative launch; `CursorInstructionComposer`; lifecycle context/store; provisioning actions including `provision_room_participants`, `initialize_feature_room_state`, create channel/thread, etc.
- **Acceptance criteria (excerpt):** `discordMention` + optional `discordAuthors`; YAML `workflowRef luna_cursor`; no hardcoded bot ids in Router/engine; confirmation branch clears; adapter logging/parsing rules; post/create targets and bind precedence.
- **anti_patterns:** listed in **Consolidated anti_patterns** above.
- **Tests (existing samples):** `GatheringStateTest`, `InitializeFeatureRoomStateActionTest`, `LaunchCursorRunActionTest`, adapter tests, etc.
- **Implement note:** YAML must place **new steps after `initialize_feature_room_state`** per user request; keep secrets in env, not state.

**Doc excerpts:** `cursor-gathering.md` Summary documents full provisioning sequence and `initialize_feature_room_state`; **update** to mention plan init/append and `ensure_repo_workspace` (or final action names) after implement.

**`cursor-gathering/decisions.md`:** Luna stays YAML-driven; mention routing vs workflow responsibilities.

---

### FEAT-CORE / FEAT-CONFIG (wiring and YAML)

- **REQ-CORE-002 (excerpt):** Bootstrap wires engine, config, connectors, `WorkflowRunnerFactory`, reply sinks, shared tools; action registration remains in Bootstrap.
- **REQ-CONFIG-001 (excerpt):** ConfigLoader loads bots/routing/workflows; step bind keys and interpolation supported; feature-room bots use `sessionKeyStrategy: thread` where applicable.
- **Implement note:** Extend **ASSET-BOOTSTRAP** role in `core-registry.yml` for new stores/services; extend **ASSET-BOTS-YAML** role in `config-registry.yml` for new Luna steps—within existing `role` text updates and traceability only.

---

## Impacted registry files (plan_change impact set)

- `specs/state-registry.yml`
- `specs/workflow-registry.yml`
- `specs/core-registry.yml`
- `specs/config-registry.yml`

(Index `specs/specs.yml` unchanged unless index scope assets need updating—only if new primary assets are added to index scope per project convention.)

---

## removal_or_rename

**false** — additive feature (new types, actions, docs, tests). No requirement deletion or file rename required by this plan.

---

## Preflight (Schema + Drift)

At context generation time: `npm run validate-specs` → OK; `npm run validate-drift` → OK.
