# Common

## Overview

Shared sub-skills used by nova-code, nova-commit, and other workflows. All content lives under `.cursor/skills/common/`; there is no top-level SKILL.md (Common is not an orchestrator). Workflows reference these files via the `location` field.

## Sub-skills

| Name | Purpose | Link |
|------|---------|------|
| Discovery | Read spec index, load registry specs, summarize primary_assets and README. | [discovery](discovery.md) |
| Drift gate | Run validate-drift or manual drift checks; Pass/Fail + Spec Drift Issue. | [drift-gate](drift-gate.md) |
| Project config | Resolve project name, paths, validation commands, wiki prefix from .cursor/project.yml. | [project-config](project-config.md) |
| Reconcile | No dangling refs; traceability consistent; spec matches code; README matches. | [reconcile](reconcile.md) |
| Requirement tracking | When to create, update, or split requirements; id and traceability rules. | [requirement-tracking](requirement-tracking.md) |
| Run tests | Run project test command; full output and counts; Pass/Fail/Blocked. | [run-tests](run-tests.md) |
| Schema gate | Run validate-specs; Pass/Fail + Spec Drift Issue. | [schema-gate](schema-gate.md) |
| Static analysis | Run project static-analysis command; Pass/Fail. | [static-analysis](static-analysis.md) |
| Sync Cursor docs | Enumerate rules, skills, workflows; write cursor/ pages and update mkdocs nav. | [sync-cursor-docs](sync-cursor-docs.md) |
| Update readme | Review 12 artifact categories; update only impacted (incl. README/docs). | [update-readme](update-readme.md) |
