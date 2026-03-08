# Contracts

# APIs

Tools expose name, args schema, and execution behavior. There is no external API surface; workflow and reasoner tool calls are mediated through `ToolRunner`.

# Schemas

Tool arguments are validated per tool definition. Runtime execution returns success or failure plus a payload or error result that the caller can interpret.

# Interfaces

- **`Tool`:** defines the tool name, argument schema, and execution contract.
- **`ToolRegistry`:** registers tools and resolves them by name.
- **`ToolRunner`:** enforces `ToolPolicy`, rejects unknown or denied tools, and executes approved calls for both workflow actions and reasoner proposals.
- Built-in examples in the current implementation include `cursor.fullRun` and `echo`.

