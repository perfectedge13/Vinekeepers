# Mkdoc gap-fill checklist

When syncing the docs dir from specs (e.g. in **@.cursor/skills/nova-spec/sub-skills/sync-mkdoc.md**), apply this checklist comprehensively: **create** missing files; **update** existing files that lack required sections, markup, or links. Skip only files that already satisfy every requirement below.

**References:** **@.cursor/skills/nova-code/mk/page-formats.md**, **@.cursor/skills/common/sync-cursor-docs.md**.

---

## Index and home

- `index.md`: H1 = project name (from config/registry); preserve any existing project logo image block immediately below the H1 (for Vinekeepers, keep `<img src="img/Vinekeepers.png" alt="Vinekeepers logo" width="400" />` and do not remove it); `# Overview`; `# Quick links` includes [Features](features/index.md), [Architecture](architecture.md), [Runbooks](runbooks/index.md), **[Cursor](cursor/index.md)**. Fix if Quick links omit Cursor or any standard link.

## Features

- `features/index.md`: Title Features; `# Definition of a feature`; `# Domains` table with every inferred domain and link to `features/domain/<domain-slug>.md`.
- `features/domain.md`: Title Domain index; list of domains with links to `features/domain/<domain-slug>.md`.
- Each `features/domain/<domain-slug>.md`: Title = domain name; `# Features` table listing **every** feature in that domain (Feature, Status, link to feature summary). Add any missing features; remove stale entries. A domain page may link to summary docs whose `doc_path` lives outside `features/domain/<domain-slug>/` when the registry intentionally preserves an older path.
- For **each** feature in the registry: feature summary page at the resolved summary path (`feature.doc_path` when present, otherwise per **@.cursor/skills/common/project-config.md** default feature summary path) and **all seven sub-pages** under the sibling feature directory. Each must have the required sections per **page-formats.md**. In **diagrams.md**, ensure link to main architecture uses `../../../../architecture.md`. Add any missing sub-page or section; refresh content from registry where needed.
- **Generated doc ownership:** Fully overwrite structural/generated docs such as home/index pages, domain indexes, feature summaries, how-it-works, contracts, tests, diagrams, architecture, runbooks, Cursor docs, and mkdocs nav. Preserve history for `change-log.md`, `known-issues.md`, and `decisions.md`: append or merge entries by stable key instead of replacing prior entries wholesale.

## Architecture

- `architecture.md`: `# Overview`, `# System context`, `# Major subsystems`, `# Runtime flows`, `# Diagram`. If a Mermaid diagram is present, use **vertical** flow (`flowchart TB`). Add or fix any missing section.

## Runbooks

- For each runbook in page-formats runbooks section: file exists, has required headings, and is linked from runbooks/index.md and from mkdocs Runbooks nav. Create any missing runbook file; add missing headings to existing files. Consider each runbook: create if missing; add missing sections; update from handoff when applicable; for runbooks with a documented source of truth (e.g. configuring-bots ↔ config/bots.yaml), refresh content when that source exists and the runbook is stale or incomplete.

## Cursor docs

- Apply the **Cursor comprehensive checklist** in **@.cursor/skills/common/sync-cursor-docs.md** (section "Comprehensive update (gap-fill)"): every rule, skill, and workflow page exists; every skill/sub-skill page has TOC heading + expandable source with `skill-source-wrap`; all Mermaid diagrams use `flowchart TB`; nav includes every current rule, skill (with nested sub-skills), and workflow. Create missing cursor pages; **update** existing cursor pages that lack any of these.

## Navigation (mkdocs.yml)

- Regenerate or patch nav so it is complete and consistent: Home, Features (nested by inferred domain, each domain with its feature summary paths), Architecture, Runbooks (runbooks/index plus every runbook in page-formats runbooks section), **Cursor** (Overview, Rules with index + each rule, Skills with index + each skill and nested sub-skill pages, Workflows with index + each workflow). Remove or update stale entries (e.g. features or skills no longer in `.cursor` or specs).
