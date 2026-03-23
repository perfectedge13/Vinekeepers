# Run tests (common)

## Summary

Runs the project test command (from specs validation or project.yml); shell-safe invocation; reports Pass/Fail/Blocked and full counts (run, passed, failed, skipped).

## Key points

- Command from specs validation.commands.test or project.yml validation_defaults.test. Shell-safe: no `&&` on PowerShell; use Set-Location or cmd /c.
- Run without quiet flags to get full summary. Extract and report counts (tests run, passed, failed, skipped). Pass = exit 0 all passed; Fail = exit non-zero (report failed class/method); Blocked = raise issue.
- Always print quantity of tests run, passed, failed in step result.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-run-tests (common)

**Inputs**: Project root, adapter config (from specs index `validation.commands.test` and `validation.shell` if present).
**Outputs**: Pass | Fail | Blocked. Counts: tests run, passed, failed, skipped/errors. On Fail: failed test class/method. On Blocked: missing prerequisite; raise issue.

## Instructions

1. Read **@specs/specs.yml** for `validation.commands.test`. If present, use that command; else **@.cursor/project.yml** `validation_defaults.test`; else report "validation not configured" and do not assume a test command.
2. Use **shell-safe** invocation: on Windows PowerShell do not use `&&`. Use `Set-Location <project-root>; <test-command>` or `cmd /c "cd /d <path> && <test-command>"`.
3. Run the test command **without** `-q` or quiet flags so the runner prints a full summary (e.g. Maven Surefire prints "Tests run: X, Failures: Y, Errors: Z, Skipped: W").
4. After the run, **show the full test output** in the agent console (stdout/stderr), including the summary line(s) at the end.
5. **Extract and report counts** from the output: how many tests ran, how many passed, how many failed, how many skipped (or errors). For Maven Surefire: use the "Tests run: N, Failures: F, Errors: E, Skipped: S" line; passed = N - F - E - S. Include these counts in the step result and in the final output.
6. Record: **Pass** (exit 0, all passed), **Fail** (exit non-zero — report failed class/method), or **Blocked** (tests cannot run: missing JUnit/Maven test runtime, no test dir, etc.). **Always print the specific quantity** of tests run, passed, and failed (e.g. "Tests run: 42, Passed: 40, Failed: 2") in the step result, for every outcome (Pass, Fail, or Blocked when a partial run occurred).
7. On Fail: orchestrator will return to implement step. On Blocked: raise a spec issue with the missing prerequisite; do not proceed to commit.
```

</details>


