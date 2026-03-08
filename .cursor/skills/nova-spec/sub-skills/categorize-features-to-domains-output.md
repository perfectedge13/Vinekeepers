# Categorize features to domains — output

## feature_slug → domain_slug

| feature_slug | domain_slug |
|--------------|-------------|
| env | core |
| config | core |
| bot | core |
| events | core |
| state | core |
| tools | core |
| audit | core |
| reasoner | core |
| workflow | core |
| core | core |
| luna | core |
| discord | connectors |
| github | connectors |

## Per domain_slug

### core

- **requirement_ids:** REQ-CORE-001, REQ-CORE-002, REQ-CORE-003, REQ-ENV-001, REQ-CONFIG-001, REQ-BOT-001, REQ-BOT-002, REQ-BOT-003, REQ-EVENTS-001, REQ-STATE-001, REQ-TOOLS-001, REQ-AUDIT-001, REQ-REASONER-001, REQ-WORKFLOW-001, REQ-LUNA-001
- **asset_ids:** ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-ENV-LOADER, ASSET-ENV, ASSET-ENGINE, ASSET-CONFIG-LOADER, ASSET-BOTS-YAML, ASSET-BOT-CONFIG, ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY, ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER, ASSET-STATE-STORE, ASSET-LUNA-STATE, ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-STUB-TOOL, ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER, ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-STUB-REASONER, ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE, ASSET-LUNA-WORKFLOW, ASSET-WORKFLOW-RUNNER, ASSET-STUB-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-STEP-RESULT, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-WORKFLOW-DEFINITION, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-ASK-FOR-INPUT-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DONE-STEP, ASSET-PACKAGE-MARKER, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL
- **features:**
  - id: FEAT-ENV, slug: env, title: Environment and .env loading, requirement_ids: [REQ-ENV-001], asset_ids: [ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP], status: active
  - id: FEAT-CONFIG, slug: config, title: Bot config from YAML, requirement_ids: [REQ-CONFIG-001], asset_ids: [ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML], status: active
  - id: FEAT-BOT, slug: bot, title: Bot definition, routing, and tool policy, requirement_ids: [REQ-BOT-001, REQ-BOT-002, REQ-BOT-003], asset_ids: [ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY], status: active
  - id: FEAT-EVENTS, slug: events, title: Event bus and publish/subscribe, requirement_ids: [REQ-EVENTS-001], asset_ids: [ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER], status: active
  - id: FEAT-STATE, slug: state, title: State store for workflow state, requirement_ids: [REQ-STATE-001], asset_ids: [ASSET-STATE-STORE, ASSET-LUNA-STATE], status: active
  - id: FEAT-TOOLS, slug: tools, title: Tool registry and execution, requirement_ids: [REQ-TOOLS-001], asset_ids: [ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-STUB-TOOL], status: active
  - id: FEAT-AUDIT, slug: audit, title: Audit log for tool calls and outcomes, requirement_ids: [REQ-AUDIT-001], asset_ids: [ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER], status: active
  - id: FEAT-REASONER, slug: reasoner, title: Reasoner interface for bot decisions, requirement_ids: [REQ-REASONER-001], asset_ids: [ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-STUB-REASONER], status: active
  - id: FEAT-WORKFLOW, slug: workflow, title: Workflow state machine and config-driven runners, requirement_ids: [REQ-WORKFLOW-001], asset_ids: [ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE, ASSET-LUNA-WORKFLOW, ASSET-LUNA-STATE, ASSET-WORKFLOW-RUNNER, ASSET-STUB-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-STEP-RESULT, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-WORKFLOW-DEFINITION, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-ASK-FOR-INPUT-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DONE-STEP], status: active
  - id: FEAT-CORE, slug: core, title: Core engine, bootstrap, and specs, requirement_ids: [REQ-CORE-001, REQ-CORE-002, REQ-CORE-003], asset_ids: [ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-PACKAGE-MARKER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-WORKFLOW-RUNNER], status: active
  - id: FEAT-LUNA, slug: luna, title: Luna bot — Discord /Luna, multi-turn gather, Cursor Cloud API, requirement_ids: [REQ-LUNA-001], asset_ids: [ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-ENGINE, ASSET-BOOTSTRAP, ASSET-BOTS-YAML], status: active

### connectors

- **requirement_ids:** REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001
- **asset_ids:** ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-GITHUB-SOURCE
- **features:**
  - id: FEAT-CONNECTORS-DISCORD, slug: discord, title: Discord event source and reply, requirement_ids: [REQ-CONNECTORS-DISCORD-001], asset_ids: [ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY], status: active
  - id: FEAT-CONNECTORS-GITHUB, slug: github, title: GitHub event source, requirement_ids: [REQ-CONNECTORS-GITHUB-001], asset_ids: [ASSET-GITHUB-SOURCE], status: active

---

## Return

**Pass.** All 13 candidate features assigned to exactly one domain (11 → core, 2 → connectors). Every requirement and every asset appears in exactly one domain; no orphans, no cross-registry duplicates. Core holds REQ-CORE/ENV/CONFIG/BOT/EVENTS/STATE/TOOLS/AUDIT/REASONER/WORKFLOW/LUNA and their assets; connectors holds REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001 and connector assets. Output ready for update-registry.
