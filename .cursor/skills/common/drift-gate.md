# Nova-drift-gate (common)

**Inputs**: Index, loaded registries, project root.
**Outputs**: Pass | Fail. On Fail: Spec Drift Issue (exact missing/mismatched refs, minimal spec or repo fix).

## Instructions

1. When **validate-drift** is available: run `npm run validate-drift` from project root. If exit non-zero, return Fail and output Spec Drift Issue (paths, refs, minimal fixes). Orchestrator will STOP.
2. When validate-drift is not yet runnable, perform drift checks per rule 0b:
   - **Index**: every `specs[].file` exists; every `scope.primary_assets[]` exists; every `change_triggers.paths[]` exists; every `interfaces.cli[].command` points to an existing entrypoint or declared alias.
   - **Registries**: every `assets[].path` exists; every `requirements[].traceability.assets[]` points to a declared `assets[].id`; every `assets[].requires[]` and `dependencies.items[].used_by_requirements[]` point to existing requirement ids; every feature `domain_slug` is declared in `specs/specs.yml` `domains[]` and stored in the mapped registry file; every active requirement has at least one test in validation.tests or is exempt per rule guidelines. During migration, treat legacy `accepted` as an alias for `active`.
3. If any check fails: return Fail, output Spec Drift Issue with exact missing/mismatched paths and minimal edits. Otherwise return Pass.
