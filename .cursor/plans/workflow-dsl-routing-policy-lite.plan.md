# Workflow DSL upgrade + routing policy cleanup lite — revised plan

**Scope:** Workflow DSL upgrade + routing policy cleanup lite. No broadening. Backward-compatible. No connector/module redesign. No deliveryChannelId/lifecycle-thread redesign.

**Goal:** Make YAML workflows substantially more expressive so fewer bot behavior changes require Java edits; make routing internally cleaner and explicitly policy-shaped without changing behavior.

---

## 1. Current-state assessment

### 1.1 BranchStep

- **BranchStep** supports exactly three forms:
  - `when: { key: "<stateKey>", value: "<expect>" }` — string equality (state value vs string form of expect).
  - `when: "else"` — fallback; always matches.
  - `when: "<stateKey>"` — truthy check (non-null, non-false, non-blank string).
- No other operators. No normalization before compare. Branch order: first match wins; optional `clear`, `next`. YAML is readable; limitation is expressiveness.

### 1.2 CaptureFieldFromEventStep and event data in state

- **CaptureFieldFromEventStep**: one field from event → one `storeIn`. Reads: interaction values/customId, or `contentKey`, or content/text. Only **trimAndLower** (boolean).
- **__event** is not in workflow state. It is built in **CallActionStep.buildArgs()** and passed only to actions. There is no config-driven way to copy event fields (e.g. channelId, messageId) into state for later steps or interpolation.

### 1.3 Transforms

- No shared transform utility. trimAndLower is hard-coded in CaptureFieldFromEventStep.

### 1.4 Router and routing config

- **Router** holds a list of **Routing** (filter + botId). **route(event)** filter-matches each in order, then applies lifecycle-owned-space override for Discord, then returns deduped bot ids. **RoutingFilter**: flat fields (discordAuthors, discordChannels, discordTrigger, discordMention, repos, prLabels, prAuthors). **ConfigLoader** parses `routing[].filter` into RoutingFilter. No named "policy" in code or docs; order and lifecycle override are implicit.

---

## 2. Recommended design decisions

### 2.1 Branch / operator design

**A. Official supported branch operators**

- **equals** (default when `value` present)
- **not_equals**
- **blank** (state value null or blank)
- **nonblank** (state value non-null and non-blank)
- **contains** (state value contains compare value as substring)
- **starts_with**
- **regex** (compare value is pattern; **full-string match** — the pattern must match the entire state value, not a substring)
- **one_of** (state value equals one of the values in the list)

**B. Legacy forms — unchanged**

- `when: { key: ..., value: ... }` → implicit **equals**.
- `when: "else"` → fallback.
- `when: "<stateKey>"` → truthy check (existing behavior).

**C. one_of config shape**

- **Official supported config shape for one_of is a YAML list:** `value: [a, b, c]`.
- Comma-separated strings are **not** documented as a first-class config format. If the implementation tolerates a single string and treats it as a one-element list (or splits for robustness), that is an internal fallback only and must not be advertised as supported YAML.

**D. Branch transforms apply to state value only**

- Any **transform** on a branch condition is applied **only to the state value** before the comparison. The comparison value in YAML is written in normalized form by the author; the right-hand side is **not** transformed. Semantics must stay unambiguous in docs and tests.

**E. Branch-time transforms — narrow**

- For branch conditions in this pass, support **only**: **trim**, **lower**, **upper**. Do **not** make default/coalesce part of branch condition transforms. Branch semantics stay simple and readable.

**F. Where operator evaluation lives**

- **Recommendation:** Put operator evaluation in a **small shared utility** (e.g. **WorkflowConditionEvaluator** or **BranchPredicates**), not inside BranchStep. BranchStep parses `when`, resolves state value, applies allowed transforms (trim/lower/upper), then calls the utility. This keeps BranchStep from becoming a kitchen sink and gives a single testable place for operator semantics.

---

### 2.2 Shared transform model

**A. Distinction by use**

