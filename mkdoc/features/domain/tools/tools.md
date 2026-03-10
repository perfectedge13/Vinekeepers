# Tool registry and execution

# Status

active

# Summary

Tool registry and execution (REQ-TOOLS-001). Tools are registered by name; ToolRunner executes approved tool calls; Tool interface defines name, argsSchema, execute. Assets: Tool, ToolRegistry, ToolRunner, StubTool.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-TOOL | Tool interface | src/main/java/com/vinekeepers/tools/Tool.java |
| ASSET-TOOL-REGISTRY | Register and resolve tools | src/main/java/com/vinekeepers/tools/ToolRegistry.java |
| ASSET-TOOL-RUNNER | Execute approved tool calls | src/main/java/com/vinekeepers/tools/ToolRunner.java |
| ASSET-CURSOR-FULL-RUN-TOOL | Tool wrapper that launches a Cursor Cloud run from workflow state and stores launch metadata | src/main/java/com/vinekeepers/tools/CursorFullRunTool.java |
| ASSET-ECHO-TOOL | Simple echo tool for workflow and tool runner tests | src/main/java/com/vinekeepers/tools/EchoTool.java |
| ASSET-STUB-TOOL | Stub tool implementation | src/main/java/com/vinekeepers/tools/StubTool.java |

# Sub-pages

- [How it works](tools/how-it-works.md)
- [Change log](tools/change-log.md)
- [Known issues](tools/known-issues.md)
- [Decisions](tools/decisions.md)
- [Contracts](tools/contracts.md)
- [Tests](tools/tests.md)
- [Diagrams](tools/diagrams.md)

