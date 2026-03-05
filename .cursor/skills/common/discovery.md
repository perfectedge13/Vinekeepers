# Nova-spec-discovery (common)

**Inputs**: Index path (default @specs/specs.yml), optional scope (e.g. changed file paths, or "all" for full suite).
**Outputs**: Summary of index, list of loaded registry spec paths, primary_assets, README summary.

## Instructions

1. Read the project's top-level spec index: **@specs/specs.yml**.
2. From the index, load each registry spec listed in `specs[].file` that is relevant to the change — or **all** registries if scope is "all" or no scope (e.g. for nova-commit full verification).
3. Open and summarize in order: (1) top-level spec index, (2) each loaded registry spec, (3) impacted assets / change_triggers / validation files from those specs (or all if scope is all), (4) root **README.md**.
4. Note `scope.primary_assets`, `change_triggers.paths`, and `interfaces.cli` for later steps.
