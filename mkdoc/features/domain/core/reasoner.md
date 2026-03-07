# Reasoner interface for bot decisions

# Status

active

# Summary

Reasoner interface for bot decisions (REQ-REASONER-001). Reasoner.think(ReasonerInput) returns ReasonerOutput (statePatch, proposedActions, nextState); can be LLM-backed or rules-based. Assets: Reasoner, ReasonerInput, ReasonerOutput, StubReasoner.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-REASONER | Reasoner interface for bot decisions | src/main/java/com/vinekeepers/reasoner/Reasoner.java |
| ASSET-REASONER-INPUT | Reasoner input model | src/main/java/com/vinekeepers/reasoner/ReasonerInput.java |
| ASSET-REASONER-OUTPUT | Reasoner output model | src/main/java/com/vinekeepers/reasoner/ReasonerOutput.java |
| ASSET-STUB-REASONER | Stub reasoner implementation | src/main/java/com/vinekeepers/reasoner/StubReasoner.java |

# Sub-pages

- [How it works](reasoner/how-it-works.md)
- [Change log](reasoner/change-log.md)
- [Known issues](reasoner/known-issues.md)
- [Decisions](reasoner/decisions.md)
- [Contracts](reasoner/contracts.md)
- [Tests](reasoner/tests.md)
- [Diagrams](reasoner/diagrams.md)