- **Transforms for branch evaluation:** trim, lower, upper only (state value before compare).
- **Transforms for value capture/extraction:** trim, lower, upper, default (used in CaptureFieldFromEventStep and extract_event). **coalesce** is deferred this pass to keep the transform model simpler; only **default** is supported for capture/extract.

**B. Where each transform is allowed (this pass)**

| Transform | Branch conditions | Capture / extract_event |
|-----------|--------------------|-------------------------|
| trim      | Yes                | Yes                     |
| lower     | Yes                | Yes                     |
| upper     | Yes                | Yes                     |
| default   | No                 | Yes                     |
| coalesce  | No (deferred)      | No (deferred)           |

**C. Config shape for transforms**

- **Canonical style: list.** Transforms are configured as a **list** of transform names, e.g. `transform: [trim, lower]`. This is the documented and preferred form.
- **Convenience:** If a single string is provided (e.g. `transform: lower`), the implementation may treat it as a single-element list. Canonical remains the list form.

**D. Legacy trimAndLower**

- **trimAndLower: true** is legacy. It maps to the new model as: **transforms: [trim, lower]** (or equivalent internal list). **When both trimAndLower and transforms are present, trimAndLower: true overrides transforms** (i.e. behave as [trim, lower]) for backward compatibility. In docs and specs, **explicitly state** that trimAndLower: true overrides transforms when both are present.

---

### 2.3 extract_event — precise source-path model

**Direction:** Add a dedicated **extract_event** step. Do **not** generalize CaptureFieldFromEventStep into a generic "extract anything" step in this pass. CaptureFieldFromEventStep stays focused on "one user-facing content field (message/interaction)"; extract_event is the explicit "event fields → state" step with a defined source model.

**Source syntax (explicit)**

- **payload.\<key\>** — One-level payload key. Resolved by `event.getPayload().get(key)`. Key must be a single identifier (e.g. `payload.channelId`, `payload.messageId`, `payload.text`). **Nested payload paths are not supported in this pass** (no `payload.foo.bar`).
- **context.\<field\>** — NormalizedEventContext getter. Resolved by the corresponding getter on `NormalizedEventContext.from(event)`. Supported **context** fields are the public getters that return simple values or lists: **channelId, threadId, conversationId, actorId, actorUsername, text, sourceType, eventType, repo, interactionId, token, customId**. For **mentions**, **labels**, and **interactionValues** (list-valued context fields), store them in workflow state as **List** (e.g. `List<String>`). Prefer List in state rather than comma-joined strings; state supports `Map<String, Object>` so List is valid.

**Missing source path**

- If the source path does not exist (payload key absent, or context field not applicable): the **default** for that mapping is used if configured; otherwise store nothing (or omit the key from state). Do not fail the step; skip that mapping and continue.

**Default and transforms**

- Each mapping may specify **default** (value when source is null/blank/missing) and **transforms** (list, applied in order after reading the value; default allowed here; coalesce deferred). Order: read source → apply default if missing/blank → apply transforms → state.put(storeIn, result).

**Multiple mappings**

- **extract_event supports multiple mappings in one step.** Config: **fromEvent** as a list of objects, each with **from** (source path), **storeIn** (state key), and optional **default** and **transforms**.

**Summary**

- One new step type: **extract_event**.
- Source paths: **payload.\<key\>** (flat only), **context.\<field\>** (supported getters only).
- No nested payload in this pass. Missing path → use default or skip. Default and transforms per mapping; multiple mappings in one step.

---

### 2.4 Routing cleanup

**A. Why RoutingRule**

- Today **Routing** is "filter + botId." Renaming or replacing it with **RoutingRule** makes the concept explicit: "one rule in an ordered policy list." Code and docs can refer to "routing policy" and "rules" instead of an opaque list. No behavior change, but clearer intent and a better base for future policy extensions (e.g. priority, labels) without a second rename.

**B. Priority**

