# Event-driven engine orchestration

# Status

active

# Summary

Event-driven engine routes events to bots (REQ-CORE-003). `VinekeepersEngine` receives events, routes them to matching bots, runs the registered `WorkflowRunner`, builds `ReasonerInput` from workflow context and session state, applies `ReasonerOutput` patches and proposed tool calls, records audit activity, and emits replies when a connector supports them.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-ENGINE | Route events to bots; run workflow and reasoner; apply tool/state side effects; deliver replies to Discord | src/main/java/com/vinekeepers/core/VinekeepersEngine.java |
| ASSET-WORKFLOW-RUNNER | Interface to run workflow for an event and return a structured workflow result | src/main/java/com/vinekeepers/workflow/WorkflowRunner.java |
| ASSET-TOOL-RUNNER | Execute approved tool calls for workflows and reasoners under `ToolPolicy` | src/main/java/com/vinekeepers/tools/ToolRunner.java |
| ASSET-REASONER-INPUT | Reasoner input model including workflow context, current state, and last user message | src/main/java/com/vinekeepers/reasoner/ReasonerInput.java |
| ASSET-REASONER-OUTPUT | Reasoner output model including reply text, state patch, and proposed tool calls | src/main/java/com/vinekeepers/reasoner/ReasonerOutput.java |

# Sub-pages

- [How it works](engine/how-it-works.md)
- [Change log](engine/change-log.md)
- [Known issues](engine/known-issues.md)
- [Decisions](engine/decisions.md)
- [Contracts](engine/contracts.md)
- [Tests](engine/tests.md)
- [Diagrams](engine/diagrams.md)

