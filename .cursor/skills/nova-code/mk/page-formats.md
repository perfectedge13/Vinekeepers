# Nova-mk page formats (single source of truth)

Sub-skills MUST follow these formats when creating or updating markdown files under the **docs dir** (from **@.cursor/project.yml** `paths.docs_dir`, default `mkdoc`). Order of sections and required headings are fixed; content is filled from specs and handoff. Create parent directories as needed. **Project name** for titles: from **@.cursor/project.yml** `project_name` or first registry `project.name`. See **@.cursor/skills/common/project-config.md**.

| File path | Required format (sections in order) |
|-----------|-------------------------------------|
| &lt;docs_dir&gt;/index.md | **Title (H1):** &lt;project name from config or registry&gt;. Preserve any existing project logo image block directly below the H1 (for Vinekeepers, `<img src="img/Vinekeepers.png" alt="Vinekeepers logo" width="400" />`). `# Overview` (1–2 sentence project summary). `# Quick links` (bulleted links to Features, Architecture, Runbooks, and Cursor — use relative links e.g. [Features](features/index.md)). |
| `&lt;docs_dir&gt;/features/index.md` | **Title:** Features. `# Definition of a feature` (paragraph: feature = cohesive capability backed by spec requirements/assets; status draft / active / deprecated). `# Domains` (table or list: Domain, Description, Link to domain index). |
| `&lt;docs_dir&gt;/features/domain.md` | **Title:** Domain index. `# Domains` (short intro). List of domains with links to `features/domain/<domain-slug>.md`. |
| `&lt;docs_dir&gt;/features/domain/<domain-slug>.md` | **Title:** \<Domain name\>. `# Features` (table or list: Feature, Status, Link to feature summary page). |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>.md` | **Title:** \<Feature name\>. `# Status` (single line: draft / active / deprecated; if deprecated, link to successor). `# Summary` (short description). `# Key assets` (class/method summaries or table: Asset, Role, Path). `# Sub-pages` (bulleted links to how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams). |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>/how-it-works.md` | **Title:** How it works. `# Overview` (behavior in prose). `# Flow` (numbered or bullet steps; optional Mermaid). `# Inputs and outputs` (if applicable). |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>/change-log.md` | **Title:** Change log. `# Entries` (chronological list; each entry: `## YYYY-MM-DD` then paragraph or bullets). New entries append or merge by stable identity; preserve prior history by default. |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>/known-issues.md` | **Title:** Known issues. `# Active` (list of current issues: short title, description, workaround if any). `# Resolved` (optional; moved from Active when fixed). Update by issue identity; preserve prior entries by default. |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>/decisions.md` | **Title:** Decisions. `# Entries` (each: `## YYYY-MM-DD — <short title>` then context, decision, consequence). Update by `date + title`; preserve prior entries by default. |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>/contracts.md` | **Title:** Contracts. `# APIs` (endpoints, request/response shape). `# Schemas` (data models, field list). `# Interfaces` (Java/other interfaces; method signatures or link to code). |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>/tests.md` | **Title:** Tests. `# Coverage` (summary). `# Test list` (table: Test ID, Title, Class, Method, Intent). |
| `&lt;docs_dir&gt;/features/domain/<domain>/<feature-name>/diagrams.md` | **Title:** Diagrams. `# Architecture` (link to main architecture or inline Mermaid). When linking to the main architecture page from this file, use `../../../../architecture.md` — four levels up to the docs root. `# Feature flow` (optional Mermaid for this feature). |
| `&lt;docs_dir&gt;/architecture.md` | **Title:** Architecture. `# Overview` (2–3 sentences). `# System context` (external systems, boundaries). `# Major subsystems` (domains with short description). `# Runtime flows` (how components interact). `# Diagram` (Mermaid block or link). |

**Runbooks (canonical list):** The runbook set below is canonical. mk_runbooks and sync-mkdoc must ensure every runbook listed here exists under `<docs_dir>/runbooks/` and is linked from runbooks/index.md and from the Runbooks nav.

| `&lt;docs_dir&gt;/runbooks/index.md` | **Title:** Runbooks. `# Index` (links to operational, troubleshooting, recovery, maintenance, configuring-bots). Short intro. |
| `&lt;docs_dir&gt;/runbooks/operational.md` | **Title:** Operational. `# Procedures` (heading per procedure; steps underneath). |
| `&lt;docs_dir&gt;/runbooks/troubleshooting.md` | **Title:** Troubleshooting. `# Entries` (each: `## <symptom or issue>` then cause, steps, resolution). |
| `&lt;docs_dir&gt;/runbooks/recovery.md` | **Title:** Recovery. `# Procedures` (recovery scenarios and steps). |
| `&lt;docs_dir&gt;/runbooks/maintenance.md` | **Title:** Maintenance. `# Procedures` (maintenance tasks, schedule, steps). |
| `&lt;docs_dir&gt;/runbooks/configuring-bots.md` | **Title:** Configuring bots. `# Procedures` (steps for editing bot definitions, routing, persona, workflows; reference config/bots.yaml and project.yml `scan.bots_config`). |

