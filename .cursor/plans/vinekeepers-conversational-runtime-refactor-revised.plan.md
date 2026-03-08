# Vinekeepers Conversational Runtime Refactor (Corrected Plan v2)

## Goals and constraints

- Transform from configurable scripted workflow runner to a bot runtime with multi-turn conversation, structured per-session state, reasoner-in-the-loop (validated), unified tool execution with policy, and more generic event routing.
- Preserve existing code and naming where reasonable; incremental, compile-safe refactors; add tests; keep simple single-event workflows; Java-first, typed domain objects; YAML support with config that describes capability.

---

## Phase ordering (required)

Unified tool execution **must** complete before the reasoner can propose tool calls. No reasoner-driven tool execution until all workflow-triggered actions go through ToolRunner.

| Phase | Focus |
|-------|--------|
| 1 | Extend workflow state with conversational/runtime metadata only |
| 2 | Add WAITING step outcome and introduce WorkflowRunResult (mandatory) |
| 3 | Add conversational step semantics (prompt_for_field / capture_field) |
| 4 | Add session key strategy and session isolation |
| 5 | Unify tool execution path through ToolRunner + ToolPolicy |
| 6 | Integrate reasoner into control flow |
| 7 | Add normalized event context adapter and router cleanup |
| 8 | Config and Bootstrap cleanup |
| 9 | Tests |

---

## Phase 1: Extend workflow state (lightweight, first pass only)

### 1.1 Extend ConfigurableWorkflowState only — no new persistence abstraction

Extend [ConfigurableWorkflowState](src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowState.java) with the **minimum** fields needed for conversational/runtime behavior:

- `status` — e.g. `ACTIVE`, `WAITING_INPUT`, `COMPLETED`
- `waitingForField` — optional; set when workflow is paused for user input
- `pendingPrompt` — optional; last prompt sent (audit/debug)
- `stepIndex` — already present
- `updatedAt` — optional long (millis); for future TTL hooks only; no eviction in this refactor

**Do not** introduce a new ConversationSession persistence abstraction in this pass. StateStore continues to key by string (session key); value remains ConfigurableWorkflowState. A future ConversationSession wrapper may be added later if needed; it is not mandatory here.

---

## Phase 2: WAITING step outcome and WorkflowRunResult (mandatory)

### 2.1 Step outcome

- **StepResult**: Add `StepOutcome` enum: `CONTINUE`, `WAITING`, `COMPLETE`, `ERROR`. Add optional `waitingForField`, `promptMessage`. Factory `StepResult.waiting(String promptMessage, String waitingForField)`.

### 2.2 WorkflowRunResult — mandatory, not optional

Introduce **WorkflowRunResult** as soon as WAITING/conversational behavior exists. The runner contract **must** stop being raw `String` once waiting/completed semantics matter.

**Minimal shape:**

- `replyMessage` (String)
- `waiting` (boolean)
- `completed` (boolean)
- `waitingForField` (String, optional)
- `errorMessage` (String, optional)

Engine continues to send `replyMessage` as today; the object is the single return type for WorkflowRunner.

- **WorkflowRunner** interface: Return type changes from `String` to **WorkflowRunResult**. Engine and all callers use `result.getReplyMessage()` for reply and `result.isWaiting()` / `result.isCompleted()` as needed.

### 2.3 Runner loop and WAITING (pointer advance rule)

In [ConfigurableWorkflowRunner](src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java):

- When a step returns outcome **WAITING**:
  - Update state: `status = WAITING_INPUT`, `waitingForField`, `pendingPrompt`.
  - **Advance the persisted step pointer by one** so that the stored `stepIndex` points to the **next** step (the capture step). This is mandatory for prompt/capture flows so that on resume we do **not** re-run the prompt step.
  - Persist state; return `WorkflowRunResult` with `waiting=true`, `replyMessage=promptMessage`, `waitingForField` set.
- When outcome is **CONTINUE** or **COMPLETE**: advance step (or finish), persist, return result with `waiting=false`, `completed` as appropriate.

**Resume semantics (mandatory, non-negotiable):**

- On the next event for the same session, the runner loads state and executes the step at the **current** `stepIndex`. Because that index was advanced when entering WAITING, execution **resumes at the capture step**, not the prompt step.
- **Rejected:** Any design that “reruns the same stepIndex” after WAITING for prompt/capture conversational flows. That would cause repeated prompting or hidden branching and is explicitly out of scope.

---

## Phase 3: Conversational step semantics (prompt_for_field / capture_field)

### 3.1 PromptForFieldStep

- If state **already** has a value for `storeIn`: return CONTINUE and advance to next step (skip prompting).
- If state **does not** have value for `storeIn`:
  - Return **WAITING** with `promptMessage = prompt`, `waitingForField = storeIn`.
  - The runner (Phase 2) will set state (`status`, `waitingForField`, `pendingPrompt`) and **advance the persisted step pointer by one** so the next step is CaptureFieldFromEventStep.
  - Persist and return; on next event the runner will **not** re-execute this step.

### 3.2 CaptureFieldFromEventStep

