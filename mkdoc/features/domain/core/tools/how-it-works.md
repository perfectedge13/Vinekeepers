# How it works

# Overview

ToolRegistry resolves tools by name. ToolRunner runs approved actions (after ToolPolicy) and returns results. Tool: name(), argsSchema(), execute(args, context).

# Flow

1. Reasoner proposes actions; ToolPolicy filters.
2. ToolRunner looks up each tool by name and executes with args.
3. Results returned to engine for audit and response.

# Inputs and outputs

- **Inputs:** Tool name, args, BotContext. **Outputs:** ToolResult per call.
