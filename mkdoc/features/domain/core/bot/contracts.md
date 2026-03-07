# Contracts

# APIs

None. Router.match(Event) → List of BotDefinition. ToolPolicy enforces per bot.

# Schemas

BotDefinition: botId, persona, model, workflow, toolPolicy, routing, memoryPolicy. Routing: discord/git filters. ToolPolicy: allow, deny, approvalRequired.

# Interfaces

Router: match(Event). EventFilter, RoutingFilter. ToolPolicy. BotDefinition, Persona, ModelProfile, MemoryPolicy records.
