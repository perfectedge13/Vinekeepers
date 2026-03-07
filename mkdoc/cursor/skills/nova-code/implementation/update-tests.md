# Update tests

## Summary

Adds or updates unit tests for new/changed behavior from implement; runs the project test command; returns Pass/Fail and list of test file paths.

## Key points

- Use validation test command from specs index or project config.
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
3. Run the test command from **specs/specs.yml** `validation.commands.test` if present, else **@.cursor/project.yml** `validation_defaults.test`; if neither is set, report "validation not configured."
4. Return: **Pass** or **Fail** (one short line); list of new/updated test file paths.
```

</details>
