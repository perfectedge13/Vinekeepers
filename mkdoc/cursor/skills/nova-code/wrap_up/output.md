# Output

## Summary

Produces the final report: change summary, files/specs changed, schema/drift results, test results, static analysis, reconcile results, issues raised.

## Key points

- See **.cursor/skills/nova-code/wrap_up/output.md** for full instructions and report format.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-output

**Inputs**: All step results (change summary, files changed, specs changed, schema/drift results, test results, static analysis, reconcile results, issues).
**Outputs**: Formatted final response per rule.

## Instructions

Use the output **format** defined in **@.cursor/skills/nova-code/documentation/output-format.md**. Produce the final report by filling in that format with the step results from this run:

1. **Workflow executed** — List **every** step from the workflow run_order, in order, with status (Pass/Skip/Fail/Not run). If any required step has status **Not run**, include **Workflow incomplete** and list the missing step IDs.
2. **Per-step outcome** — One short line per step that ran: step id, result, and what happened or changed.
3. **Workflow validation** — Checklist (Schema Gate, Drift Gate, Pre-change lock, Post-change schema, Tests with counts, Static analysis, Reconcile, No unresolved issues).
4. **Detail sections** — Include all detail sections listed in output-format.md (summary of change, changed files, specs updated, schema validation results, drift gate result, test results with counts, static analysis, reconcile results, README changes, issues raised).

Populate each section from the step results gathered during the run. The format ensures the report visualizes the workflow that executed and validates that it completed as intended.
```

</details>
