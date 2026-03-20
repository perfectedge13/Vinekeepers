# Intelligent thread-native planning workflow — implementation plan

Planning/design only. No code in this document. Grounded in Vinekeepers as of repo inspection (config, `src/main/java`, specs, work profiles).

---

## 1. Executive Summary

The repo already has strong **workflow rails** (`luna_cursor` in `config/bots.yaml`), **canonical planning state** (`FeaturePlanState`, artifacts from `config/work-profiles.yaml`), **structured discovery** (`StructuredDiscoverySupport`, `GetStructuredDiscoveryGapsAction`, `BuildDiscoveryAgendaAction`, `CaptureAndApplyDiscoveryAnswerAction`), and **Phase C** readiness/critique (`RunPlanCritiqueAndReadinessAction`, `PlanReadinessEvaluator`, `PlanCritiqueSupport`, approval + `persist_plan_approval`).

Gaps are primarily **UX and intelligence layer**:

1. **Session / surface mismatch** — Luna uses default Discord session strategy **`channel_user`** (`SessionKeyStrategies`). Thread messages use the **thread’s channel id** as `channelId` in the payload (`JdaDiscordGateway`), so workflow state keyed to the **parent room** does not match replies in the **intake thread** (`deliveryChannelId`). That explains prompts appearing “in the wrong place” or the thread feeling hung while Luna waits on a different session key.
2. **Internal tokens in user copy** — `StructuredDiscoverySupport.toQuestion()` builds `discoveryCurrentQuestionPrompt` with explicit `artifactId.sectionId.fieldId` (e.g. `Discovery — please provide **validation_plan.checks.validation_notes**`). `GetProfileMissingFieldsAction` builds the same style strings for missing paths. Profile YAML already has **`label`** and **`promptHint`** per field but discovery does not use them for prompts.
3. **No LLM in the loop** — Every bot gets `StubReasoner` in `Bootstrap` (`registerReasoner(..., new StubReasoner())`). `ReasonerInput` already carries event, bot id, last workflow reply text, workflow state map, and last user message — a viable hook for a real reasoner without new engine concepts.
4. **Form-shaped discovery** — Gaps are “missing required field” → one question per gap → user types value. There is no **draft-then-confirm**, **confidence tier**, or **single high-value question** pass.

The implementation should be **incremental**: fix **session + delivery policy** first, then **prompt mapping** (profile-driven), then **drafting actions** (deterministic + optional LLM), then **reasoner provider** behind the existing `Reasoner` interface, then **workflow YAML** adjustments (thread-native prompts, optional role turns).

---

## 2. Current State in the Repo

### Planning state and profiles

- **`FeaturePlanState`** (`src/main/java/com/vinekeepers/state/planning/FeaturePlanState.java`) — `contextId`, room/thread ids, `artifacts` (`ArtifactState` tree), legacy lists (requirements, assumptions, issues, validation notes), `PlanConfidence`, `PlanApproval`, `PlanCritiqueSnapshot`, workspace linkage, `profileId`.
- **`FeatureRoomState` / participants** — Multi-bot room; `OutboundDeliveryRouter` + `post_channel_message` with `asRole` / `asBotId`, `target: thread`.
- **`config/work-profiles.yaml`** — e.g. `software_feature_planning`: artifacts `requirements_spec`, `overall_plan.outline.plan_body`, `validation_plan.checks.validation_notes`, `project_context`, etc., each field with **`label`** and **`promptHint`** (not consumed by discovery prompts today).

### Workflow and actions

- **`luna_cursor`** (`config/bots.yaml`) — Phase A–ish: repo choice, request, confirm → provisioning (`create_channel`, `create_thread` → `deliveryChannelId`), `initialize_feature_plan_state`, `ensure_repo_workspace`, `upsert_artifact_section_data` (narrative from `codeChange`), `get_profile_missing_fields`, `get_structured_discovery_gaps` → branch → discovery loop (`build_discovery_agenda`, `prompt_for_field`, `capture_field`, `capture_and_apply_discovery_answer`, `classify_assumption_or_issue`, `recompute_plan_progress`) → Phase C `run_plan_critique_and_readiness` → `post_channel_message` (thread) → approval `prompt_for_field` / `capture_field` / `persist_plan_approval` → thread kickoff posts for roles → `launch_cursor_run` (later steps).
- **Bootstrap** registers all planning/discovery/critique actions (`registerLifecycleActions`); **`post_channel_message`**, **`launch_cursor_run`**, etc.

### Reasoner