- Read event content (e.g. `content` / `text`) and store in `storeIn`.
- Clear `waitingForField` and `pendingPrompt`; set `status = ACTIVE`.
- Return CONTINUE and advance to next step.

### 3.3 Workflow DSL

- New step types: `prompt_for_field` (prompt, storeIn), `capture_field` (storeIn, optional contentKey). Workflow definition order: … → prompt_for_field → capture_field → … so that when WAITING is returned and the pointer is advanced, the stored index points to capture_field.
- Existing `ask_input` remains for legacy single-event flows (see Phase 8).

### 3.4 Backward compatibility (conversationMode)

- **conversationMode**: `single_event` | `conversational`.
- **single_event** (default): Existing `ask_input` behavior only; read from current event, advance, no WAITING; no prompt/capture pair required.
- **conversational**: Use prompt_for_field and capture_field; WAITING and pointer-advance semantics apply.
- Legacy workflows continue to run without change when conversationMode is absent or single_event.

---

## Phase 4: Session key strategy and session isolation

### 4.1 SessionKeyStrategy

- **SessionKeyStrategy** interface: `String resolveSessionKey(String botId, Event event)`.
- Implementations:
  - **ChannelOnlyKeyStrategy**: `bot:botId:conv:channelId` (legacy).
  - **ChannelAndUserKeyStrategy**: `bot:botId:conv:channelId:userId` so two users in the same channel do not share state.
  - **ThreadKeyStrategy**: use threadId when present, else channelId + userId.

### 4.2 Integration

- [BotDefinition](src/main/java/com/vinekeepers/bot/BotDefinition.java): Optional `sessionKeyStrategy` (e.g. `"channel"`, `"channel_user"`, `"thread"`). Default for Discord: `channel_user` when available to avoid collision.
- [ConfigurableWorkflowRunner](src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java): Use SessionKeyStrategy to compute session key; replace inline `stateKey(botId, event)` with `strategy.resolveSessionKey(botId, event)`. Strategy reads from Event payload; connectors unchanged.
- ConfigLoader: Parse `sessionKeyStrategy` from bot YAML within existing structure.

---

## Phase 5: Unify tool execution (primary path through ToolRunner)

### 5.1 ToolRunner as primary; WorkflowActionRegistry as legacy only

**Rule: Workflow-triggered actions go through ToolRunner first.** WorkflowActionRegistry is **legacy/adapter** behavior for migration only, not a parallel first-class action path.

- **CallActionStep** must:
  1. Check **ToolRegistry** for `actionId`.
  2. If a tool exists: build args from state + bind; execute via **ToolRunner.run(actionId, args, botToolPolicy)**. ToolPolicy enforcement happens inside ToolRunner. Use the return value; do **not** call WorkflowActionRegistry for that id.
  3. If no tool exists: **Fallback** to **WorkflowActionRegistry** only for legacy compatibility during migration.

CallActionStep receives ToolRunner (or ToolRegistry + ToolRunner), WorkflowActionRegistry, and the bot’s ToolPolicy. Bootstrap creates ToolRegistry and ToolRunner and passes them into the runner factory / ConfigurableWorkflowRunner.

### 5.2 Bootstrap and tool registration

- Bootstrap instantiates ToolRegistry and ToolRunner. Registers tools (e.g. CursorCloudTool, EchoTool) **in ToolRegistry**. Side-effect actions are Tools; CallActionStep uses ToolRunner first, then fallback to WorkflowActionRegistry only when no tool is registered for that id.

---

## Phase 6: Reasoner integration (limited role)

### 6.1 Principle: "LLM proposes, Vinekeepers validates and executes"

- Workflow and engine state remain **authoritative**. The reasoner does **not** become an uncontrolled control plane.
- **Tool execution** for reasoner-proposed calls **must always** go through **ToolRunner** and policy checks. No separate or bypass path.

### 6.2 ReasonerOutput and ReasonerInput

- **ReasonerOutput** may include: `statePatch`, `nextQuestion`, `missingFields`, `proposedToolCalls`, `replyText`. Engine validates and applies; for each proposedToolCall the engine invokes **ToolRunner.run(...)** with the bot’s ToolPolicy.
- **ReasonerInput**: Add optional `currentState`, `lastUserMessage`, and/or minimal context so the reasoner can propose patches and next question.

### 6.3 Engine integration

- After workflow run: load session state; build ReasonerInput; call reasoner. Apply **statePatch** only after validation. For **proposedToolCalls**: invoke **ToolRunner** only (same path as workflow). Use **nextQuestion** / **replyText** for reply or pending prompt. StubReasoner returns empty patches and no tool calls.

---

## Phase 7: Normalized event context adapter and router cleanup

### 7.1 Adapter-first; no connector rewrite

- **NormalizedEventContext**: Read-only adapter/view built from Event. Fields: sourceType, eventType, actorId, channelId, threadId, conversationId, text/content, repo, labels, metadata.
- **NormalizedEventContext.from(Event)** — static factory that builds context from the existing Event payload. **Connectors continue to emit Event unchanged**; no changes to event publishers in this phase.
- [Router](src/main/java/com/vinekeepers/bot/Router.java): Build context via `NormalizedEventContext.from(event)`; evaluate **RoutingFilter** against context fields (e.g. getChannelId(), getActorId()) instead of raw payload. RoutingFilter keeps existing Discord/GitHub sets; only the source of values changes. Minimal blast radius; no large routing or connector redesign.

