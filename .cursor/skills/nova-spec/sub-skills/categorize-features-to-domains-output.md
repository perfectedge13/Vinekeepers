# Categorize features to domains — output

**Status:** Pass

**Applied mappings (per task):**
- routing → **bot** (routing is bot-scoped capability)
- workflow-steps, cursor-gathering → **workflow**
- engine → **core**
- discord, github → **connectors**
- No bot id (luna) as feature slug; cursor-gathering remains the capability slug.

---

## 1. Feature slug → domain_slug

| feature_slug    | domain_slug |
|-----------------|-------------|
| env             | env         |
| config          | config      |
| routing         | bot         |
| bot             | bot         |
| events          | events      |
| state           | state       |
| tools           | tools       |
| audit           | audit       |
| reasoner        | reasoner    |
| workflow        | workflow    |
| workflow-steps   | workflow    |
| cursor-gathering| workflow    |
| core            | core        |
| engine          | core        |
| discord         | connectors  |
| github          | connectors  |

---

## 2. Per domain_slug: requirement_ids, asset_ids, features[]

### env
- **requirement_ids:** [REQ-ENV-001]
- **asset_ids:** [ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP]
- **features:**
  - id: FEAT-ENV  
    slug: env  
    title: Environment and .env loading  
    requirement_ids: [REQ-ENV-001]  
    asset_ids: [ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP]  
    status: active  

### config
- **requirement_ids:** [REQ-CONFIG-001]
- **asset_ids:** [ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML]
- **features:**
  - id: FEAT-CONFIG  
    slug: config  
    title: Bot config from YAML  
    requirement_ids: [REQ-CONFIG-001]  
    asset_ids: [ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML]  
    status: active  

### bot
- **requirement_ids:** [REQ-BOT-001, REQ-BOT-002, REQ-BOT-003]
- **asset_ids:** [ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-CONVERSATION-MODE, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY]
- **features:**
  - id: FEAT-ROUTING  
    slug: routing  
    title: Event routing and normalized context  
    requirement_ids: [REQ-BOT-001]  
    asset_ids: [ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT]  
    status: active  
  - id: FEAT-BOT  
    slug: bot  
    title: Bot runtime definition and tool policy  
    requirement_ids: [REQ-BOT-002, REQ-BOT-003]  
    asset_ids: [ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-CONVERSATION-MODE, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY]  
    status: active  

### events
- **requirement_ids:** [REQ-EVENTS-001]
- **asset_ids:** [ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER]
- **features:**
  - id: FEAT-EVENTS  
    slug: events  
    title: Event bus and publish/subscribe  
    requirement_ids: [REQ-EVENTS-001]  
    asset_ids: [ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER]  
    status: active  

### state
- **requirement_ids:** [REQ-STATE-001]
- **asset_ids:** [ASSET-STATE-STORE, ASSET-LUNA-STATE]
- **features:**
  - id: FEAT-STATE  
    slug: state  
    title: State store for workflow state  
    requirement_ids: [REQ-STATE-001]  
    asset_ids: [ASSET-STATE-STORE, ASSET-LUNA-STATE]  
    status: active  

### tools
- **requirement_ids:** [REQ-TOOLS-001]
- **asset_ids:** [ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-BOOTSTRAP]
- **features:**
  - id: FEAT-TOOLS  
    slug: tools  
    title: Tool registry and execution  
    requirement_ids: [REQ-TOOLS-001]  
    asset_ids: [ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-BOOTSTRAP]  
    status: active  

### audit
- **requirement_ids:** [REQ-AUDIT-001]
- **asset_ids:** [ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER]
- **features:**
  - id: FEAT-AUDIT  
    slug: audit  
    title: Audit log for tool calls and outcomes  
    requirement_ids: [REQ-AUDIT-001]  
    asset_ids: [ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER]  
    status: active  

### reasoner
- **requirement_ids:** [REQ-REASONER-001]
- **asset_ids:** [ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-ENGINE]
- **features:**
  - id: FEAT-REASONER  
    slug: reasoner  
    title: Reasoner interface for bot decisions  
    requirement_ids: [REQ-REASONER-001]  
    asset_ids: [ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-ENGINE]  
    status: active  