- **`Reasoner`** / **`ReasonerInput`** / **`ReasonerOutput`** — `StubReasoner` returns empty; engine merges reasoner reply with workflow reply and can patch state (`VinekeepersEngine.runReasoner`).

### Discovery / critique (deterministic today)

- **`StructuredDiscoverySupport`** — Gaps from required empty fields + workspace failures; **`toQuestion()`** embeds internal paths in prompt text.
- **`PlanCritiqueSupport`** + **`PlanReadinessEvaluator`** — Rule-based critique/readiness, not LLM.

### Discord / session

- **`JdaDiscordGateway`** — Message events: `channelId` = `event.getChannel().getId()`; if from thread, that id is the **thread** id.
- **`SessionKeyStrategies.channelAndUser`** — `bot:<id>:conv:<channelId>:<actorId>`. Thread vs parent room ⇒ **different keys** for the same Luna run.
- **`PromptForFieldStep`** — Sends prompt via `WorkflowRunResult` / outbound; delivery uses **`ReplyTargetResolver`** (reply goes to **event’s channel**, not `deliveryChannelId` unless the connector steers it).

### Specs / docs

- **`specs/workflow-registry.yml`**, **`specs/state-registry.yml`** — Rich asset/requirement coverage for lifecycle, discovery, plan critique, `FeaturePlanState`, etc.
- **mkdoc** — Cursor-gathering / state / workflow pages describe provisioning, thread delivery, Phase B/C at a high level.

---

## 3. Desired User Experience and Operating Model

From the user’s perspective:

1. Talk to **Luna** in **main** (or intake) to choose repo and describe the change; confirm **Launch**.
2. See the **feature room** and **intake/spec thread** created; a short orchestrator summary appears **in the thread**.
3. **All planning** (gaps, drafts, clarifications, critique summary, **approval**) happens **in that thread** — messages feel like a **collaborator**: “Here’s what I inferred and drafted; please confirm or change X.”
4. **Rare** generic “fill field X” — instead **one clear question** or **approve this draft** with plain-English **why**.
5. **Main room** after handoff: optional status pings only, not interactive planning prompts.
6. **Launch** still blocked until **readiness + explicit approval** — safety unchanged.

---

## 4. Thread-Native Planning Design

### Handoff completion (state terms)

- Represent explicitly in workflow state (e.g. `planningPhase: PRE_HANDOFF | POST_HANDOFF` or `planningSurface: main | intake_thread`) set **once** after successful `create_lifecycle_context` + `create_thread` + `initialize_feature_plan_state` (or a small dedicated action).
- Store **`intakeThreadId` / `deliveryChannelId`** in workflow state (mirror `LifecycleContext` / `FeaturePlanState.intakeThreadId`) for routing decisions.

### Session key policy (critical)

- **Option A (recommended):** After handoff, bind Luna’s workflow session to a **stable key** that does not switch when the user moves from parent channel to thread — e.g. `bot:luna:plan:<contextId>` or `bot:luna:conv:<deliveryChannelId>:<userId>`, set when `contextId` + `deliveryChannelId` are known. All `prompt_for_field` / `capture_field` for that run use this key.
- **Option B:** Switch Luna to **`sessionKeyStrategy: thread`** only after handoff (complex — requires re-keying or migrating state once).
- **Option C:** Transfer planning steps to a **thread-scoped bot** (e.g. Arrietty) for post-handoff only — larger change to workflow ownership.

### Capturing thread replies

- With **Option A**, user replies in thread; if payload `channelId` is thread id, **session key must still resolve** to the same `ConfigurableWorkflowState` bucket (hence key by `contextId` or `deliveryChannelId+user`, not raw message `channelId` alone after handoff).

### Wrong-surface replies

- If `POST_HANDOFF` and event `channelId` ≠ `deliveryChannelId` (parent room): **post_channel_message** (orchestrator) with short redirect: “Planning continues in &lt;thread&gt; — reply there.” Do not advance workflow from parent-channel text (or only allow explicit commands).

### Waiting-for-input

- Keep `ConfigurableWorkflowState.Status.WAITING_INPUT` + `addBotsWithWaitingSessionForDiscordMessage`; ensure **waiting sessions** are discoverable by the **same session key** the user will hit when typing in the thread.

---

## 5. Plain-English Prompt and Explanation Design

### Mapping internal targets → user copy

