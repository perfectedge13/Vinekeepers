# Specs governance and bootstrap

# Status

active

# Summary

Specs bootstrap and traceability (REQ-CORE-001) and application bootstrap and entrypoint (REQ-CORE-002). `specs/specs.yml` and `specs/core-registry.yml` anchor traceability, while `VinekeepersApp` loads `.env` and hands startup to `Bootstrap`, which wires the event bus, router, state store, tool execution, connectors, and configured bot runners.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-SPEC-INDEX | Spec index and validation entrypoint | specs/specs.yml |
| ASSET-REGISTRY | Core requirements registry | specs/core-registry.yml |
| ASSET-APP | Application entrypoint; loads `.env` and bootstraps runtime wiring | src/main/java/com/vinekeepers/VinekeepersApp.java |
| ASSET-BOOTSTRAP | Wire engine, config loader, connectors, shared tools, and configured workflow runners | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-PACKAGE-MARKER | Package marker utility | src/main/java/com/vinekeepers/util/PackageMarker.java |

# Sub-pages

- [How it works](core/how-it-works.md)
- [Change log](core/change-log.md)
- [Known issues](core/known-issues.md)
- [Decisions](core/decisions.md)
- [Contracts](core/contracts.md)
- [Tests](core/tests.md)
- [Diagrams](core/diagrams.md)

