# Plausibility review

## Summary

Reviews changes for plausibility before commit (e.g. no unintended deletions, scope matches request).

## Key points

- See **.cursor/skills/nova-commit/sub-skills/plausibility-review.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-plausibility-review

**Inputs**: All loaded registry specs (every requirement across all registries).
**Outputs**: Plausibility table — for each requirement: Implemented? (true/false), brief justification. If any active requirement is false, treat as failure.

## Instructions

1. For **every requirement** in every loaded registry, determine whether it is **implemented** based on assets, symbols, tests, and code.
2. Build a table (or list) with: requirement id, title or short label, status (e.g. accepted/deprecated), **Implemented?** (true/false), and a brief justification.
3. If any requirement with **status: accepted** has **Implemented? false**: treat as failure; raise an issue; do not proceed to commit. Deprecated requirements: note only, do not fail the workflow.
4. Output the plausibility table so it can be included in the final report.
```

</details>


