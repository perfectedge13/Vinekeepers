# Update tests

## Summary

Adds or updates unit tests for new/changed behavior from implement; leaves execution to the workflow's `run_tests` step; returns Pass/Fail and list of test file paths.

## Key points

- `update_tests` edits tests only; `run_tests` executes them later in the workflow.
- See **.cursor/skills/nova-code/implementation/update-tests.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-update-tests

**Inputs:** User request, implement output (changed paths, summary), plan_change optional.

**Outputs:** Pass or Fail (one line); list of new/updated test file paths.

## Instructions

1. From implement output, identify **new or changed behavior** (new paths, modified paths, summary).
2. **Add unit tests** for new behavior; **update tests** for changed behavior.
3. Do **not** run the project test command in this step. The workflow's separate **run_tests** step handles test execution.
4. Return: **Pass** or **Fail** (one short line); list of new/updated test file paths.
```

</details>