## When to update (by owning step)

- **mk_index** owns: `&lt;docs_dir&gt;/index.md`, `&lt;docs_dir&gt;/features/index.md`, `&lt;docs_dir&gt;/features/domain.md`, `&lt;docs_dir&gt;/features/domain/<domain-slug>.md`.
- **mk_architecture** owns: `&lt;docs_dir&gt;/architecture.md`.
- **mk_runbooks** owns: `&lt;docs_dir&gt;/runbooks/index.md`, `&lt;docs_dir&gt;/runbooks/operational.md`, `&lt;docs_dir&gt;/runbooks/troubleshooting.md`, `&lt;docs_dir&gt;/runbooks/recovery.md`, `&lt;docs_dir&gt;/runbooks/maintenance.md`, `&lt;docs_dir&gt;/runbooks/configuring-bots.md`.
- **mk_feature_dossiers** owns: the resolved feature summary path plus its seven sub-pages (how-it-works.md, change-log.md, known-issues.md, decisions.md, contracts.md, tests.md, diagrams.md).

## Update semantics

- **Generated-and-overwritable:** home/index pages, domain index pages, feature summary pages, how-it-works, contracts, tests, diagrams, architecture, runbooks, Cursor docs, and mkdocs nav may be fully regenerated.
- **Generated-with-history-preserving semantics:** `change-log.md`, `known-issues.md`, and `decisions.md` preserve prior entries by default. Append or merge entries by stable identity instead of replacing the whole file.

Use relative links between markdown files (e.g. `[Architecture](../architecture.md)` from runbooks, `[Features](features/index.md)` from index).

---

## Cursor docs (rules, skills, workflows)

When writing **cursor/** pages (see **@.cursor/skills/common/sync-cursor-docs.md**), use these formats. Base path: &lt;docs_dir&gt;/cursor/.

| Page | Required format |
|------|------------------|
| `cursor/index.md` | **Title:** Cursor. **Overview:** Short intro to project Cursor config (rules, skills, workflows). **Sections:** Links to [Rules](rules/index.md), [Skills](skills/index.md), [Workflows](workflows/index.md). |
| `cursor/rules/index.md` | **Title:** Rules. **Overview:** What rules are (alwaysApply / optional). **Table:** Rule name, Description (from frontmatter), Link to rule page. |
| `cursor/rules/<slug>.md` | **Title:** &lt;Rule name&gt;. **Summary:** 1–2 sentences from rule description. **Key points:** Bullet list of main obligations (gates, workflow steps, removal/rename, shell, output). Do not duplicate full rule text. |
| `cursor/skills/index.md` | **Title:** Skills. **Overview:** What skills are (orchestrators, sub-skills). **Table:** Skill name, Description, Link to skill page. |
| `cursor/skills/<skill>.md` | **Title:** &lt;Skill name&gt;. **Summary:** What the skill does (from SKILL.md). **Key points:** Bullet list. **Diagram:** Mermaid (flow or sub-skill hierarchy); use **vertical** flow (`flowchart TB`). **Sub-skills:** List with links to sub-skill pages. **Expandable (TOC + wrap):** At the end, a level-2 heading `## Skill source (markdown)` (so it appears in the table of contents), then `<details class="skill-source-wrap"><summary>Click to expand</summary>` + fenced code block with full SKILL.md content + `</details>`. The class enables horizontal text wrap in mkdoc CSS. |
| `cursor/skills/<skill>/<path>.md` | **Title:** &lt;Sub-skill name&gt;. **Summary:** What the sub-skill does. **Key points:** Bullets. **Diagram:** Optional Mermaid if it describes a flow; use **vertical** flow (`flowchart TB`). **Expandable (TOC + wrap):** At the end, `## Source (markdown)` then `<details class="skill-source-wrap"><summary>Click to expand</summary>` + fenced code block with sub-skill file content + `</details>`. |
| `cursor/workflows/index.md` | **Title:** Workflows. **Overview:** What workflows are. **Table:** Workflow name, Description, Link to workflow page. |
| `cursor/workflows/<slug>.md` | **Title:** &lt;Workflow name&gt;. **Summary:** From workflow `description`. **Sequence:** Ordered list or table of `run_order` step IDs. **Diagram:** Optional Mermaid flowchart (steps in order); use **vertical** flow (`flowchart TB`). **Phases/steps:** Short reference (from YAML) if useful. |