- **Do not add a priority field in this pass.** It would be unused and add no behavior. Recommendation: **RoutingRule** holds only **filter** and **botId**.

**C. Policy-shaped internally**

- **Router** stores an ordered list of rules and applies (1) filter matching in order, (2) lifecycle-owned-space precedence for Discord. Document in Router and in docs that "routing is an ordered policy list; when a Discord channel has a lifecycle owner with handlesOwnedSpaces, that owner is chosen regardless of filter order." Do not change order-based behavior or filter semantics.

**D. Config and build**

- **Router** stores **List\<RoutingRule\>**.
- **ConfigLoader** builds **RoutingRule(filter, botId)** from the existing YAML (`routing[].botId`, `routing[].filter`). No change to external routing config.

**E. Routing type**

- **Recommendation:** **Replace** the current **Routing** type with **RoutingRule** (filter, botId). Update Router, ConfigLoader, and all tests to use RoutingRule. Do not keep both types long-term; avoid compatibility clutter. If another module holds a reference to Routing, that reference becomes RoutingRule in this pass.

---

### 2.5 Workflow vs action boundary (architecture guideline)

**After this pass, express in YAML when possible**

- **Flow and branching:** Step order, branches with operators (equals, not_equals, blank, nonblank, contains, starts_with, regex, one_of) and optional state-value transforms (trim/lower/upper).
- **Capturing user input:** capture_field (message/interaction content, with transforms including default).
- **Pulling event metadata into state:** extract_event with payload.\<key\> and context.\<field\> so later steps and interpolation can use channelId, messageId, actorId, etc., without custom code.
- **Simple messaging and control:** done (with interpolation), branch, clear.

**Keep in Java workflow actions**

- Side effects: API calls, creating channels/threads, launching runs, sending to connectors, provisioning.
- Anything that needs non-trivial logic, external services, or the full Event object beyond what NormalizedEventContext and payload expose.

**Not in routing logic**

- Routing decides *which bot(s)* handle an event. It does not define workflow steps, state shape, or message content. Filter fields (authors, channels, mention, trigger, repos, labels) stay as today.

**When to prefer extract_event + branch over a new action**

- When the only goal is to move event or context fields into state and branch on them (e.g. "if channelId is X go here, else there"), use extract_event plus branch. Add a new action only when you need a side effect or logic that cannot be expressed with the DSL (operators, transforms, extract_event).

---

## 3. Revised implementation plan

### Phase 1: Shared utilities

1. **WorkflowTransforms** — New class in workflow package. Static methods: trim, lower, upper, default(value, defaultIfBlank). Null/blank-safe. Used by branch (trim/lower/upper only), capture, and extract_event. (coalesce deferred this pass.)
2. **WorkflowConditionEvaluator** (or BranchPredicates) — New small utility. Evaluates one condition: (stateValue, operator, compareValue, transformList). transformList only trim/lower/upper. Operators: equals, not_equals, blank, nonblank, contains, starts_with, regex (full-string match), one_of. For one_of, compareValue is a List (YAML list); if a string is passed internally, treat as single-element list only for tolerance. Unit tests per operator and for transform application to state value only.

### Phase 2: BranchStep

3. **BranchStep** — Parse `when` for optional `operator` and `transform`. Resolve state value; apply only trim/lower/upper via WorkflowTransforms (reject or ignore default/coalesce in branch). Delegate comparison to WorkflowConditionEvaluator. Keep legacy behavior when operator/transform absent. Tests: each operator, legacy equals/truthy/else, transform applied to state value only, one_of with YAML list, regex full-string match success/failure, blank/nonblank edges.

### Phase 3: Capture and extract_event

