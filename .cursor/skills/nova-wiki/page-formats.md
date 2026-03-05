# Nova-wiki page formats (single source of truth)

Sub-skills MUST follow these formats when creating or updating Wiki.js pages. Order of sections and required headings are fixed; content is filled from specs and handoff.

| Page type (path pattern) | Required format (sections in order) |
|--------------------------|-------------------------------------|
| `/vinekeepers` | **Title:** Vinekeepers. **Body:** `# Overview` (1–2 sentence project summary). `# Quick links` (bulleted links to Features, Architecture, Runbooks). |
| `/vinekeepers/features` | **Title:** Features. **Body:** `# Definition of a feature` (paragraph: feature = cohesive capability backed by spec requirements/assets; status draft / active / deprecated). `# Domains` (table or list: Domain, Description, Link to domain index page). |
| `/vinekeepers/features/domain` | **Title:** Domain index. **Body:** `# Domains` (short intro). List of domains with links to `/vinekeepers/features/domain/<domain-slug>`. |
| `/vinekeepers/features/domain/<domain-slug>` | **Title:** \<Domain name\>. **Body:** `# Features` (table or list: Feature, Status, Link to feature summary page). |
| `/vinekeepers/features/domain/<domain>/<feature-name>` | **Title:** \<Feature name\>. **Body:** `# Status` (single line: draft / active / deprecated; if deprecated, link to successor). `# Summary` (short description). `# Key assets` (class/method summaries or table: Asset, Role, Path). `# Sub-pages` (bulleted links to how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams). |
| `/vinekeepers/features/domain/<domain>/<feature-name>/how-it-works` | **Title:** How it works. **Body:** `# Overview` (behavior in prose). `# Flow` (numbered or bullet steps; optional Mermaid). `# Inputs and outputs` (if applicable). |
| `/vinekeepers/features/domain/<domain>/<feature-name>/change-log` | **Title:** Change log. **Body:** `# Entries` (chronological list; each entry: `## YYYY-MM-DD` then paragraph or bullets). Newest first or oldest first (pick one and stick to it). |
| `/vinekeepers/features/domain/<domain>/<feature-name>/known-issues` | **Title:** Known issues. **Body:** `# Active` (list of current issues: short title, description, workaround if any). `# Resolved` (optional; moved from Active when fixed). |
| `/vinekeepers/features/domain/<domain>/<feature-name>/decisions` | **Title:** Decisions. **Body:** `# Entries` (each: `## YYYY-MM-DD — <short title>` then context, decision, consequence). |
| `/vinekeepers/features/domain/<domain>/<feature-name>/contracts` | **Title:** Contracts. **Body:** `# APIs` (endpoints, request/response shape). `# Schemas` (data models, field list). `# Interfaces` (Java/other interfaces; method signatures or link to code). |
| `/vinekeepers/features/domain/<domain>/<feature-name>/tests` | **Title:** Tests. **Body:** `# Coverage` (summary). `# Test list` (table: Test ID, Title, Class, Method, Intent). |
| `/vinekeepers/features/domain/<domain>/<feature-name>/diagrams` | **Title:** Diagrams. **Body:** `# Architecture` (link to main architecture or inline Mermaid). `# Feature flow` (optional Mermaid for this feature). |
| `/vinekeepers/architecture` | **Title:** Architecture. **Body:** `# Overview` (2–3 sentences). `# System context` (external systems, boundaries). `# Major subsystems` (domains with short description). `# Runtime flows` (how components interact). `# Diagram` (Mermaid block or link). |
| `/vinekeepers/runbooks` | **Title:** Runbooks. **Body:** `# Index` (links to Operational, Troubleshooting, Recovery, Maintenance). Short intro. |
| `/vinekeepers/runbooks/operational` | **Title:** Operational. **Body:** `# Procedures` (heading per procedure; steps underneath). |
| `/vinekeepers/runbooks/troubleshooting` | **Title:** Troubleshooting. **Body:** `# Entries` (each: `## <symptom or issue>` then cause, steps, resolution). |
| `/vinekeepers/runbooks/recovery` | **Title:** Recovery. **Body:** `# Procedures` (recovery scenarios and steps). |
| `/vinekeepers/runbooks/maintenance` | **Title:** Maintenance. **Body:** `# Procedures` (maintenance tasks, schedule, steps). |

## When to update (by owning step)

- **wiki_index** owns: `/vinekeepers`, `/vinekeepers/features`, `/vinekeepers/features/domain`, `/vinekeepers/features/domain/<domain-slug>`.
- **wiki_architecture** owns: `/vinekeepers/architecture`.
- **wiki_runbooks** owns: `/vinekeepers/runbooks`, `/vinekeepers/runbooks/operational`, `/vinekeepers/runbooks/troubleshooting`, `/vinekeepers/runbooks/recovery`, `/vinekeepers/runbooks/maintenance`.
- **wiki_feature_dossiers** owns: `/vinekeepers/features/domain/<domain>/<feature-name>` and its seven sub-pages (how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams).

See the plan (section 4) for the full "when to update" triggers per page.
