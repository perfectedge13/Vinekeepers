# Specs governance and bootstrap

# Status

active

# Summary

Spec metadata anchors traceability, and VinekeepersApp plus Bootstrap wire the runtime before events begin flowing.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-SPEC-INDEX | Spec index and validation entrypoint | specs/specs.yml |
| ASSET-REGISTRY | Core requirements registry | specs/core-registry.yml |
| ASSET-APP | Application entrypoint; loads .env and bootstraps engine | src/main/java/com/vinekeepers/VinekeepersApp.java |
| ASSET-BOOTSTRAP | Wire engine, config, ConnectorRegistry; build Discord adapter; registerBots; action and sink registration | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-PACKAGE-MARKER | Package marker utility | src/main/java/com/vinekeepers/util/PackageMarker.java |

# Sub-pages

- [How it works](core/how-it-works.md)
- [Change log](core/change-log.md)
- [Known issues](core/known-issues.md)
- [Decisions](core/decisions.md)
- [Contracts](core/contracts.md)
- [Tests](core/tests.md)
- [Diagrams](core/diagrams.md)
