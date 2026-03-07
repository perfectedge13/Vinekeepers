# Contracts

# APIs

No external REST APIs. Tool invocations (e.g. discord.reply, github.comment) are defined per tool and executed by ToolRunner with BotContext.

# Schemas

- **BotConfig (YAML):** botId, persona, model, workflow, stateSchema, tools (allow/deny, approval-required), routing (discord, git/github filters), memory. See ConfigLoader and BotConfig.
- **Event:** sourceId, kind, payload (opaque).
- **ReasonerInput / ReasonerOutput:** event, context, state in; statePatch, proposedActions, nextState out.
- **WorkflowResult&lt;S&gt;:** nextState, actions, done flag.

# Interfaces

- **EventSource:** void start(EventBus bus); connectors implement this.
- **EventSubscriber:** void onEvent(Event e); engine implements this.
- **Tool:** String name(); JsonSchema argsSchema(); ToolResult execute(JsonObject args, BotContext ctx).
- **Reasoner:** ReasonerOutput think(ReasonerInput input).
- **Workflow&lt;S&gt;:** WorkflowResult&lt;S&gt; handle(Event event, BotContext ctx, S state).
- **StateStore:** load(botId, conversationKey), save(botId, conversationKey, state).
- **AuditLog:** record(event, bot, actions, results).
