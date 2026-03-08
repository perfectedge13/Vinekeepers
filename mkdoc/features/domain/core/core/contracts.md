# Contracts

# APIs

No external REST APIs. Tool invocations are defined per tool and executed by `ToolRunner`.

# Schemas

- **BotConfig (YAML):** bot definitions, routing rules, workflow config, and runtime options such as `conversationMode` and `sessionKeyStrategy`. See `ConfigLoader` and `BotConfig`.
- **Event:** sourceId, kind, payload (opaque).
- **ReasonerInput / ReasonerOutput:** event, workflow context, current state, last user message in; reply text, state patch, and proposed tool calls out.
- **WorkflowResult&lt;S&gt; / WorkflowRunResult:** legacy workflow state transitions plus engine-facing workflow outcome metadata.

# Interfaces

- **EventSource:** void start(EventBus bus); connectors implement this.
- **EventSubscriber:** void onEvent(Event e); engine implements this.
- **Tool:** named tool contract executed through `ToolRunner`.
- **Reasoner:** `ReasonerOutput reason(ReasonerInput input)`.
- **Workflow&lt;S&gt;:** WorkflowResult&lt;S&gt; handle(Event event, BotContext ctx, S state).
- **StateStore:** session-scoped get/put for persisted workflow state.
- **AuditLog:** record(event, bot, actions, results).

