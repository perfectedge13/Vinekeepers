# Generic workflow/autonomy framework — refined final plan

**Repository:** [perfectedge13/Vinekeepers](https://github.com/perfectedge13/Vinekeepers)  
**Branch target:** `main`  
**Planning only** — do not implement from this document until explicitly requested.

This document **refines** the prior revised final plan direction (richness, UX view-model, convergence, migration). It is **not** a rewrite from scratch: same spine (config-driven phases, capabilities, ledger, rules, progress, work profiles, strangler migration). **Nothing here hardcodes the planning room or specific bots into the engine.**

---

## 1. Root cause analysis (repo-grounded, tightened)

**Richness is still planning-shaped in code.** [PlanningPacketDepthEvaluator.java](src/main/java/com/vinekeepers/workflow/planreview/PlanningPacketDepthEvaluator.java) binds concrete artifact paths and heuristics. [config/work-profiles.yaml](config/work-profiles.yaml) is the right generic schema for artifacts but lacks **declarative** depth/quality rules at scale; **plugin hooks must not become a back door** for re-hardcoding workflow logic (see Refinement 3).

**UX and copy boundaries are inconsistent; clarification UX drifts toward rich controls.** User-facing strings still mix unbounded `{{stateKey}}` interpolation, planning-prefixed branch keys in [config/bots.yaml](config/bots.yaml), and [WorkflowSafeTemplateRenderer](src/main/java/com/vinekeepers/workflow/template/WorkflowSafeTemplateRenderer.java). There is **no explicit framework default** that unresolved-item / clarification prompts are **plain text**, so YAML and policies can **accidentally** reintroduce `present_choices` or `choiceProvider` for clarifications that are not truly bounded decisions (Refinement 1).

**There is no single coordinator UX policy object.** Tuning such as “how many questions per turn,” progress frequency, and assumption aggressiveness is implicit in YAML branches and Java — not a **declared UX policy** workflows can share and test (Refinement 2).

**Convergence is partially generic but not end-to-end.** [WorkflowRulesEngine](src/main/java/com/vinekeepers/workflow/v2/WorkflowRulesEngine.java) and [UnresolvedItemLedger](src/main/java/com/vinekeepers/state/workflow/UnresolvedItemLedger.java) are good primitives; live planning still relies heavily on scratch keys and a mega `execute_planning_room_cycle`. Progress dedupe exists ([ProgressDedupeHelper](src/main/java/com/vinekeepers/state/workflow/ProgressDedupeHelper.java)) but **must align** with UX caps (e.g. max progress posts per cycle) in policy, not ad hoc (Refinement 2 + convergence section).

**Migration center of gravity** remains [PlanningCyclePipeline](src/main/java/com/vinekeepers/workflow/planning/PlanningCyclePipeline.java) behind [ExecutePlanningRoomCycleAction](src/main/java/com/vinekeepers/workflow/actions/ExecutePlanningRoomCycleAction.java); [WorkflowRunnerFactory](src/main/java/com/vinekeepers/workflow/WorkflowRunnerFactory.java) v2 path is adjacent until YAML adopts phase graphs — unchanged diagnosis, **with plugin discipline** so migration does not add one-off Java checkers per workflow (Refinement 3).

---

## 2. Revised target architecture (same direction, tighter contracts)

Keep: phases, capabilities (`legacy_action` bridges), rulesets, ledger, progress policies, participants/passes, templates — all **declared in config**.

**Add / strengthen four subsystems:**

- **Readiness and richness** — Declarative-first rules over profile paths; optional narrow plugins, explicitly classified (Refinement 3).
- **UX view-model + coordinator UX policy** — Bounded user-facing keys **and** a workflow-scoped **UxPolicy** config object for coordinator behavior knobs (Refinement 2).
- **Prompt/control defaults** — Framework-level **default plain-text for ledger-driven clarification**; structured UI only when explicitly declared (Refinement 1).
- **Convergence controller** — Ledger + `domainRevision` + visible-delta + **UxPolicy limits** (progress caps, questions per turn) → rule outcomes → stable outcomes.

---

## 3. Packet richness contract (declarative-first + plugin discipline)

**Principle:** Routine richness and “good enough to post” are expressed **declaratively** on work profiles (or workflow readiness policy), using reusable **generic predicates** (min token counts, regex forbid lists, echo/overlap vs request, required path non-blank, cross-field constraints).

**3.1 Work profile extensions (preferred)**  
Per field/section/artifact: `minWords`, `maxEchoOverlapWithRequest`, `forbidPlaceholderPatterns`, `required`, `blockingForPacketPost`, weights — all **data**, no Java per workflow.

**3.2 Readiness evaluator**  
Consumes profile + materialized documents + optional `requestFingerprint`. Returns structured `readyToPost`, `blockingReasons[]`, path-level findings.

**3.3 Plugin checks (`qualityCheckRef`) — strict discipline (Refinement 3)**

- **Default:** No plugin. Declarative rules cover normal cases.
- **Optional plugins are narrow:** e.g. `generic.placeholder_scan`, `generic.token_count`, not `planning.is_good_packet`.
- **Classification:** Every registered checker declares `scope: generic | domain_specific` (or equivalent). **Domain-specific** plugins must be **explicitly tagged** and **justified** in workflow config (`usesDomainPlugins: [id]`); the engine may warn or enforce allowlists in strict mode.
- **Not primary:** Readiness evaluation **must** apply the full **declarative** rule set first; plugins run as **supplements** or **last-resort** gates, never the main store of routine policy.
- **Migration:** Do not add new domain-specific plugins to replicate what declarative rules can express; when strangling [PlanningPacketDepthEvaluator](src/main/java/com/vinekeepers/workflow/planreview/PlanningPacketDepthEvaluator.java), **transpile** its logic into **declarative policy** for the planning profile, keeping Java only as a thin **generic** interpreter.

**3.4 Packet-level bands**  
Config declares bands (e.g. block post / internal draft only / user-visible) via codes, not planning-specific strings.

---

## 4. UX / conversational contract (view-model + coordinator UX policy)

**4.1 Layers (unchanged intent, tighter)**  
Internal state vs **user view-model** (allowlisted keys) vs **templates** only on view-model (+ explicit expose path for debug).

**4.2 Coordinator UX policy — config model (Refinement 2)**  
Workflow YAML (or included fragment) declares **`uxPolicy`** (name illustrative), **generic** keys, for example:

- `maxClarifyingQuestionsPerTurn` — integer; enforces cap before prompting.
- `clarificationBundleStrategy` — `single_top` | `small_bundle` (with `maxBundleSize`); ranks items via ledger policy.
- `assumptionAggressiveness` — enum e.g. `conservative | balanced | aggressive`; drives when merge/capability may set `resolved_by_assumption` vs keep `OPEN`.
- `assumeVsAskThreshold` — optional scoring hook: above threshold → assume with logged rationale; below → ask (ties to ledger + rules, not planning names).
- `maxProgressMessagesPerCycle` — caps user-visible progress posts per orchestration cycle; works with **G2** (no-delta) in convergence.
- `revisionSummaryWhen` — `on_domain_revision` | `on_readiness_change` | `always_after_user_answer` | `never` (workflow chooses).
- `explainWhatChangedAfterAnswer` — boolean; when true, capability fills view-model `whatChangedSinceLastTurn` from diff/hash of relevant artifact slices.
- `clarificationIncludeExamples` — boolean; template slot for examples when true.
- `userSummaryTemplateRef` / `tone` — template ids or tone hint for coordinator summaries (still allowlisted keys only).

**Engine behavior:** Runner or `shape_user_copy` capability **reads `uxPolicy`** to decide how many questions to surface, whether to post progress, and which template refs to use. **Convergence rules** may read counters incremented by the engine when progress posts fire, to prevent policy violations becoming loops.

**4.3 Prompt / control model — default plain text for clarifications (Refinement 1)**

- **`UnresolvedItem` model:** Each item includes **`promptControl`** (illustrative name) with:
  - **`mode` default `PLAIN_TEXT`** for ledger-sourced clarification prompts.
  - **`boundedOptions`** optional list (label + value + optional description). **If and only if** this list is **non-empty** and `mode` is `STRUCTURED_CHOICES` (or similar), the framework may render buttons/dropdowns for **that** item.
  - If `boundedOptions` is empty/absent, **`mode` must not** resolve to structured rich controls — engine normalizes to plain text even if YAML elsewhere requests `present_choices`.
- **Workflow steps:** Distinct classes:
  - **Clarification capture** (ledger-bound): **must** respect item default + item override; **forbidden** to inherit a global `intent: present_choices` unless the **active ledger item** declares a bounded set.
  - **Bounded decision / workflow action prompts** (confirm launch, pick repo, etc.): may use `confirm_action`, `present_choices`, `choiceProvider` **when the step or bind explicitly declares** them — these are **not** “unresolved items” unless wired to a ledger item that carries options.
- **Configuration:** `workflows.*.clarificationDefaults: { controlMode: plain_text }` (illustrative); explicit opt-in for structured clarification only via ledger field or step-level override.
- **Accidental drift prevention:** Config loader or runtime guard: if step combines `choiceProvider` with `ledgerActiveItemId` (illustrative) and item has no options → **coerce to plain_text** and log.

**4.4 Other UX blocks**  
Kickoff, progress, revision, approval — same as prior plan, **parameterized by `uxPolicy`** where relevant (e.g. when to show revision summary).

---

## 5. Convergence / anti-loop contract (aligned with UX policy)

Preserve guarantees **G1–G5** (fingerprint re-ask, progress spam, merge explanation, bounded retries, no-op rerun) from the prior plan.

**Tighten:**

- **Progress:** Combine hash dedupe with **`uxPolicy.maxProgressMessagesPerCycle`**; if cap hit, suppress further progress unless `readiness` or `domainRevision` crosses a **major** threshold (configurable flag).
- **Clarifications:** **`uxPolicy.maxClarifyingQuestionsPerTurn`** interacts with ledger ranking: never surface more than N items; remaining stay queued.
- **Stable outcomes:** Unchanged five-outcome set; add explicit **outcome** when **assumptionAggressiveness** resolves an item without user text (`ResolvedByAssumption` as variant under stable set if desired).

---

## 6. Migration strategy (realistic + plugin discipline)

**Unchanged strangler:** readiness evaluator delegation, dual-write ledger + legacy keys, view-model shaper, `arrietty_room_v2`, cutover.

**Add (Refinement 3):**

- Phase ordering: **declarative readiness parity** before adding any **new** `qualityCheckRef` plugins.
- **Forbidden pattern:** “New workflow needs richness → new Java checker.” **Required pattern:** extend YAML profile + generic predicates; only then optional generic plugin.
- **Domain plugins:** Allowed only in `usesDomainPlugins` allowlist on the workflow; document in migration checklist.

**Add (Refinement 1):** When migrating [config/bots.yaml](config/bots.yaml) clarification branches, **default** ledger items to plain text; map existing structured clarification only where ranking policy emits **bounded** options.

**Add (Refinement 2):** Introduce `uxPolicy` in staging YAML first; wire caps before removing old branches.

---

## 7. Concrete implementation plan (phased — brief)

Same phases as prior plan (R1 richness, U1 view-model, C1 convergence, M1/M2 migration, S1 specs/mkdoc), with explicit tasks:

- **R1:** Implement declarative readiness **without** new domain plugins; adapter from [PlanningPacketDepthEvaluator](src/main/java/com/vinekeepers/workflow/planreview/PlanningPacketDepthEvaluator.java) → generated declarative policy.
- **U1:** `uxPolicy` block in workflow loader + enforcement in prompt/progress steps; [WorkflowSafeTemplateRenderer](src/main/java/com/vinekeepers/workflow/template/WorkflowSafeTemplateRenderer.java) integration.
- **C1:** Counters for progress-per-cycle and questions-per-turn; rule predicates.
- **P1 (control defaults):** Extend [UnresolvedItem](src/main/java/com/vinekeepers/state/workflow/UnresolvedItem.java) / ledger + [PromptForFieldStep](src/main/java/com/vinekeepers/workflow/steps/PromptForFieldStep.java) / runner glue for **default plain text** and **coercion** when options absent.
- **M1/M2:** Staging workflow with `uxPolicy` + clarification defaults.
- **S1:** Specs + mkdoc for control defaults, `uxPolicy`, plugin classification.

---

## 8. Validation plan (tightened)

**Richness / readiness**

- Declarative-only fixture passes; plugin supplement runs **after** declarative and cannot veto without declarative pass (or inverse policy explicitly tested).
- Domain plugin without allowlist → loader or runtime error.

**Copy / template safety**

- Unknown template keys rejected; `exposeInternal` test.

**Clarification control (Refinement 1)**

- Ledger item with no `boundedOptions` + YAML `present_choices` → **plain text** outcome in tests.
- Item with `boundedOptions` + structured mode → rich reply allowed.
- Non-clarification step with explicit `confirm_action` → still works.

**Coordinator UX policy (Refinement 2)**

- `maxClarifyingQuestionsPerTurn` = 1 → only one prompt surfaced; ledger retains queue.
- `maxProgressMessagesPerCycle` → count enforced across a synthetic cycle.

**Clarification lifecycle / anti-loop**

- G1–G5 tests unchanged; add progress cap + no-delta interaction tests.

**Stable outcomes**

- After bounded retries, policy triggers fallback/blocked per config.

**Backward compatibility**

- v1 workflow until cutover; regression suite green.

---

## 9. Acceptance criteria (concrete, includes three refinements)

**Architecture (unchanged thrust)**  
Config-driven phases, ledger, rules, migration strangler; no planning/bot names in engine core.

**Refinement 1 — Plain text default**

- Framework spec/doc states: **unresolved-item-driven prompts default to plain text.**
- Structured controls for clarification **only** if the unresolved item declares a **non-empty bounded option set** and explicit structured mode.
- Bounded **workflow** prompts (confirm, static choices, dynamic provider for **non-ledger** steps) remain allowed when the **step** declares them.
- Tests prove coercion when structured UI would drift without bounded options.

**Refinement 2 — UX policy**

- Workflows can declare **`uxPolicy`** (or equivalent) with the knobs listed in §4.2; engine enforces caps deterministically.
- Coordinator summaries and revision behavior follow policy + view-model, not scattered magic keys.

**Refinement 3 — Plugin discipline**

- Routine richness is **declarative** on profiles/readiness policy.
- Optional `qualityCheckRef` plugins are **generic by default**; **domain-specific** plugins require explicit workflow allowlisting and are **not** the primary policy mechanism.
- Acceptance: **no new domain-specific checker** added for standard packet depth that is expressible declaratively.

**Cross-cutting**

- Readiness returns structured codes; approval gated on readiness + ledger + policy.
- `execute_planning_room_cycle` shrinks / v2 graph adoption without breaking v1 until cutover.

---

*Refined final plan — generic workflow/autonomy framework (Vinekeepers). Planning only.*