4. **CaptureFieldFromEventStep** — Add **transforms** (list; canonical). Support trim, lower, upper, default (coalesce deferred). **trimAndLower: true** maps to [trim, lower]; **when both trimAndLower and transforms are present, trimAndLower wins** for backward compatibility — document this explicitly in specs/docs. Use WorkflowTransforms. Tests: transform order, trimAndLower backward compat.
5. **ExtractEventFieldsStep** — New step. Config: **fromEvent** list of { from, storeIn, default?, transforms? }. Resolve **from** by prefix: payload.\<key\> → event.getPayload().get(key) (flat only); context.\<field\> → NormalizedEventContext getter (supported fields only). List-valued context fields stored as List in state. Missing path → default or skip. Apply default then transforms per mapping; state.put(storeIn, result). Support multiple mappings. Register in ConfigurableWorkflowRunner as **extract_event**. Tests: payload.\<key\>, context.\<field\>, missing path, default, transforms, multiple mappings, list-valued fields as List, extracted values used in interpolation/branch.

### Phase 4: Routing

6. **RoutingRule** — New type (filter, botId). Replace usages of Routing; remove or deprecate Routing.
7. **Router** — Store List\<RoutingRule\>; addRouting(RoutingRule); route() logic unchanged (order, lifecycle precedence). Document ordered policy + lifecycle precedence.
8. **ConfigLoader** — Build RoutingRule from routing[].filter and routing[].botId; add to router. External YAML unchanged.
9. **RouterTest** — No behavior change; order preserved; lifecycle-owned-space precedence preserved; use RoutingRule.

### Phase 5: Docs and specs

10. **workflow-registry, README, mkdoc** — Branch operators (regex = full-string match) and state-value-only transforms; one_of as YAML list; extract_event source paths (payload.\<key\>, context.\<field\>), supported context fields, **list-valued context fields stored as List in state**; missing path behavior; workflow-vs-action guideline; routing as ordered policy; RoutingRule without priority. Transform config: list canonical; **trimAndLower shorthand; explicitly state that trimAndLower: true overrides transforms when both are present (backward compatibility).**

### Phase 6: Integration test

11. **ConfigurableWorkflowRunnerTest** (or equivalent) — At least one workflow that uses extract_event (e.g. context.channelId, payload.messageId) → branch with an operator and optional transform → done message that interpolates extracted state. Asserts final message or step outcome.

---

## 4. Tradeoffs and intentionally deferred work

| Decision | Tradeoff | Deferred |
|----------|----------|----------|
| Branch: operators in shared utility | BranchStep stays small; one place for semantics. | Composite conditions (and/or across keys); step labels by name. |
| Branch transforms = trim/lower/upper only | Simple, readable branch semantics. | default/coalesce in branch; custom predicates. |
| one_of = YAML list only (docs) | Clear, consistent config. | Comma-separated as first-class. |
| regex = full-string match | Unambiguous; no substring surprise. | Substring/regex-find if needed later. |
| List for context list fields | Single convention; state supports List. | Comma-joined string alternative. |
| coalesce deferred | Simpler transform model; default suffices for capture/extract. | coalesce in a later pass if needed. |
| extract_event as separate step | Explicit, testable source model; CaptureField stays focused. | Generalizing CaptureField to arbitrary event keys. |
| Flat payload only | Simple resolution; no nested path parsing. | payload.foo.bar. |
| RoutingRule without priority | No dead code; clear abstraction. | Priority-based ordering later. |
| Replace Routing with RoutingRule | Single concept; no dual types. | Keeping both for compatibility. |

**Out of scope (unchanged):** Connector/module redesign; deliveryChannelId/lifecycle-thread behavior; full template language; routing config schema changes.

---

## 5. Files to add or touch

