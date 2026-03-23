# Nova-reconcile (common)

**Inputs**: Index, registries, README, code paths.
**Outputs**: Mismatches (if any); applied fixes or raised issues.

**Boundary**: This step must not execute unit tests or re-run the project test command. Treat `run_tests` as the only workflow phase that runs tests; in reconcile, consume existing test results and limit validation to reconcile-specific checks.

## Instructions

1. **No dangling refs**: All index entrypoints exist; all `assets[].path` exist; all validation references point to existing JUnit test classes/methods (or real files).
2. **Traceability consistent**: Requirements ↔ assets ↔ symbols links are consistent.
3. **Spec matches code**: If something in code is not reflected in specs, update specs within existing schema. If a requirement appears unmet, do not delete the requirement; raise an issue.
4. **README matches**: Entrypoints, commands, options, and deprecations in README match code and specs. Update README if outdated (or invoke **nova-update-readme** for a dedicated pass).
5. **Wiki–spec alignment** (when wiki is used): The wiki feature and domain list must match the current spec index and registries. If a registry was removed from specs, the wiki should not create or retain pages for that domain/feature; wiki content (assets, contracts, tests) is derived from specs (specs are source of truth).
