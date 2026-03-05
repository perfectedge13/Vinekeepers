# Nova-commit-push

**Inputs**: Confirmation that all prior steps passed (schema gate, drift gate, tests, plausibility, docs reconcile, static analysis).
**Outputs**: Commit + push executed, or exact commands for the user if VCS unavailable; on failure, report and do not push.

## Instructions

1. **Only run** when all previous phases (schema gate, drift gate, run tests, plausibility review, docs reconcile, static analysis) have passed. If any failed or were blocked, do not run this step; report why commit was skipped.
2. **Commit**: Run `git add -A` then `git commit -m "<message>"`. Compose the message using the formatting and rules in **@.cursor/skills/nova-commit/sub-skills/commit-message.md**: imperative subject (≤72 chars), no workflow/tool prefixes, optional scope or body. Summarize the changes from the run (e.g. "Add schema validation script and index schema", "Fix registry test IDs for schema compliance").
3. **Push**: Run `git push` (or `git push -u origin <branch>` if needed). On failure: STOP and report (e.g. auth, network, protected branch).
4. If VCS is unavailable (e.g. not a git repo, no remote): do not run git commands; return the exact commands for the user to run locally (e.g. "Run: git add -A && git commit -m \"Your message\" && git push").
