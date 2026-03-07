# Contracts

# APIs

Tools expose name, argsSchema, execute. No REST; tool invocations via ToolRunner.

# Schemas

Tool args per tool (JsonSchema). ToolResult: success, payload or error.

# Interfaces

Tool: name(), argsSchema(), execute(JsonObject, BotContext). ToolRegistry: register, resolve. ToolRunner: run(approved actions).
