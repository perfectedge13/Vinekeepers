# Nova-implement (ImplementBot)

**Inputs**: User request, plan_change output (impacted specs/assets), handoff from previous steps.
**Outputs**: Code/source changes applied; Pass or Fail; one short line describing what was done or changed; **list of changed file paths** (paths added, modified, or deleted).

## Instructions

1. **Scope** — Implement the user request. Keep scope to impacted specs and assets (from plan_change) unless the change inherently affects shared interfaces.
2. **Anti-patterns** — Before and during implementation, read **anti_patterns** on impacted requirements and assets in the registry specs (see **@.cursor/skills/common/guardrails.md**); avoid those approaches.
3. **Apply the change** — Make the requested code or file changes. Do not delete requirements or remove required functionality; do not change schema files or invent new spec keys.
4. **Return** — Pass or Fail; one short line describing what you implemented or what changed (e.g. "Added X; modified Y."); and a **list of changed file paths** (paths added, modified, or deleted) so downstream steps (e.g. wiki) can map changes to features.
