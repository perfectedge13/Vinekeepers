# Nova PR

## Summary

Creates or updates a **pull request** for the current branch. PR body is set to the last nova-code run report. Single step: create_pr.

## Key points

- Workflow in **nova-pr.yml**; open **create-pr.md** and follow it.
- Prerequisites: branch pushed; `.cursor/workflows/reports/nova-code-output-report.md` from a prior nova-code run; GitHub CLI (`gh`) when available.

## Sub-skills

- [sub-skills/create-pr](nova-pr/sub-skills/create-pr.md)

## Skill source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
---
name: nova-pr
description: Create a pull request for the current (pushed) branch with the PR description set to the last nova-code run report; or update an existing PR's description to that report. Use after nova-code and push (e.g. after nova-commit).
disable-model-invocation: true
---

# Nova PR (Orchestrator)

This skill creates or updates a **pull request** for the branch you just pushed. The PR description (body) is set to the **last nova-code run report** (workflow executed, per-step outcome, validation, detail sections).

**Workflow** — Read **@.cursor/workflows/nova-pr.yml**. Single step: **create_pr**.

**How to run** — Run the workflow's one phase: open **@.cursor/skills/nova-pr/sub-skills/create-pr.md** and follow it. The sub-skill will get the current branch, ensure it is pushed, read the nova-code report from `.cursor/workflows/reports/nova-code-output-report.md`, and create a PR (or update an existing one) with that report as the body, using GitHub CLI (`gh`) when available.

**Prerequisites:** A branch already pushed (e.g. after nova-commit or manual push). The file `.cursor/workflows/reports/nova-code-output-report.md` should exist from a prior nova-code run. GitHub CLI (`gh`) installed and authenticated for create/update; if not, the sub-skill returns the exact commands for you to run locally.

Do not duplicate the step list here; the workflow and sub-skill contain the full instructions.
```

</details>


