# Core engine, bootstrap, and specs

# Status

active

# Summary

Specs bootstrap and traceability (REQ-CORE-001), application bootstrap and entrypoint (REQ-CORE-002), and event-driven engine (REQ-CORE-003). Assets: spec index, registry, VinekeepersApp, Bootstrap, VinekeepersEngine, PackageMarker.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-SPEC-INDEX | Spec index and validation entrypoint | specs/specs.yml |
| ASSET-REGISTRY | Core requirements registry | specs/core-registry.yml |
| ASSET-APP | Application entrypoint; loads .env and bootstraps engine | src/main/java/com/vinekeepers/VinekeepersApp.java |
| ASSET-BOOTSTRAP | Wire engine, config, connectors; create WorkflowRunner per bot via WorkflowRunnerFactory, registerRunner(botId, runner) | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-ENGINE | Route events to bots; run core loop using registered WorkflowRunners (runner.run loads state, runs workflow, persists, responds) | src/main/java/com/vinekeepers/core/VinekeepersEngine.java |
| ASSET-WORKFLOW-RUNNER-FACTORY | Create WorkflowRunner from workflow.type and params from config | src/main/java/com/vinekeepers/workflow/WorkflowRunnerFactory.java |
| ASSET-PACKAGE-MARKER | Package marker utility | src/main/java/com/vinekeepers/util/PackageMarker.java |

# Sub-pages

- [How it works](core/how-it-works.md)
- [Change log](core/change-log.md)
- [Known issues](core/known-issues.md)
- [Decisions](core/decisions.md)
- [Contracts](core/contracts.md)
- [Tests](core/tests.md)
- [Diagrams](core/diagrams.md)
