# Nova Commit

## Summary

Runs the full **verification and commit** workflow: discovery, schema/drift gates, run_tests, plausibility_review, docs_reconcile, static_analysis, commit_push, output. Do not commit until all prior phases pass.

## Key points

- Workflow defined in **nova-commit.yml**; execute every phase in order.
- Use each phase's **location** for steps; stop on gate or step failure.
- Project rule **spec-workflow.mdc** is source of truth for gate semantics.

## Sub-skills

- [sub-skills/plausibility-review](nova-commit/sub-skills/plausibility-review.md)
- [sub-skills/commit-message](nova-commit/sub-skills/commit-message.md)
- [sub-skills/commit-push](nova-commit/sub-skills/commit-push.md)
- [sub-skills/output](nova-commit/sub-skills/output.md)

## Skill source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
---
name: nova-commit
description: Orchestrator for full-suite verification and commit. Follows .cursor/workflows/nova-commit.yml; reuses common sub-skills; adds plausibility review and commit/push. Use when the user requests nova-commit or full verification before commit.
disable-model-invocation: true
---

# Nova Commit (Orchestrator)

This skill runs the full **verification and commit** workflow for the project.

**Workflow** — The flow is defined in **@.cursor/workflows/nova-commit.yml**. The project rule **@.cursor/rules/spec-workflow.mdc** is the source of truth for gate semantics.

**How to run** — Read the workflow file. Then follow **@.cursor/skills/common/README.md** ("How to execute a workflow"). Execute phases in order; use each phase's **location** for steps. Do not run commit/push until all prior phases have passed — if any gate or step fails or is blocked, stop and report; do not commit.

**Mandatory:** Run every phase in order. Do not skip any phase. The workflow has 9 phases: discovery, schema_gate, drift_gate, run_tests, plausibility_review, docs_reconcile, static_analysis, commit_push, output. Run each one before proceeding to the next (stop on gate/step failure; do not commit if any prior phase failed).

Do not duplicate the step list here; the workflow and the files at each `location` contain the full instructions.
```

</details>
