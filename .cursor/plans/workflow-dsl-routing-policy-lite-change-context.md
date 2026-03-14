# Change context (for plan_change / implement)

## Scope

**Request:** Implement workflow-dsl-routing-policy-lite plan (Workflow DSL upgrade + routing policy cleanup lite).

**Impacted registry slice:** workflow-registry.yml (FEAT-WORKFLOW, FEAT-WORKFLOW-STEPS, REQ-WORKFLOW-001; BranchStep, CaptureFieldFromEventStep, ConfigurableWorkflowRunner; new WorkflowTransforms, WorkflowConditionEvaluator, ExtractEventFieldsStep); bot-registry.yml (FEAT-ROUTING, REQ-BOT-001; Router, Routing → RoutingRule, RoutingFilter, NormalizedEventContext); config-registry.yml (FEAT-CONFIG, REQ-CONFIG-001; ConfigLoader builds RoutingRule from YAML).

**Features / requirements / assets in scope:**
- **Features:** FEAT-WORKFLOW, FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING (workflow); FEAT-ROUTING (bot); FEAT-CONFIG (config).
- **Requirements:** REQ-WORKFLOW-001, REQ-LUNA-001 (workflow); REQ-BOT-001 (bot); REQ-CONFIG-001 (config).
- **Assets:** ASSET-BRANCH-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-CALL-ACTION-STEP, ASSET-ROUTER, ASSET-ROUTING (replaced by RoutingRule), ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-CONFIG-LOADER; new: WorkflowTransforms, WorkflowConditionEvaluator, ExtractEventFieldsStep, RoutingRule.

---

## Per feature / requirement

### FEAT-WORKFLOW-STEPS / REQ-WORKFLOW-001 (Workflow step DSL)

**Feature:** FEAT-WORKFLOW-STEPS — Workflow step DSL and branching actions. doc_path: features/domain/workflow/workflow-steps.md. status: active. Summary: WorkflowDefinition and step/action primitives define the configurable workflow DSL and branch execution rules.

**Requirements (excerpt):**
- **REQ-WORKFLOW-001:** Workflow state machine and config-driven runners. Statement: Workflow handle(event, context, state) returns WorkflowResult; WorkflowRunner runs workflow for an event and returns WorkflowRunResult with reply, waiting, completion, error state. Configurable workflows support prompt_for_field, capture_field; capture_field supports optional trimAndLower; BranchStep branch config may include clear; when key/value may use value "" for empty; step bind and state passed to actions; create_thread action contract as in spec.
- **Acceptance (relevant):** capture_field trimAndLower; BranchStep clear; when key/value value ""; create_thread storeIn, lifecycle gateway, setDeliveryTargetId.

**Anti_patterns (guardrails + workflow-registry):**
- Do not delete requirements; do not change schema or invent new spec keys; repair spec drift before coding; consider anti_patterns on requirements and assets.
- REQ-LUNA-001 (workflow-registry): Hardcoding Discord channel in workflow; storing secrets in state; do not rely on discordTrigger alone for Luna; do not log Cursor API key/body; do not assume single Cursor error shape; do not serialize null in Cursor request payload.

**Assets (impacted):**
- ASSET-BRANCH-STEP — path: src/main/java/com/vinekeepers/workflow/steps/BranchStep.java. Role: Branch by condition; add operator/transform support via WorkflowConditionEvaluator; parse when for operator, transform; apply only trim/lower/upper to state value.
- ASSET-CAPTURE-FIELD-STEP — path: src/main/java/com/vinekeepers/workflow/steps/CaptureFieldFromEventStep.java. Role: Capture field from event; add transforms list; trimAndLower true → [trim, lower]; when both present trimAndLower wins.
- ASSET-CONFIGURABLE-WORKFLOW-RUNNER — path: src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java. Role: Run workflow from WorkflowDefinition; register extract_event step; pass transforms for capture_field and branch.
- **New:** WorkflowTransforms — workflow package; static trim, lower, upper, default; null/blank-safe.
- **New:** WorkflowConditionEvaluator (or BranchPredicates) — evaluate(stateValue, operator, compareValue, transformList); operators equals, not_equals, blank, nonblank, contains, starts_with, regex (full-string), one_of; transformList trim/lower/upper only.
- **New:** ExtractEventFieldsStep — fromEvent list of { from, storeIn, default?, transforms? }; payload.<key> and context.<field>; list-valued context as List in state; default then transforms; register as extract_event.

**Doc excerpts (workflow-steps):**
- **decisions:** ResponseIntent/OutboundResponse for connector sink; step config intent, choices, confirmLabel, cancelLabel, fields; NormalizedEventContext interaction payload for capture. CallActionStep resolves workflow action or ToolRunner. StepOutcome explicit for waiting/completion/error.
- **contracts:** WorkflowDefinition id + ordered step configs. StepResult message, stored values, next-step, StepOutcome, optional OutboundResponse. capture_field trimAndLower when true. WorkflowActionRegistry; bind precedence over state for call_action. create_channel/post_channel_message/create_thread/launch_cursor_run contracts.
- **known-issues:** DSL limited to built-in step types; no general looping/sub-workflow; tool-backed actions depend on runner context.

