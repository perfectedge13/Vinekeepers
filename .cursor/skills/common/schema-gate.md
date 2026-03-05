# Nova-schema-gate (common)

**Inputs**: Project root.
**Outputs**: Pass | Fail. On Fail: Spec Drift Issue (file paths, schema errors, minimal edits).

## Instructions

1. From project root, run: `npm run validate-specs` (or `node scripts/validate-specs.cjs`). Requires Node 18+; run `npm install` first if needed.
2. If `specs/schema/req-registry.schema.json` or index/schema not present: report "Schema Gate skipped (schemas not present)" and return Pass (proceed).
3. If the command exits non-zero or reports validation FAILED: return **Fail**. Output a **Spec Drift Issue** with: file path(s), schema validation error(s), minimal edits needed to restore schema compliance. Orchestrator will STOP.
4. Otherwise return Pass.
