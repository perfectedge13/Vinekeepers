# Nova-implement (ImplementBot)

**Inputs**: User request, plan_change output (impacted specs/assets), handoff from previous steps.
**Outputs**: Code/source changes applied; Pass or Fail; one short line describing what was done or changed; **list of changed file paths** (paths added, modified, or deleted — **production/source code only**).

Implement = **production code only**. Tests are added or updated by the **update_tests** step, and executed later by **run_tests**; implement only changes production/source code, **must not run tests during this phase**, and returns the list of changed source file paths.

## Instructions

1. **Scope** — Implement the user request. Keep scope to impacted specs and assets (from plan_change) unless the change inherently affects shared interfaces.
2. **Anti-patterns** — Before and during implementation, read **anti_patterns** on impacted requirements and assets in the registry specs (see **@.cursor/rules/guardrails.mdc**); avoid those approaches.
3. **Apply the change** — Make the requested code or file changes. Do not delete requirements or remove required functionality; do not change schema files or invent new spec keys. **Do not add or update unit tests, and do not run tests during this phase** — test edits happen in **update_tests** and execution happens in **run_tests**.
4. **Return** — Pass or Fail; one short line describing what you implemented or what changed (e.g. "Added X; modified Y."); and a **list of changed source file paths** (paths added, modified, or deleted) so downstream steps (update_tests, mk) can map changes to features.
