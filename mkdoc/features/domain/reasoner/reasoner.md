# Reasoner interface for bot decisions

# Status

active

# Summary

Reasoner interface for bot decisions (REQ-REASONER-001). `Reasoner.reason(ReasonerInput)` receives workflow context, current state, and the last normalized user message, then returns `ReasonerOutput` with reply text, optional `OutboundResponse` richReply, state patches, and proposed tool calls. The engine can persist the patch, execute approved proposed tools through `ToolRunner`, and use the resulting reply when the workflow stays silent.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-REASONER | Reasoner interface for bot decisions | src/main/java/com/vinekeepers/reasoner/Reasoner.java |
| ASSET-REASONER-INPUT | Reasoner input model | src/main/java/com/vinekeepers/reasoner/ReasonerInput.java |
| ASSET-REASONER-OUTPUT | Reasoner output model including reply text, optional OutboundResponse richReply, state patch, and proposed tool calls | src/main/java/com/vinekeepers/reasoner/ReasonerOutput.java |
| ASSET-PROPOSED-TOOL-CALL | Proposed tool call emitted by a reasoner | src/main/java/com/vinekeepers/reasoner/ProposedToolCall.java |
| ASSET-STUB-REASONER | Stub reasoner implementation | src/main/java/com/vinekeepers/reasoner/StubReasoner.java |

# Sub-pages

- [How it works](reasoner/how-it-works.md)
- [Change log](reasoner/change-log.md)
- [Known issues](reasoner/known-issues.md)
- [Decisions](reasoner/decisions.md)
- [Contracts](reasoner/contracts.md)
- [Tests](reasoner/tests.md)
- [Diagrams](reasoner/diagrams.md)