- **Primary source:** `WorkProfileDefinition` — use **`FieldDefinition.getLabel()`** and **`getPromptHint()`** (already in YAML).
- **Secondary:** Optional `userFacingPrompt` / `whyWeAsk` on field or section (schema extension in profile loader + YAML) for longer copy.
- **Discovery:** Change `StructuredDiscoverySupport.toQuestion()` for `REQUIRED_FIELD` to build prompts like:  
  `Please confirm or edit **{label}** ({short why from promptHint}).`  
  Never emit raw `artifact.section.field` in default mode.
- **`GetProfileMissingFieldsAction`:** Return **human summaries** for UI/thread posts, or keep machine list internal and expose only via actions that map labels.

### Debug mode

- Env or bind flag `planningPromptDebug=true` — include internal path suffix for support.

### Shape of a user-facing prompt

- **Title / question** (plain English)  
- **Why it matters** (1–2 sentences)  
- **Draft** (if any) in a quote/block  
- **How to respond** (“Reply OK to accept”, “Reply with edits”, or choice buttons if using `present_choices`)

---

## 6. Auto-Drafting and Inference Design

### Draftable fields (examples)

- **`requirements_spec.narrative.feature_summary`** — Already seeded from `codeChange`; extend with repo-aware summary once workspace path exists.
- **`overall_plan.outline.plan_body`** — Draft from `initialRequest` + file tree / key paths (deterministic template first; LLM refine optional).
- **`validation_plan.checks.validation_notes`** — Draft from assumptions + outline (template: tests, manual checks).
- **`project_context.context.summary`** — From README snippet + repo root listing (deterministic).

### Signals

- `FeaturePlanState.getRepoLocalPath()` / workspace status, `initialRequest`, existing artifacts, `GatheringState`/`codeChange` in workflow state, optional git grep / read limited files (new **read-only** tool or action).

### Infer vs draft vs ask

- **Infer (auto-apply):** Low-risk defaults (e.g. echo `feature_summary` if already set from launch).
- **Draft + confirm:** High-value text fields (`plan_body`, `validation_notes`) — write to artifacts with marker `draft: true` or separate state keys until user confirms (then `upsert` + clear draft).
- **Ask:** Ambiguity, permission, tradeoffs, approval — use **single** prioritized question.

### Confidence

- Reuse/extend **`PlanConfidence`** / gap **severity**; add per-field **`DraftConfidence`** enum or numeric tier in workflow state (not necessarily persisted on `FeaturePlanState` v1 — can live in workflow state until promoted).

---

## 7. LLM Reasoning Architecture

### Provider abstraction

- New **`LlmClient`** or **`PlanningSynthesisService`** interface (prompt in, structured JSON or text out) with env-based API key; implementation **OpenAI-compatible** or similar.
- **Do not** bypass actions: LLM outputs feed **`UpsertArtifactSectionDataAction`**-like persistence or dedicated **`apply_llm_draft`** action that validates shape.

### Reasoner path

- Replace `StubReasoner` for **specific bots** (start with **Arrietty** or **Luna** only) with **`DelegatingReasoner`** that:
  - Calls LLM only when workflow state says `reasoningTrigger` (e.g. after handoff, or when `WAITING_INPUT` with `pendingReasonerTurn`).
  - Returns **`ReasonerOutput`** with `replyText` (plain-English assistant turn) **or** empty if workflow reply suffices.

### Role differentiation

- **Arrietty:** Orchestration copy, synthesis summaries, “one question” framing.  
- **Architect / Auditor / Scribe:** Optional phased `post_channel_message` + reasoner with **role-specific system prompts** from `config/bots.yaml` `persona.systemPrompt` + injected artifact excerpts.

### Workflow vs LLM boundary

- **Workflow:** step index, branches, `FeaturePlanState` persistence, approval enums, `launch_cursor_run` preconditions.  
- **LLM:** drafting text, ranking gaps, rewriting prompts, critique *suggestions* (human-readable); **PlanCritiqueSupport** can stay canonical for MUST_FIX rules, with LLM adding **suggested** findings into a staging area reviewed by rules.

### First LLM-assisted steps (suggested order)

1. **Rewrite discovery prompt** from gap + profile metadata (server-side, no user-visible raw path).  
2. **Draft** `plan_body` + `validation_notes` after repo index.  
3. **Optional:** LLM-assisted **question ordering** (input: gaps JSON, output: ordered list of question ids).

---

## 8. Discovery and Question Quality Design

### Today

- One gap → one `DiscoveryQuestion`; prompt includes internal ids (`StructuredDiscoverySupport.toQuestion`).

### Target

