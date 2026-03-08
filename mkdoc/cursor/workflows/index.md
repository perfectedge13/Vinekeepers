# Workflows

## Overview

Workflows are YAML definitions under `.cursor/workflows/` that define `run_order` and step locations. The orchestrator runs steps in order; gates stop on failure.

| Workflow | Description | Link |
|----------|-------------|------|
| nova-code | Spec-driven implementation. Discovery → gates → execution loop → mk → output. | [nova-code](nova-code.md) |
| nova-commit | Full verification and commit. Discovery, gates, tests, plausibility, commit/push, output. | [nova-commit](nova-commit.md) |
| nova-mk | Sync mkdoc/ from specs and handoff. prepare, index, architecture, runbooks, feature dossiers, cursor. | [nova-mk](nova-mk.md) |
| nova-pr | Create or update PR with nova-code report as body. | [nova-pr](nova-pr.md) |
| nova-spec | Full-repo scan and complete spec update. Single step: scan_and_update. | [nova-spec](nova-spec.md) |
| nova-wiki | Wiki.js sync. prepare, index, architecture, runbooks, feature dossiers. | [nova-wiki](nova-wiki.md) |


