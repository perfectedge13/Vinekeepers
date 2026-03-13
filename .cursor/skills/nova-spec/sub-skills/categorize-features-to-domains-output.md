# Categorize features to domains — output

**Status:** Pass

Candidate features from scan were assigned to the 11 domains (core, env, config, bot, events, state, tools, audit, reasoner, workflow, connectors) using existing `domain_slug` and registry contents. No bot-id feature slugs present; no splits or renames performed.

---

## 1. Feature slug → domain_slug mapping

| feature_slug      | domain_slug |
|-------------------|-------------|
| core              | core        |
| engine            | core        |
| env               | env         |
| config            | config      |
| routing           | bot         |
| bot               | bot         |
| events            | events      |
| state             | state       |
| tools             | tools       |
| audit             | audit       |
| reasoner          | reasoner    |
| workflow          | workflow    |
| workflow-steps    | workflow    |
| cursor-gathering  | workflow    |
| discord           | connectors  |
| github            | connectors  |

---

## 2. Per domain_slug: requirement_ids[], asset_ids[], features[]

### core

- **requirement_ids:** REQ-CORE-001, REQ-CORE-002, REQ-CORE-003
- **asset_ids:** ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-PACKAGE-MARKER, ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT
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

**Registry file:** core-registry.yml

---

### env

- **requirement_ids:** REQ-ENV-001
- **asset_ids:** ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP
- **features:**
  - id: FEAT-ENV  
    slug: env  
    title: Environment and .env loading  
    requirement_ids: [REQ-ENV-001]  
    asset_ids: [ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP]  
    status: active

**Registry file:** core-registry.yml

---

### config

- **requirement_ids:** REQ-CONFIG-001
- **asset_ids:** ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML, ASSET-BOT-DEFINITION
- **features:**
  - id: FEAT-CONFIG  
    slug: config  
    title: Bot config from YAML  
    requirement_ids: [REQ-CONFIG-001]  
    asset_ids: [ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML, ASSET-BOT-DEFINITION]  
    status: active

**Registry file:** core-registry.yml

---

### bot

- **requirement_ids:** REQ-BOT-001, REQ-BOT-002, REQ-BOT-003
- **asset_ids:** ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-CONVERSATION-MODE, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY
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

**Registry file:** core-registry.yml

---

### events

- **requirement_ids:** REQ-EVENTS-001
- **asset_ids:** ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER
- **features:**
  - id: FEAT-EVENTS  
    slug: events  
    title: Event bus and publish/subscribe  
    requirement_ids: [REQ-EVENTS-001]  
    asset_ids: [ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER]  
    status: active

**Registry file:** core-registry.yml

---

### state

- **requirement_ids:** REQ-STATE-001
- **asset_ids:** ASSET-STATE-STORE, ASSET-LUNA-STATE
- **features:**
  - id: FEAT-STATE  
    slug: state  
    title: State store for workflow state  
    requirement_ids: [REQ-STATE-001]  
    asset_ids: [ASSET-STATE-STORE, ASSET-LUNA-STATE]  
    status: active

**Registry file:** core-registry.yml

---

### tools

- **requirement_ids:** REQ-TOOLS-001
- **asset_ids:** ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-BOOTSTRAP
- **features:**
  - id: FEAT-TOOLS  
    slug: tools  
    title: Tool registry and execution  
    requirement_ids: [REQ-TOOLS-001]  
    asset_ids: [ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-BOOTSTRAP]  
    status: active

**Registry file:** core-registry.yml

---

### audit

- **requirement_ids:** REQ-AUDIT-001
- **asset_ids:** ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER
- **features:**
  - id: FEAT-AUDIT  
    slug: audit  
    title: Audit log for tool calls and outcomes  
    requirement_ids: [REQ-AUDIT-001]  
    asset_ids: [ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER]  
    status: active

**Registry file:** core-registry.yml

---

### reasoner

