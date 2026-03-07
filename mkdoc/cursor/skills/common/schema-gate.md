# Schema gate (common)

## Summary

Runs schema validation (e.g. npm run validate-specs) on the spec index and registries. Pass/Fail; on Fail outputs Spec Drift Issue and orchestrator STOPs.

## Key points

- Run from project root: npm run validate-specs (or node scripts/validate-specs.cjs). Node 18+; npm install first if needed.
- If schemas not present: report "Schema Gate skipped" and return Pass. If command exits non-zero or reports FAILED: return Fail with Spec Drift Issue (paths, errors, minimal edits).

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-schema-gate (common)

**Inputs**: Project root.
**Outputs**: Pass | Fail. On Fail: Spec Drift Issue (file paths, schema errors, minimal edits).

## Instructions

1. From project root, run: `npm run validate-specs` (or `node scripts/validate-specs.cjs`). Requires Node 18+; run `npm install` first if needed.
2. If `specs/schema/req-registry.schema.json` or index/schema not present: report "Schema Gate skipped (schemas not present)" and return Pass (proceed).
3. If the command exits non-zero or reports validation FAILED: return **Fail**. Output a **Spec Drift Issue** with: file path(s), schema validation error(s), minimal edits needed to restore schema compliance. Orchestrator will STOP.
4. Otherwise return Pass.
```

</details>
