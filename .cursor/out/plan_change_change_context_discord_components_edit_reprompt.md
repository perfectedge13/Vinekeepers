# Change context (for plan_change / implement)

**Plan:** Discord intake: initial components and edit-reprompt fixes — (1) Gateway send(channelId, messageId, content, components) and DiscordAppReplySink ChannelTarget path; (2) StepResult clearKeys, BranchStep clear config, runner clears state before setStepIndex; clear in bots.yaml; (3) Tests for channel components and branch clearKeys.

---

## Scope

**Request-derived:** Discord connector (gateway send with components, sink ChannelTarget); workflow steps (StepResult clearKeys, BranchStep clear config, ConfigurableWorkflowRunner state clear before setStepIndex); config (bots.yaml luna_cursor branch steps with clear); tests (channel components, branch clearKeys).

**Impacted registry slice:** core-registry.yml (REQ-CORE-003, REQ-WORKFLOW-001, REQ-LUNA-001; ASSET-ENGINE, ASSET-STEP-RESULT, ASSET-BRANCH-STEP, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-BOTS-YAML, ASSET-DISCORD-* from engine/sink usage); connectors-registry.yml (REQ-CONNECTORS-DISCORD-001; ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK).

**Features:** FEAT-ENGINE, FEAT-CONNECTORS-DISCORD, FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING.

---

## Per feature / requirement / asset

### REQ-CORE-003 — Event-driven engine routes events to bots
- **Statement:** Engine delivers replies via connector contract (AppReplySink) with lifecycle operations; builds OutboundResponse; resolves ReplyTarget (ChannelTarget, InteractionTarget); calls sink (source-prefix); no defer in engine.
- **Acceptance (excerpt):** Engine delivers via sink registry and lifecycle methods (respondImmediately, sendFollowUp, updateMessage, openModal); sink exposes getCapabilities.
- **Traceability:** ASSET-ENGINE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-OUTBOUND-RESPONSE, ASSET-RESPONSE-INTENT.

### REQ-CONNECTORS-DISCORD-001 — Discord event source and reply
- **Statement:** Discord gateway receives/sends; DiscordAppReplySink implements AppReplySink; lifecycle operations; render intents; fallback to text. DiscordReplySender (channelId, messageId, content).
- **Acceptance (excerpt):** DiscordAppReplySink implements AppReplySink; all lifecycle operations; render intents; fallback to text. Replies delivered to Discord via sink. DiscordReplySender supports send(channelId, messageId, content).
- **Traceability:** ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK.
- **Validation tests:** UNIT-DISCORD-APP-REPLY-SINK (DiscordAppReplySinkTest).

### REQ-WORKFLOW-001 — Workflow state machine and config-driven runners
- **Statement:** WorkflowRunner runs workflow; ConfigurableWorkflowRunner parses step config; session keys; prompt_for_field, capture_field, branch, call_action, done.
- **Acceptance (excerpt):** Configurable workflows support prompt_for_field and capture_field; state records waiting, completion, error; SessionKeyStrategies; CallActionStep invokes action or tool.
- **Traceability:** ASSET-STEP-RESULT, ASSET-BRANCH-STEP, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-WORKFLOW-DEFINITION.
- **Validation tests:** UNIT-STEP-RESULT (StepResultTest), UNIT-CONFIGURABLE-WORKFLOW-RUNNER-*.

### REQ-LUNA-001 — Luna bot
- **Statement:** Luna id luna; discordMention; workflowRef luna_cursor; prompts project, codeChange; confirmation (Launch/Edit repo/Edit request/Cancel); cursor.fullRun; run state and relay to Discord.
- **Acceptance (excerpt):** luna_cursor prompts and captures project and codeChange; confirmation step with choices; Edit repo / Edit request must reprompt (clear state so user re-enters).
- **Anti_patterns:** Hardcoding Discord channel in workflow; storing secrets in state; do not rely on discordTrigger alone for Luna activation.

### ASSET-DISCORD-GATEWAY-CONTRACT (DiscordGateway.java)
- **Role:** Gateway contract for receive/send and interaction lifecycle (sendFollowUp, updateMessage, openModal). Current: `void send(String channelId, String messageId, String content)`.
- **Change:** Add overload or replace with `send(String channelId, String messageId, String content, List<List<Map<String, Object>>> components)` so channel sends can include components.

### ASSET-DISCORD-GATEWAY (JdaDiscordGateway.java)
- **Role:** JDA-backed gateway; send(channelId, messageId, content); sendFollowUp/updateMessage with optional components.
- **Change:** Implement send(channelId, messageId, content, components); when replying to message, use components if non-null; else current behavior.

### ASSET-DISCORD-REPLY-SINK (DiscordAppReplySink.java)
- **Role:** Implements AppReplySink; lifecycle; render intents to Discord; fallback to text.
- **Change:** For ChannelTarget, call gateway.send(channelId, messageId, content, components) so initial channel replies include components (e.g. buttons from PresentChoices/ConfirmAction).

