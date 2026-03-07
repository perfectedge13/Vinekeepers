# Skills

## Overview

Skills are orchestrators and sub-skills under `.cursor/skills/`. Each top-level skill has a `SKILL.md` and may have sub-skills (e.g. `implementation/`, `sub-skills/`). The orchestrator runs the workflow and launches sub-agents for each step.

| Skill | Description | Link |
|-------|-------------|------|
| Nova-code | Spec-driven implementation; runs gates and launches one sub-agent per step. Mk (mkdoc sync) is a sub-skill. | [Nova-code](nova-code.md) |
| Nova-commit | Full verification and commit; discovery, gates, tests, plausibility, commit/push. | [Nova-commit](nova-commit.md) |
| Nova-pr | Create or update PR with last nova-code report as body. | [Nova-pr](nova-pr.md) |
| Nova-spec | Full-repo scan and complete spec update; scan, update registries, sync mkdoc. | [Nova-spec](nova-spec.md) |
| Nova-wiki | Wiki.js sync; prepare, index, architecture, runbooks, feature dossiers. | [Nova-wiki](nova-wiki.md) |
| Common | Shared sub-skills (discovery, run-tests, reconcile, etc.) used by nova-code and other workflows. | [Common](common/index.md) |