- **requirement_ids:** REQ-REASONER-001
- **asset_ids:** ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-ENGINE
- **features:**
  - id: FEAT-REASONER  
    slug: reasoner  
    title: Reasoner interface for bot decisions  
    requirement_ids: [REQ-REASONER-001]  
    asset_ids: [ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-ENGINE]  
    status: active

**Registry file:** core-registry.yml

---

### workflow

- **requirement_ids:** REQ-WORKFLOW-001, REQ-LUNA-001
- **asset_ids:** ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE, ASSET-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUN-RESULT, ASSET-STUB-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-SESSION-KEY-STRATEGY, ASSET-SESSION-KEY-STRATEGIES, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY, ASSET-STEP-RESULT, ASSET-STEP-OUTCOME, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-WORKFLOW-DEFINITION, ASSET-ASK-FOR-INPUT-STEP, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DYNAMIC-CHOICE-PROVIDER, ASSET-DONE-STEP, ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-LUNA-RUN-STATE, ASSET-CURSOR-RUN-MONITOR, ASSET-CURSOR-AGENT-LAUNCH-REQUEST, ASSET-CURSOR-AGENT-CONVERSATION, ASSET-CURSOR-AGENT-DETAILS, ASSET-CURSOR-AGENT-LAUNCH-RESULT, ASSET-CURSOR-AGENT-MESSAGE, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-CURSOR-INSTRUCTION-COMPOSER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-GITHUB-REPOS-CHOICE-PROVIDER, ASSET-BOTS-YAML, ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-LIFECYCLE-CONTEXT, ASSET-LIFECYCLE-CONTEXT-STORE, ASSET-RUNTIME-BOT-INSTANCE, ASSET-CREATE-CHANNEL-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-PROVISION-BOT-INSTANCE-ACTION, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION
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
    asset_ids: [ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-LUNA-RUN-STATE, ASSET-CURSOR-RUN-MONITOR, ASSET-CURSOR-AGENT-LAUNCH-REQUEST, ASSET-CURSOR-AGENT-CONVERSATION, ASSET-CURSOR-AGENT-DETAILS, ASSET-CURSOR-AGENT-LAUNCH-RESULT, ASSET-CURSOR-AGENT-MESSAGE, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-CURSOR-INSTRUCTION-COMPOSER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-GITHUB-REPOS-CHOICE-PROVIDER, ASSET-BOTS-YAML, ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-LIFECYCLE-CONTEXT, ASSET-LIFECYCLE-CONTEXT-STORE, ASSET-RUNTIME-BOT-INSTANCE, ASSET-CREATE-CHANNEL-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-PROVISION-BOT-INSTANCE-ACTION, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION]  
    status: active

**Registry file:** core-registry.yml

---

### connectors

- **requirement_ids:** REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001
- **asset_ids:** ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-GITHUB-SOURCE
- **features:**
  - id: FEAT-CONNECTORS-DISCORD  
    slug: discord  
    title: Discord event source and reply  
    requirement_ids: [REQ-CONNECTORS-DISCORD-001]  
    asset_ids: [ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-OUTBOUND-DELIVERY-ROUTER]  
    status: active
  - id: FEAT-CONNECTORS-GITHUB  
    slug: github  
    title: GitHub event source  
    requirement_ids: [REQ-CONNECTORS-GITHUB-001]  
    asset_ids: [ASSET-GITHUB-SOURCE]  
    status: active

**Registry file:** connectors-registry.yml

---

## 3. Registry file → domain groups (for orchestrator)

| Registry file          | domain_slugs                                                                 |
|------------------------|-------------------------------------------------------------------------------|
| core-registry.yml      | core, env, config, bot, events, state, tools, audit, reasoner, workflow     |
| connectors-registry.yml| connectors                                                                    |

Use the per-domain breakdown above for each domain when calling update-registry: pass the domain’s `requirement_ids`, `asset_ids`, and `features[]` for the corresponding registry file.