### ASSET-STEP-RESULT (StepResult.java)
- **Role:** Result of single step: nextStepIndex, storeIn, storeValue, outcome, message, promptMessage, waitingForField, richReply.
- **Change:** Add optional `clearKeys` (e.g. List<String> or Collection<String>); factory methods (goTo, etc.) need overloads or builder for clearKeys. Runner applies clearKeys to state before setStepIndex.

### ASSET-BRANCH-STEP (BranchStep.java)
- **Role:** Branches by when (else or key/value); returns StepResult.goTo(next).
- **Change:** Parse optional `clear` (list of state keys) from branch config; return StepResult that carries clearKeys so runner clears those keys before advancing (e.g. edit_repo → clear [project]; edit_request → clear [codeChange]).

### ASSET-CONFIGURABLE-WORKFLOW-RUNNER (ConfigurableWorkflowRunner.java)
- **Role:** Run workflow from definition; apply storage from StepResult; setStepIndex; persist state.
- **Change:** Before `state.setStepIndex(nextStepIndex)` when StepResult has clearKeys, clear those keys from state (ConfigurableWorkflowState must support clearKeys(keys)). Apply clearKeys regardless of outcome (continue, waiting, complete) when present.

### ASSET-CONFIGURABLE-WORKFLOW-STATE (ConfigurableWorkflowState.java)
- **Role:** Mutable state: data map, stepIndex, status, waiting metadata.
- **Change:** Add `clearKeys(Collection<String> keys)` (or `removeKeys`) that removes the given keys from `data`. Preserve __sessionKey if desired.

### ASSET-WORKFLOW-DEFINITION / step parsing (ConfigurableWorkflowRunner buildSteps)
- **Role:** buildSteps parses branch step: `branches` list with `when`, `next`.
- **Change:** When building BranchStep, pass branch config that includes optional `clear` list; BranchStep constructor accepts and uses it when building StepResult.

### ASSET-BOTS-YAML (config/bots.yaml)
- **Role:** Luna workflow luna_cursor: repo choice, custom repo, codeChange prompt/capture, confirmation (Launch/Edit repo/Edit request/Cancel), branch to step 0 (edit_repo) or 5 (edit_request) or 10 (launch) or 12 (cancel).
- **Change:** Add `clear: [project]` to branch when confirmAction is edit_repo (next: 0); add `clear: [codeChange]` to branch when confirmAction is edit_request (next: 5). Ensures reprompt clears previous project/codeChange so user re-enters.

---

## Schema constraints

- Do not add new top-level keys to req-registry schema. `clearKeys` and step config `clear` are code/config only; no spec schema change.
- Guardrails: no requirement deletion; repair drift first; avoid anti_patterns on requirements/assets.

---

## Doc excerpts (docs_dir: mkdoc)

### features/domain/connectors/discord.md
- Summary: Discord event source; gateway; DiscordAppReplySink delivers workflow replies; lifecycle; intents with fallback to text.
- Key assets: ASSET-DISCORD-GATEWAY, ASSET-DISCORD-REPLY-SINK.

### features/domain/connectors/discord/contracts.md
- DiscordReplySender: send(channelId, messageId, content). Update to note channel send may include components when gateway supports it.

### features/domain/workflow/workflow-steps.md
- Summary: StepResult with next-step, storage, outcome, richReply; branch when: else or key/value.
- ASSET-STEP-RESULT, ASSET-BRANCH-STEP. Add note: StepResult may carry clearKeys; branch step config may include clear: [keys] for reprompt flows.

### features/domain/workflow/workflow-steps/contracts.md
- StepResult carries message, stored values, next-step, StepOutcome, optional richReply. Add: optional clearKeys for state keys to clear before advancing (e.g. edit-reprompt).

---

## Anti_patterns

- **REQ-LUNA-001:** Hardcoding Discord channel in workflow; storing secrets in state; do not rely on discordTrigger alone for Luna activation.
- **Implement:** When adding gateway send with components, keep ChannelTarget path consistent with InteractionTarget (intents → components); avoid dropping components on channel path only.

---

## Tests to add

1. **Channel components:** DiscordAppReplySinkTest (or engine test): respondImmediately with ChannelTarget and OutboundResponse containing intent (e.g. PresentChoices) verifies gateway.send(channelId, messageId, content, components) called with non-null components.
2. **Branch clearKeys:** ConfigurableWorkflowRunnerTest or BranchStepTest: branch step with clear: [project] yields StepResult with clearKeys; runner clears state keys before setStepIndex so next step sees cleared project/codeChange (reprompt).

---

## Impact set summary

- **Registry files:** specs/core-registry.yml, specs/connectors-registry.yml.
- **Asset paths:** src/main/java/com/vinekeepers/connectors/DiscordGateway.java, JdaDiscordGateway.java, DiscordAppReplySink.java; src/main/java/com/vinekeepers/workflow/StepResult.java, ConfigurableWorkflowState.java, ConfigurableWorkflowRunner.java; src/main/java/com/vinekeepers/workflow/steps/BranchStep.java; config/bots.yaml; src/test/... DiscordAppReplySinkTest, ConfigurableWorkflowRunnerTest or StepResultTest/BranchStepTest.
- **removal_or_rename:** none.
