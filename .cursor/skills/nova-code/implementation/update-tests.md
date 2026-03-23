# Nova-update-tests

**Inputs:** User request, implement output (changed paths, summary), plan_change optional.

**Outputs:** Pass or Fail (one line); list of new/updated test file paths.

## Instructions

1. From implement output, identify **new or changed behavior** (new paths, modified paths, summary).
2. **Add unit tests** for new behavior; **update tests** for changed behavior.
3. Do **not** run the project test command in this step. The workflow's separate **run_tests** step handles test execution.
4. Return: **Pass** or **Fail** (one short line); list of new/updated test file paths.