---

### FEAT-ROUTING / REQ-BOT-001 (Routing)

**Feature:** FEAT-ROUTING — Event routing and normalized context. doc_path: features/domain/bot/routing.md. status: active. Summary: Router matches events to bots through routing filters over a normalized event view.

**Requirements (excerpt):**
- **REQ-BOT-001:** Route events to bots by routing rules and ownership. Statement: Router matches using Routing and EventFilter/RoutingFilter over normalized event view; lifecycle ownership when channel has context and owner has handlesOwnedSpaces (single-owner precedence). Filter-based routing otherwise; discordAuthors by actor id or username.
- **Acceptance:** Router.match returns bot ids; lifecycle owner precedence; filter-based when no context or owner without handlesOwnedSpaces; NormalizedEventContext actorId, actorUsername; discordTrigger/discordMention message events only; interaction events author/channel only.

**Anti_patterns (bot-registry):**
- Do not hardcode bot ids (e.g. Arrietty) in Router or engine; use config-driven handlesOwnedSpaces and lifecycle context.

**Assets (impacted):**
- ASSET-ROUTER — path: src/main/java/com/vinekeepers/bot/Router.java. Role: Store List<RoutingRule>; addRouting(RoutingRule); route() logic unchanged; document ordered policy + lifecycle precedence.
- ASSET-ROUTING — path: src/main/java/com/vinekeepers/bot/Routing.java. **Replaced by RoutingRule** (filter, botId); remove or replace all usages.
- **New:** RoutingRule — filter, botId only; no priority in this pass.
- ASSET-ROUTING-FILTER — unchanged. ASSET-NORMALIZED-EVENT-CONTEXT — used by ExtractEventFieldsStep (context.<field> getters: channelId, threadId, conversationId, actorId, actorUsername, text, sourceType, eventType, repo, interactionId, token, customId; list-valued: mentions, labels, interactionValues).

**Doc excerpts (routing):**
- **decisions:** handlesOwnedSpaces for single-owner precedence; no hardcoded bot ids. discordTrigger/discordMention only for message events; interaction uses author/channel. discordAuthors from YAML; actorId/actorUsername in NormalizedEventContext. Routing over normalized event view.
- **contracts:** Routing holds filter (discordAuthors etc.). Router returns bot ids; depends on LifecycleContextStore and handlesOwnedSpaces map. **Implement:** RoutingRule holds filter and botId; ConfigLoader builds RoutingRule from routing[].filter and routing[].botId; external YAML unchanged.
- **known-issues:** Routing fields depend on connector; adding predicates requires filter layer code.

---

### FEAT-CONFIG / REQ-CONFIG-001 (ConfigLoader)

**Requirements (excerpt):**
- **REQ-CONFIG-001:** Load bot config from YAML. ConfigLoader parses routing filters; produces BotDefinition. Build RoutingRule from routing[].botId and routing[].filter; add to router. No change to external routing config schema.

**Assets:** ASSET-CONFIG-LOADER — path: src/main/java/com/vinekeepers/config/ConfigLoader.java. Role: Build RoutingRule(filter, botId) from routing[]; add to router.

---

## Plan constraints (from workflow-dsl-routing-policy-lite.plan.md)

- **Branch:** Operators equals, not_equals, blank, nonblank, contains, starts_with, regex (full-string match), one_of. Legacy when key/value (equals), when "else", when stateKey (truthy). one_of value: YAML list; single string tolerance internal only. Transforms on branch: state value only; only trim, lower, upper. WorkflowConditionEvaluator for evaluation; BranchStep delegates.
- **Transforms:** Branch: trim, lower, upper only. Capture/extract_event: trim, lower, upper, default (no coalesce this pass). Config: list canonical; single string → single-element list ok. trimAndLower: true → [trim, lower]; **when both trimAndLower and transforms present, trimAndLower wins** (document in specs/docs).
- **extract_event:** Step type extract_event. fromEvent list of { from, storeIn, default?, transforms? }. from: payload.<key> (flat only), context.<field> (NormalizedEventContext getters). Missing path → default or skip. List-valued context fields stored as List in state. Order: read → default if missing/blank → transforms → state.put.
- **Routing:** Replace Routing with RoutingRule (filter, botId). Router List<RoutingRule>; ConfigLoader builds RoutingRule; no priority field; document ordered policy + lifecycle precedence. No YAML schema change.
- **Workflow vs action:** In YAML: flow, branching, capture_field, extract_event, done/branch/clear. In Java: side effects, API calls, non-trivial logic. Routing does not define workflow steps or state shape.

---

## Schema constraints

- **req-registry:** Do not invent new requirement or asset keys; stay within existing schema. Mark deprecated rather than delete requirements.
- **Guardrails:** Repair spec drift first; avoid anti_patterns; no schema file changes or new keys without schema support.

---

## Validation (implement step)

- npm run validate-specs; npm run validate-drift; mvn test; mvn compile; npm run validate-docs.
- Update README.md and mkdoc feature pages (workflow-steps, routing) when runtime behavior or contracts change.