### workflow
- **requirement_ids:** [REQ-WORKFLOW-001, REQ-LUNA-001]
- **asset_ids:** [ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE, ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUN-RESULT, ASSET-STUB-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-SESSION-KEY-STRATEGY, ASSET-SESSION-KEY-STRATEGIES, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY, ASSET-STEP-RESULT, ASSET-STEP-OUTCOME, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-WORKFLOW-DEFINITION, ASSET-ASK-FOR-INPUT-STEP, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DYNAMIC-CHOICE-PROVIDER, ASSET-DONE-STEP, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-LUNA-RUN-STATE, ASSET-CURSOR-RUN-MONITOR, ASSET-CURSOR-AGENT-LAUNCH-REQUEST, ASSET-CURSOR-AGENT-CONVERSATION, ASSET-CURSOR-AGENT-DETAILS, ASSET-CURSOR-AGENT-LAUNCH-RESULT, ASSET-CURSOR-AGENT-MESSAGE, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-GITHUB-REPOS-CHOICE-PROVIDER, ASSET-BOTS-YAML, ASSET-BOOTSTRAP, ASSET-ENGINE]
- **features:**
  - id: FEAT-WORKFLOW  
    slug: workflow  
    title: Workflow runners and session lifecycle  
    requirement_ids: [REQ-WORKFLOW-001]  
    asset_ids: [ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE, ASSET-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUN-RESULT, ASSET-STUB-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-SESSION-KEY-STRATEGY, ASSET-SESSION-KEY-STRATEGIES, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY]  
    status: active  
  - id: FEAT-WORKFLOW-STEPS  
    slug: workflow-steps  
    title: Workflow step DSL and branching actions  
    requirement_ids: [REQ-WORKFLOW-001]  
    asset_ids: [ASSET-STEP-RESULT, ASSET-STEP-OUTCOME, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-WORKFLOW-DEFINITION, ASSET-ASK-FOR-INPUT-STEP, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DYNAMIC-CHOICE-PROVIDER, ASSET-DONE-STEP]  
    status: active  
  - id: FEAT-CURSOR-GATHERING  
    slug: cursor-gathering  
    title: Cursor-backed gathering workflow  
    requirement_ids: [REQ-LUNA-001]  
    asset_ids: [ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-LUNA-RUN-STATE, ASSET-CURSOR-RUN-MONITOR, ASSET-CURSOR-AGENT-LAUNCH-REQUEST, ASSET-CURSOR-AGENT-CONVERSATION, ASSET-CURSOR-AGENT-DETAILS, ASSET-CURSOR-AGENT-LAUNCH-RESULT, ASSET-CURSOR-AGENT-MESSAGE, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-GITHUB-REPOS-CHOICE-PROVIDER, ASSET-BOTS-YAML, ASSET-BOOTSTRAP, ASSET-ENGINE]  
    status: active  

### core
- **requirement_ids:** [REQ-CORE-001, REQ-CORE-002, REQ-CORE-003]
- **asset_ids:** [ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-PACKAGE-MARKER, ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT]
- **features:**
  - id: FEAT-CORE  
    slug: core  
    title: Specs governance and bootstrap  
    requirement_ids: [REQ-CORE-001, REQ-CORE-002]  
    asset_ids: [ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-PACKAGE-MARKER]  
    status: active  
  - id: FEAT-ENGINE  
    slug: engine  
    title: Event-driven engine orchestration  
    requirement_ids: [REQ-CORE-003]  
    asset_ids: [ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT]  
    status: active  

### connectors
- **requirement_ids:** [REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001]
- **asset_ids:** [ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-GITHUB-SOURCE]
- **features:**
  - id: FEAT-CONNECTORS-DISCORD  
    slug: discord  
    title: Discord event source and reply  
    requirement_ids: [REQ-CONNECTORS-DISCORD-001]  
    asset_ids: [ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK]  
    status: active  
  - id: FEAT-CONNECTORS-GITHUB  
    slug: github  
    title: GitHub event source  
    requirement_ids: [REQ-CONNECTORS-GITHUB-001]  
    asset_ids: [ASSET-GITHUB-SOURCE]  
    status: active  

---

## 3. Orphan check

- **Requirements:** All 17 requirement ids (REQ-CORE-001..003, REQ-ENV-001, REQ-CONFIG-001, REQ-BOT-001..003, REQ-EVENTS-001, REQ-STATE-001, REQ-TOOLS-001, REQ-AUDIT-001, REQ-REASONER-001, REQ-WORKFLOW-001, REQ-LUNA-001, REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001) appear in at least one domain-owned feature.
- **Assets:** All registry assets are referenced by at least one feature in the domain lists above (some assets participate in multiple features by design).

---

## 4. Handoff for update-registry

Use the **feature_slug → domain_slug** mapping and the **per domain_slug** blocks above to:

1. **core-registry.yml:** Set each feature’s `domain_slug` per mapping; ensure features list matches this output (FEAT-ROUTING under domain bot, FEAT-WORKFLOW / FEAT-WORKFLOW-STEPS / FEAT-CURSOR-GATHERING under workflow, FEAT-CORE / FEAT-ENGINE under core).
2. **connectors-registry.yml:** Keep domain_slug `connectors` for FEAT-CONNECTORS-DISCORD and FEAT-CONNECTORS-GITHUB; no change to mapping.
3. Update any `doc_path` or feature summaries from existing registry only if a feature’s domain_slug changed (e.g. routing doc_path under bot domain).
