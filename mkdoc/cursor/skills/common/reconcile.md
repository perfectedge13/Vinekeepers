# Reconcile (common)

## Summary

Ensures no dangling refs, traceability consistent, spec matches code, README matches; when wiki is used, wiki–spec alignment. Apply fixes or raise issues.

## Key points

- No dangling refs: index entrypoints, assets[].path, validation refs exist. Traceability: requirements ↔ assets ↔ symbols consistent.
- Spec matches code; do not delete requirements—raise issue. README matches entrypoints, commands, options. Wiki feature/domain list matches spec index and registries.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-reconcile (common)

**Inputs**: Index, registries, README, code paths.
**Outputs**: Mismatches (if any); applied fixes or raised issues.

## Instructions

1. **No dangling refs**: All index entrypoints exist; all `assets[].path` exist; all validation references point to existing TestNG test classes/methods (or real files).
2. **Traceability consistent**: Requirements ↔ assets ↔ symbols links are consistent.
3. **Spec matches code**: If something in code is not reflected in specs, update specs within existing schema. If a requirement appears unmet, do not delete the requirement; raise an issue.
4. **README matches**: Entrypoints, commands, options, and deprecations in README match code and specs. Update README if outdated (or invoke **nova-update-readme** for a dedicated pass).
5. **Wiki–spec alignment** (when wiki is used): The wiki feature and domain list must match the current spec index and registries. If a registry was removed from specs, the wiki should not create or retain pages for that domain/feature; wiki content (assets, contracts, tests) is derived from specs (specs are source of truth).
```

</details>


