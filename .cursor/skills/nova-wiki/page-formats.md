# Nova-wiki page formats (single source of truth)

Sub-skills MUST follow these formats when creating or updating Wiki.js pages. Order of sections and required headings are fixed; content is filled from specs and handoff. **Wiki path prefix:** Use **@.cursor/project.yml** `wiki.path_prefix` (e.g. `/vinekeepers` or `Vinekeepers`) for all page paths; if missing, derive from project name. **Project name (for root title):** From project.yml `project_name` or first registry `project.name`. See **@.cursor/skills/common/project-config.md**.

| Page type (path pattern) | Required format (sections in order) |
|--------------------------|-------------------------------------|
| `<path_prefix>` | **Title:** &lt;project name from config or registry&gt;. **Body:** `# Overview` (1–2 sentence project summary). `# Quick links` (bulleted links to Features, Architecture, Runbooks). |
| `<path_prefix>/features` | **Title:** Features. **Body:** `# Definition of a feature` (paragraph: feature = cohesive capability backed by spec requirements/assets; status draft / active / deprecated). `# Domains` (table or list: Domain, Description, Link to domain index page). |
| `<path_prefix>/features/domain` | **Title:** Domain index. **Body:** `# Domains` (short intro). List of domains with links to `<path_prefix>/features/domain/<domain-slug>`. |
| `<path_prefix>/features/domain/<domain-slug>` | **Title:** \<Domain name\>. **Body:** `# Features` (table or list: Feature, Status, Link to feature summary page). |
| `<path_prefix>/features/domain/<domain>/<feature-name>` | **Title:** \<Feature name\>. **Body:** `# Status` (single line: draft / active / deprecated; if deprecated, link to successor). `# Summary` (short description). `# Key assets` (class/method summaries or table: Asset, Role, Path). `# Sub-pages` (bulleted links to how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams). |
| `<path_prefix>/features/domain/<domain>/<feature-name>/how-it-works` | **Title:** How it works. **Body:** `# Overview` (behavior in prose). `# Flow` (numbered or bullet steps; optional Mermaid). `# Inputs and outputs` (if applicable). |
| `<path_prefix>/features/domain/<domain>/<feature-name>/change-log` | **Title:** Change log. **Body:** `# Entries` (chronological list; each entry: `## YYYY-MM-DD` then paragraph or bullets). Newest first or oldest first (pick one and stick to it). |
| `<path_prefix>/features/domain/<domain>/<feature-name>/known-issues` | **Title:** Known issues. **Body:** `# Active` (list of current issues: short title, description, workaround if any). `# Resolved` (optional; moved from Active when fixed). |
| `<path_prefix>/features/domain/<domain>/<feature-name>/decisions` | **Title:** Decisions. **Body:** `# Entries` (each: `## YYYY-MM-DD — <short title>` then context, decision, consequence). |
| `<path_prefix>/features/domain/<domain>/<feature-name>/contracts` | **Title:** Contracts. **Body:** `# APIs` (endpoints, request/response shape). `# Schemas` (data models, field list). `# Interfaces` (Java/other interfaces; method signatures or link to code). |
| `<path_prefix>/features/domain/<domain>/<feature-name>/tests` | **Title:** Tests. **Body:** `# Coverage` (summary). `# Test list` (table: Test ID, Title, Class, Method, Intent). |
| `<path_prefix>/features/domain/<domain>/<feature-name>/diagrams` | **Title:** Diagrams. **Body:** `# Architecture` (link to main architecture or inline Mermaid). `# Feature flow` (optional Mermaid for this feature). |
| `<path_prefix>/architecture` | **Title:** Architecture. **Body:** `# Overview` (2–3 sentences). `# System context` (external systems, boundaries). `# Major subsystems` (domains with short description). `# Runtime flows` (how components interact). `# Diagram` (Mermaid block or link). |
| `<path_prefix>/runbooks` | **Title:** Runbooks. **Body:** `# Index` (links to Operational, Troubleshooting, Recovery, Maintenance). Short intro. |
| `<path_prefix>/runbooks/operational` | **Title:** Operational. **Body:** `# Procedures` (heading per procedure; steps underneath). |
| `<path_prefix>/runbooks/troubleshooting` | **Title:** Troubleshooting. **Body:** `# Entries` (each: `## <symptom or issue>` then cause, steps, resolution). |
| `<path_prefix>/runbooks/recovery` | **Title:** Recovery. **Body:** `# Procedures` (recovery scenarios and steps). |
| `<path_prefix>/runbooks/maintenance` | **Title:** Maintenance. **Body:** `# Procedures` (maintenance tasks, schedule, steps). |

## When to update (by owning step)

- **wiki_index** owns: `<path_prefix>`, `<path_prefix>/features`, `<path_prefix>/features/domain`, `<path_prefix>/features/domain/<domain-slug>`.
- **wiki_architecture** owns: `<path_prefix>/architecture`.
- **wiki_runbooks** owns: `<path_prefix>/runbooks`, `<path_prefix>/runbooks/operational`, `<path_prefix>/runbooks/troubleshooting`, `<path_prefix>/runbooks/recovery`, `<path_prefix>/runbooks/maintenance`.
- **wiki_feature_dossiers** owns: `<path_prefix>/features/domain/<domain>/<feature-name>` and its seven sub-pages (how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams).

See the plan (section 4) for the full "when to update" triggers per page.
