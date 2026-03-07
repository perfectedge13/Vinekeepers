# Output (nova-commit)

## Summary

Returns the final report for the commit workflow (phases run, commit result, summary).

## Key points

- See **.cursor/skills/nova-commit/sub-skills/output.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-commit-output

**Inputs**: All step results (gates, test results with counts, plausibility table, static analysis, README updates, commit/push result).
**Outputs**: Formatted final report for nova-commit.

## Instructions

Return a single response that includes:

- **Schema Gate** — Pass/Fail; if Fail, include Spec Drift Issue (file paths, errors, minimal edits).
- **Drift Gate** — Pass/Fail; if Fail, include Spec Drift Issue.
- **Test results** — Pass/Fail/Blocked; **counts** (tests run, passed, failed, skipped); if Fail include failed test class/method; if Blocked state missing prerequisite.
- **Requirement plausibility table** — For each requirement (or summary): Implemented? true/false, brief justification; note any accepted requirement that is false (and any issue raised).
- **Static analysis** — What was run and result (Pass/Fail).
- **README updates** — Summary of any doc reconciliation changes.
- **Commit + Push** — Yes or No; if Yes, the commit message used and push result; if No, the reason (e.g. gate failed, tests failed, plausibility failure, VCS unavailable, or commands for user to run locally).
```

</details>
