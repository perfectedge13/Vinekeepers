# Drift gate (common)

## Summary

Validates index and registry integrity: paths exist, refs valid, active requirements have tests. Runs validate-drift when available or performs manual checks per rule 0b.

## Key points

- Run `npm run validate-drift` when available; else perform manual drift checks (index + registries).
- Index: specs[].file, primary_assets, change_triggers, interfaces.cli exist. Registries: assets[].path, traceability, requires, used_by_requirements, validation.tests.
- On Fail: output Spec Drift Issue with exact missing/mismatched paths and minimal edits. Orchestrator STOPs.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-drift-gate (common)

**Inputs**: Index, loaded registries, project root.
**Outputs**: Pass | Fail. On Fail: Spec Drift Issue (exact missing/mismatched refs, minimal spec or repo fix).

## Instructions

1. When **validate-drift** is available: run `npm run validate-drift` from project root. If exit non-zero, return Fail and output Spec Drift Issue (paths, refs, minimal fixes). Orchestrator will STOP.
2. When validate-drift is not yet runnable, perform drift checks per rule 0b:
   - **Index**: every `specs[].file` exists; every `scope.primary_assets[]` exists; every `change_triggers.paths[]` exists; every `interfaces.cli[].command` points to an existing entrypoint or declared alias.
   - **Registries**: every `assets[].path` exists; every `requirements[].traceability.assets[]` points to a declared `assets[].id`; every `assets[].requires[]` and `dependencies.items[].used_by_requirements[]` point to existing requirement ids; every active requirement has at least one test in validation.tests or is exempt per rule guidelines.
3. If any check fails: return Fail, output Spec Drift Issue with exact missing/mismatched paths and minimal edits. Otherwise return Pass.
```

</details>