- **Batch gaps** by artifact/section; **one message** with numbered drafts/checklist.  
- **Priority:** BLOCKER → required-for-approval → optional; cap **N** questions per turn (e.g. 3).  
- **Draft + confirm:** For text fields, pre-fill via `upsert_artifact_section_data` then ask “Accept section **Overall plan**?” with `present_choices`.  
- **Assumptions/issues:** `classify_assumption_or_issue` already runs; use classified items to **suppress** redundant questions.

### Question worth asking

- Score: blocker, `requiredForApproval`, no draft confidence, user preference. Drop questions answerable from **`initialRequest`** + file path heuristics.

---

## 9. Workflow Changes

### `config/bots.yaml` — `luna_cursor`

- After provisioning + `contextId`/`deliveryChannelId` in state: set **planning surface** flag; optionally **`post_channel_message`** “Planning continues here” before any discovery prompt.
- Replace raw `prompt_for_field` for discovery with either:
  - **`call_action`** `post_thread_planning_prompt` (new) that posts to `deliveryChannelId` + sets `WAITING_INPUT` with **explicit** capture binding, or
  - Keep `prompt_for_field` but **fix session key** so capture happens in thread (preferred: session fix).
- Consider **`prompt_for_field`** only for **main-chat** phases (steps 0–9); after step ~12, **no** main-only prompts.

### New / extended steps

- **`draft_planning_artifacts`** (action) — deterministic + optional LLM drafts into artifacts or draft keys.  
- **`prepare_discovery_turn`** (action) — builds **one** `OutboundResponse` text from gaps + profile labels (replaces per-gap `toQuestion` string for UX).  
- **`maybe_skip_discovery`** — If drafts confirmed and gaps empty, branch to critique.

### Phase C

- Unchanged **gates**; enrich **thread** copy via reasoner or template.

---

## 10. State and Persistence Strategy

- **Canonical:** `FeaturePlanState` + `FeaturePlanStateStore` remain SoT.  
- **Ephemeral:** `ConfigurableWorkflowState` holds `deliveryChannelId`, `contextId`, `planningHandoffComplete`, draft flags, `pendingQuestionId`.  
- **Thread messages:** Never authoritative; only **actions** write artifacts.  
- **LifecycleContext** already has delivery target — ensure workflow session resolution **aligns** with `lifecycleContextStore.getByDeliveryTargetId` for redirects.

---

## 11. New Actions / Services / Helpers

| Item | Role |
|------|------|
| **`PlanningSessionKeyStrategy`** or extend `SessionKeyStrategies` | Stable post-handoff key for Luna (or global helper used by `ConfigurableWorkflowRunner`). |
| **`ResolvePlanningReplyEvent`** (helper) | Map Discord event to `contextId` / workflow state when channel is thread or parent. |
| **`BuildUserFacingDiscoveryPromptAction`** | Uses profile labels/hints + gaps; no raw paths in default mode. |
| **`DraftPlanningArtifactsAction`** | Deterministic + optional `LlmClient` sections. |
| **`PostThreadPlanningPromptAction`** | Sends to `deliveryChannelId`, coordinates with WAITING_INPUT. |
| **`LlmReasoner`** | Implements `Reasoner`, gated by config/env. |
| **Profile schema** (optional fields) | `whyWeAsk`, `draftStrategy`, `userVisibleGroup` on sections. |

---

## 12. Testing Strategy

- **Unit:** `StructuredDiscoverySupport` prompt building (with mock profile) — assert no raw `artifact.section.field` in default mode; assert use of `label`/`promptHint`.  
- **Session key:** Tests for same logical run: message in thread and parent — **same** workflow state bucket after handoff.  
- **Integration:** `ConfigurableWorkflowRunner` + in-memory `StateStore` — simulate handoff then thread message advances discovery step.  
- **Regression:** Phase C readiness BLOCKED/NEEDS_REVISION/READY + `launch_cursor_run` approval gate unchanged (`LaunchCursorRunActionTest`, `PhaseCPlanActionsTest`).  
- **Validation:** `npm run validate-specs`, `validate-drift`, `validate-docs`, `mvn test`, `mvn compile`.

---

## 13. Specs and Docs Updates

- **`specs/workflow-registry.yml`** — New/updated assets for drafting, prompt builder, session policy; acceptance criteria for thread-native planning.  
- **`specs/state-registry.yml`** — If new persisted fields on `FeaturePlanState` (e.g. draft metadata).  
- **`mkdoc/features/domain/workflow/cursor-gathering/*.md`** — UX model: main vs thread, handoff, no internal paths.  
- **`README.md`** — Optional env vars for LLM, debug prompts.

---

## 14. Risks, Tradeoffs, and Deferred Items