| Area | File | Change |
|------|------|--------|
| Workflow | New: `workflow/WorkflowTransforms.java` | trim, lower, upper, default (coalesce deferred). |
| Workflow | New: `workflow/WorkflowConditionEvaluator.java` (or `BranchPredicates`) | evaluate(stateValue, operator, compareValue, transformList); operators + trim/lower/upper; regex = full-string match. |
| Workflow | BranchStep.java | Parse when; apply trim/lower/upper to state value; delegate to WorkflowConditionEvaluator; legacy forms unchanged. |
| Workflow | CaptureFieldFromEventStep.java | transforms list; trimAndLower → [trim, lower]; WorkflowTransforms. |
| Workflow | New: ExtractEventFieldsStep.java | fromEvent; payload.\<key\> / context.\<field\>; list-valued as List; default; transforms; multiple mappings. |
| Workflow | ConfigurableWorkflowRunner.java | Build ExtractEventFieldsStep; pass transforms for capture_field and branch. |
| Bot | New: RoutingRule.java (replace Routing) | filter, botId only. |
| Bot | Router.java | List\<RoutingRule\>; addRouting(RoutingRule); route() unchanged; document policy. |
| Config | ConfigLoader.java | Build RoutingRule from YAML; no config schema change. |
| Tests | WorkflowTransformsTest, WorkflowConditionEvaluatorTest, BranchStepTest, ExtractEventFieldsStepTest, CaptureFieldFromEventStepTest, RouterTest, ConfigurableWorkflowRunnerTest | Per test plan below. |
| Specs/docs | workflow-registry, README, mkdoc | Per design and implementation. |

---

## 6. Test plan

### A. BranchStep tests

- Each new operator: equals, not_equals, blank, nonblank, contains, starts_with, regex, one_of (with YAML-list value).
- Legacy: `when: { key, value }` (equals); `when: "<stateKey>"` (truthy); `when: "else"`.
- Branch transforms applied to **state value only** (compare value unchanged).
- one_of with list input only (and, if implemented, single-string fallback not advertised).
- regex: full-string match semantics; success and failure cases.
- blank vs nonblank edge cases (null, "", "  ").

### B. Transform tests

- **WorkflowTransforms:** trim, lower, upper, default; null/blank inputs; order when chaining (coalesce deferred).
- **Transform order** when multiple transforms are configured (e.g. [trim, lower] order).
- **trimAndLower backward compatibility:** trimAndLower: true behaves as [trim, lower]; when both trimAndLower and transforms present, trimAndLower wins.

### C. extract_event tests

- Extracting from **payload.\<key\>** (e.g. channelId, messageId).
- Extracting from **context.\<field\>** (e.g. context.channelId, context.actorId); supported fields only.
- **Missing path:** no default → key not stored (or omitted); with default → default stored.
- **Default** when source missing/blank.
- **Transforms** applied after extraction (and after default).
- **Multiple mappings** in one step; all applied.
- **List-valued context fields** (e.g. context.mentions, context.labels) stored as List in state.
- **Extracted values** used in a later step (e.g. done message interpolation or branch) in the same run.

### D. Router tests

- **No behavior change** for existing route scenarios (authors, channels, mention, trigger, repos, labels).
- **Order preserved:** same bot list for same event when rules are in same order.
- **Lifecycle-owned-space precedence** preserved when channel has lifecycle owner with handlesOwnedSpaces.
- **RoutingRule** is the type used; routing outcomes unchanged.

### E. Integration-style workflow test

- At least one workflow test that: uses **extract_event** (e.g. context.channelId, payload.text); uses a **branch** with an operator (and optionally transform); ends with **done** that interpolates extracted state. Assert final message or completion state.

---

## 7. Success criteria

- **Expressiveness:** Fewer future bot-behavior changes require Java edits; branches and event extraction are expressible in YAML with clear semantics.
- **Clarity:** Config is more readable: operators named, transforms explicit, extract_event source paths documented; workflow-vs-action boundary documented.
- **Routing:** Internally policy-shaped (RoutingRule, documented ordered policy + lifecycle precedence); behavior unchanged; no unused priority field.
- **Backward compatibility:** Existing workflows and routing config work unchanged; legacy branch forms and trimAndLower preserved.
- **Tests:** Semantics protected by tests for each operator, transforms, extract_event source model, and routing; one integration-style workflow test.
- **Scope:** No connector redesign; no deliveryChannelId/lifecycle-thread changes; DSL remains a practical subset, not a full programming language.