---

## Phase 8: Config and Bootstrap cleanup

### 8.1 Bot config

- [BotDefinition](src/main/java/com/vinekeepers/bot/BotDefinition.java): Optional `sessionKeyStrategy`; **conversationMode** (`single_event` | `conversational`), default `single_event`.
- [ConfigLoader](src/main/java/com/vinekeepers/config/ConfigLoader.java): Parse conversationMode and sessionKeyStrategy from bot YAML within existing schema/keys.

### 8.2 Bootstrap

- Remove hardcoded cursor lambda from Bootstrap; register tools in ToolRegistry (Phase 5). WorkflowActionRegistry remains only for legacy fallback. No bot-specific branching beyond registering tools and runner type.

### 8.3 Backward compatibility

- Existing YAML with `ask_input` and conversationMode absent or `single_event`: single event completes workflow, no WAITING, no prompt/capture pair. New conversational flows use prompt_for_field + capture_field and conversationMode `conversational`.

---

## Phase 9: Tests

### 9.1 Correct test expectations (resume and pointer)

After **PromptForFieldStep** returns WAITING:

- Session/workflow state has `status = WAITING_INPUT`.
- `waitingForField` is set.
- **Persisted step pointer (stepIndex) points to the capture step, not the prompt step.** Tests must assert this explicitly.

On the next event:

- Runner executes the step at the stored stepIndex → **CaptureFieldFromEventStep** runs once; **PromptForFieldStep** is not run again.

### 9.2 Required tests

- **Conversational pause/resume**: Workflow prompt_for_field → capture_field → done. First event (no value): reply is prompt, state WAITING_INPUT, waitingForField set, **stepIndex points to capture step**. Second event (with content): capture step runs, stores value, continues to done; reply is done message.
- **Prompt does not loop forever on resume**: After first event (WAITING), second event runs **CaptureFieldFromEventStep** only; **PromptForFieldStep** is not executed again. Assert stepIndex and that prompt text is not sent again.
- **Two users in same Discord channel do not share state**: Two events, different authorIds, same channelId; assert two distinct session keys (e.g. channel_user) and that each user’s state is isolated.
- **Tool policy enforcement through workflow calls**: Workflow calls a tool; bot’s ToolPolicy denies that tool; assert execution fails (e.g. SecurityException or error result) and no side effect.
- **Reasoner state patch merge**: Reasoner returns statePatch; engine applies after validation; assert state updated.
- **Backward compatibility for single-event workflows**: Single-event workflow (ask_input + done in one event) still passes with conversationMode single_event or absent; no WAITING.

### 9.3 Explicit regression tests

- **A. Prompt step does not loop on resume** — resume runs capture step only; prompt step is not re-executed.
- **B. Two users same channel isolation** — distinct session keys and isolated state.

---

## Summary of corrections

- **Phase order**: Tool unification (Phase 5) before reasoner integration (Phase 6).
- **Resume semantics**: When WAITING, advance persisted step pointer to the **next** step (capture step). On resume, execution starts at the capture step. **Reject** “rerun same stepIndex” for prompt/capture flows.
- **Session model**: First pass extends ConfigurableWorkflowState only; no mandatory ConversationSession persistence.
- **WorkflowRunResult**: Mandatory once WAITING exists; minimal shape (replyMessage, waiting, completed, waitingForField, optional errorMessage); runner contract is never raw String for conversational behavior.
- **Tool unification**: ToolRunner is the primary path; WorkflowActionRegistry is legacy/adapter only.
- **Reasoner**: Limited; LLM proposes, Vinekeepers validates and executes; tools always through ToolRunner + policy.
- **Router**: NormalizedEventContext as read-only adapter from Event; connectors unchanged.
- **Backward compat**: conversationMode single_event | conversational; legacy ask_input supported; new flows use prompt_for_field + capture_field.
- **Tests**: Assert step pointer points to capture step after WAITING; explicit tests for no prompt loop, same-channel isolation, tool policy, reasoner patch, single-event compat.

---

## Deferred / out of scope

- TTL eviction, long-term memory, transcript summarization: do not add; only minimal state fields.
- Full ConversationSession abstraction: optional later.
- Connector changes: event normalization is read-only from Event only.
- Rich confirmation flow: can be stubbed in ReasonerOutput for later.

---

## Spec and guardrails

- Follow spec-workflow-core: Schema Gate and Spec Drift Gate; no new spec keys; update traceability/acceptance within existing schema. PowerShell for commands; mvn test and mvn compile.
- Do not delete requirements.

---

## Deliverables (post-execution)

- Refactored code per phases 1–9.
- Updated and new tests with correct expectations (pointer at capture step after WAITING; no prompt loop; same-channel isolation; tool policy; reasoner patch; single-event compat).
- Brief markdown summary: what changed, what was deferred, how to define a conversational bot (YAML + conversationMode + prompt_for_field/capture_field), migration notes for existing config/workflows.
