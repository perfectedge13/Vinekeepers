# Nova-removal-rename

**Inputs**: Path(s) to remove or rename, index, registries, README.
**Outputs**: Reference map applied; repo and specs updated; gates re-run; verification done.

## Instructions

1. **Reference map** — Identify every reference to the path(s) across: spec index (`primary_assets`, `change_triggers`, `interfaces.cli.command`), registry specs (`assets[].path`, `validation.tests`, traceability), README and docs.
2. **Apply removal/rename** — Remove or rename the file(s) in the repo; update all discovered references in specs and docs. Do not add new keys (e.g. shared.removed_assets).
3. **Re-run gates** — Run `npm run validate-specs` and `npm run validate-drift`. If either fails, STOP and output Spec Drift Issue.
4. **Verify** — Ensure no validation references still point to the removed/renamed path; ensure index entrypoints still exist or are updated to a declared alias; update README.md as needed.
