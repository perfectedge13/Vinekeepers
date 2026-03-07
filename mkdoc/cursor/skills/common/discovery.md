# Discovery (common)

## Summary

Reads the project spec index and loads relevant registry specs; summarizes index, registries, primary_assets, change_triggers, and README for later steps.

## Key points

- Index path from project.yml `paths.specs_index` or `specs/specs.yml`; optional scope (e.g. "all" for full suite).
- Load each registry in `specs[].file` relevant to the change (or all if scope is all).
- Summarize: index, each loaded registry, impacted assets/change_triggers/validation, root README.
- Note primary_assets, change_triggers.paths, interfaces.cli for later steps.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-spec-discovery (common)

**Inputs**: Index path from **@.cursor/project.yml** `paths.specs_index` if present, else `specs/specs.yml`. Optional scope (e.g. changed file paths, or "all" for full suite).
**Outputs**: Summary of index, list of loaded registry spec paths, primary_assets, README summary.

## Instructions

1. Read the project's top-level spec index: path from **@.cursor/project.yml** `paths.specs_index` if present, else **@specs/specs.yml**. If the index file is absent, still produce a short summary: state that the index is missing, list main entrypoints/layout from README or src/, and note that **update_specs** will need to bootstrap the index and at least one registry.
2. From the index, load each registry spec listed in `specs[].file` that is relevant to the change — or **all** registries if scope is "all" or no scope (e.g. for nova-commit full verification).
3. Open and summarize in order: (1) top-level spec index, (2) each loaded registry spec, (3) impacted assets / change_triggers / validation files from those specs (or all if scope is all), (4) root **README.md**.
4. Note `scope.primary_assets`, `change_triggers.paths`, and `interfaces.cli` for later steps.
```

</details>
