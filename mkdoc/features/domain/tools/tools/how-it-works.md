# How it works

# Overview

`ToolRegistry` resolves tools by name. `ToolRunner` enforces `ToolPolicy`, runs approved tool calls, and returns results. Tools can be invoked by configurable workflow steps or by reasoner proposals, but both paths use the same runner.

# Flow

1. `Bootstrap` registers tools in `ToolRegistry` and wires a shared `ToolRunner`.
2. `CallActionStep` checks `ToolRunner` first when a workflow action id matches a registered tool; otherwise it falls back to the legacy workflow action registry.
3. The engine also executes reasoner-proposed tool calls through the same `ToolRunner`.
4. Results are returned to the caller and recorded through audit events.

# Inputs and outputs

- **Inputs:** Tool id, argument map, and bot tool policy. **Outputs:** Tool result per call or a policy/lookup failure.