- **Risk:** LLM hallucination polluting artifacts → mitigate with **draft** + confirm + rule-based critique retained.  
- **Risk:** Session migration bugs → highest priority fix; feature-flag stable key.  
- **Cost/latency:** LLM calls per turn — batch and cache.  
- **Deferred:** Full handoff of `luna_cursor` to Arrietty bot id; multi-LLM providers; autonomous multi-turn agent loops outside workflow.

---

## 15. Recommended Implementation Sequence

1. **Session + surface** — Stable planning session key + wrong-channel redirect (no LLM).  
2. **Prompt hygiene** — Profile-driven discovery + missing-field summaries; remove raw paths from `toQuestion` / actions.  
3. **Deterministic drafting** — `DraftPlanningArtifactsAction` templates using repo path + existing state.  
4. **Thread-first UX** — Ensure prompts and captures resolve in intake thread (verify E2E on Discord).  
5. **`LlmClient` + narrow reasoner** — Draft/refine + optional critique suggestions.  
6. **Discovery batching / question cap** — Agenda redesign.  
7. **Specs/docs** sync.

---

## 16. File-by-File Change Plan

**Modify**

- `config/bots.yaml` — Session strategy or documented handoff; workflow step inserts; optional reasoner-related binds.  
- `src/main/java/com/vinekeepers/workflow/SessionKeyStrategies.java` (or new strategy wired from `ConfigurableWorkflowRunner` / config).  
- `src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java` — If session resolution is runner-level.  
- `src/main/java/com/vinekeepers/workflow/discovery/StructuredDiscoverySupport.java` — `toQuestion()`, optional batch APIs.  
- `src/main/java/com/vinekeepers/workflow/actions/GetProfileMissingFieldsAction.java` — Human-readable output path.  
- `src/main/java/com/vinekeepers/core/Bootstrap.java` — Register new actions; optional `LlmReasoner` registration.  
- `src/main/java/com/vinekeepers/core/VinekeepersEngine.java` — Only if reply routing must force thread id from state (prefer minimal change).  
- `src/main/java/com/vinekeepers/profile/*` — Optional new field metadata on `FieldDefinition` / loader.  
- `config/work-profiles.yaml` — Enrich hints / optional `whyWeAsk`.  
- `specs/workflow-registry.yml`, `specs/state-registry.yml`, `mkdoc/...`, `README.md`.

**Create (when implementing)**

- `.../workflow/actions/DraftPlanningArtifactsAction.java`  
- `.../workflow/actions/BuildUserFacingDiscoveryPromptAction.java` (or merge into `BuildDiscoveryAgendaAction`)  
- `.../llm/LlmClient.java` + impl  
- `.../reasoner/LlmReasoner.java` (or `ConfigurableLlmReasoner`)  
- Tests under `src/test/java/...` mirroring above.

---

## 17. Acceptance Criteria

### Thread-native interaction

- [ ] After handoff, **discovery** and **approval** prompts are received and answered **only** in the intake/spec thread in E2E Discord test (or harness simulating thread `channelId`).  
- [ ] Replying in parent room does **not** advance planning; user sees redirect (or documented exception).  
- [ ] `WAITING_INPUT` for Luna/planning resolves when user types in thread.

### Plain-English prompts

- [ ] Default discovery prompts **do not** contain raw `artifactId.sectionId.fieldId` strings from profile ids.  
- [ ] Prompts include **label** (and hint where configured) from `work-profiles.yaml`.

### Auto-drafting

- [ ] At least **`plan_body`** or **`validation_notes`** receives a **draft** before first user question for that field (deterministic or LLM), with **confirm or edit** path documented in workflow.

### LLM reasoning

- [ ] With LLM env configured, **StubReasoner** is not the only implementation for at least one bot on planning paths; with env unset, behavior falls back to current deterministic path **without** failure.

### Question quality

- [ ] Open-gap count per user turn **≤ N** (agreed constant) OR explicit “batch” message; no one-to-one mandatory loop for all low-priority gaps.

### Launch / readiness / approval safety

- [ ] `PlanReadinessStatus.BLOCKED` still prevents silent launch.  
- [ ] `persist_plan_approval` / `LaunchCursorRunAction` approval rules **unchanged** or tightened only with tests.  
- [ ] `mvn test` + spec/doc validators pass.

---

## Uncertainty / follow-up

- Exact Discord payload for “parent channel id” when user is in thread — confirm whether `channelId` is always thread id; session fix design assumes **thread channel id ≠ parent room id** for message events.
