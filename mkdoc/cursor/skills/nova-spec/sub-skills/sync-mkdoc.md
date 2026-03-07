# Sync mkdoc

## Summary

Syncs the docs dir from updated specs: bootstrap, derive domains/features, write index, domain pages, architecture, runbooks, feature dossiers; step 3b sync Cursor docs. Regenerates mkdocs.yml nav including Cursor section.

## Key points

- See **.cursor/skills/nova-spec/sub-skills/sync-mkdoc.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Sync mkdoc (sub-skill)

**Inputs:** Updated specs (spec index from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml` + all registry files).

**Outputs:** List of created/updated docs file paths (including mkdocs config at project root if updated).

**References:** **@.cursor/skills/nova-code/mk/page-formats.md**, **@.cursor/skills/common/project-config.md**.

---

After writing spec files, sync the **docs dir** (from **@.cursor/project.yml** `paths.docs_dir`, default `mkdoc`) from the updated specs so that docs reflect features, requirements, and assets. **Project name:** from project.yml `project_name` or first registry `project.name`. **Site name:** from project.yml `docs.site_name` or project name.

## 1. Bootstrap

- If the **docs dir** (paths.docs_dir, default mkdoc) does not exist, create it. If its **index.md** does not exist, create it with minimal content: H1 &lt;project name from config or registry&gt;, `# Overview` (one sentence from README or specs), `# Quick links` (bulleted links to [Features](features/index.md), [Architecture](architecture.md), [Runbooks](runbooks/index.md), [Cursor](cursor/index.md)).
- If **mkdocs config** (paths.mkdocs_file at project root, default mkdocs.yml) does not exist or needs updating, set it so that `mkdocs serve` works when run from the project root: `site_name:` &lt;docs.site_name or project name&gt;, `docs_dir:` &lt;paths.docs_dir&gt;, and a `nav` built as follows. **Same rule for bootstrap and every update:** whenever creating or updating mkdocs.yml, **regenerate the full Features nav** from the **current** domain/feature list (from step 2 below, "Derive domains and features from specs"). Do not append to an existing nav or preserve a flat list; always rebuild the Features section from current data so new domains get new subsections and new features appear under the correct domain. **Features section (nested):** Overview → `features/index.md`, then **Domains** → `features/domain.md` **with** each domain subsection (Core, Connectors, etc.) as **children** of that Domains entry, not as siblings. Under Domains, for each domain add a subsection (title = domain display name, e.g. Core, Connectors) containing: (1) the domain index page `features/domain/<domain-slug>.md` (label "Overview" or domain name), (2) each feature in that domain `features/domain/<domain-slug>/<feature-slug>.md`. Do not list domains as siblings of Domains; nest them under Domains. Also include Home → index.md, Architecture → architecture.md, Runbooks (index, operational, troubleshooting, recovery, maintenance). **Also include the Cursor section** in the nav: Overview → `cursor/index.md`, Rules (Overview + each rule), Skills (Overview + each skill with nested sub-skill pages), Workflows (Overview + each workflow); structure per **@.cursor/skills/common/sync-cursor-docs.md** section 6.

## 2. Derive domains and features from specs

For each registry file in the spec index (path from project config) → `specs[].file`:

- Load the registry. **Domain** = registry file stem (e.g. `core-registry.yml` → domain slug `core`).
- If the registry has a **features** array: use it. Each feature gives (id, slug, title, requirement_ids, asset_ids optional, status). Domain slug = registry stem. So we get a list of (domain_slug, feature_slug, feature_title, requirement_ids, asset_ids, status).
- If the registry has no features array: derive one feature per domain: slug = domain, title = project.name or "Domain &lt;domain&gt;", requirement_ids = all requirement ids in that registry, asset_ids = omit (implied), status = active/draft/deprecated from requirements.

## 3. Write docs structure

Per **@.cursor/skills/nova-code/mk/page-formats.md**. Use **docs_dir** from project config (default mkdoc) as the base path.

- **Index and features index:** &lt;docs_dir&gt;/index.md, &lt;docs_dir&gt;/features/index.md, &lt;docs_dir&gt;/features/domain.md. Content: same as nova-mk (project summary, definition of feature, domains table with links). Use registry `project` and the domain/feature list from step 2.
- **Per-domain index:** For each domain slug, write &lt;docs_dir&gt;/features/domain/&lt;domain-slug&gt;.md with a Features table listing **every** feature in that domain (Feature, Status, link to feature summary page).
- **Architecture:** &lt;docs_dir&gt;/architecture.md — Overview, System context, Major subsystems (domains), Runtime flows, Diagram. Use specs and README.
- **Runbooks:** &lt;docs_dir&gt;/runbooks/index.md, &lt;docs_dir&gt;/runbooks/operational.md (and optionally troubleshooting, recovery, maintenance). Minimal content is acceptable; link from index.
- **Feature dossiers:** For **each** feature in the registry (from step 2), write &lt;docs_dir&gt;/features/domain/&lt;domain-slug&gt;/&lt;feature-slug&gt;.md and the seven sub-pages: how-it-works.md, change-log.md, known-issues.md, decisions.md, contracts.md, tests.md, diagrams.md. Content **must** be filled from the registry: requirements (behavior, acceptance, traceability), assets (path, role), validation.tests. Do not invent content that is not in the spec. Follow the section order and headings in page-formats.md. In diagrams.md, link to the main architecture page with `../../../../architecture.md` (four levels up from feature sub-pages). Ensure the features index and per-domain page list **every** feature with correct links.

**Step 3b. Sync Cursor docs.** Run **@.cursor/skills/common/sync-cursor-docs.md** (or perform inline): enumerate `.cursor/rules`, `.cursor/skills`, `.cursor/workflows`; write all `cursor/` pages per page-formats (Cursor section); record changed cursor paths. When you regenerate mkdocs.yml (in bootstrap or any update), include the Cursor nav section as described in step 1 and in sync-cursor-docs.md section 6.

## 3c. Comprehensive update (gap-fill)

When syncing, **update all documents comprehensively**. You may **skip** files that already satisfy every requirement below; **create** missing files; **update** existing files that lack required sections, markup, or links. Do not only create missing docs—also fix gaps in existing docs.

**Index and home**

- `index.md`: H1 = project name (from config/registry); `# Overview`; `# Quick links` includes [Features](features/index.md), [Architecture](architecture.md), [Runbooks](runbooks/index.md), **[Cursor](cursor/index.md)**. Fix if Quick links omit Cursor or any standard link.

**Features**

- `features/index.md`: Title Features; `# Definition of a feature`; `# Domains` table with every domain and link to `features/domain/<domain-slug>.md`.
- `features/domain.md`: Title Domain index; list of domains with links to `features/domain/<domain-slug>.md`.
- Each `features/domain/<domain-slug>.md`: Title = domain name; `# Features` table listing **every** feature in that domain (Feature, Status, link to feature summary). Add any missing features; remove stale entries.
- For **each** feature in the registry: feature summary page `features/domain/<domain-slug>/<feature-slug>.md` and **all seven sub-pages** (how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams). Each must have the required sections per **page-formats.md**. In **diagrams.md**, ensure link to main architecture uses `../../../../architecture.md`. Add any missing sub-page or section; refresh content from registry where needed.

**Architecture**

- `architecture.md`: `# Overview`, `# System context`, `# Major subsystems`, `# Runtime flows`, `# Diagram`. If a Mermaid diagram is present, use **vertical** flow (`flowchart TB`). Add or fix any missing section.

**Runbooks**

- `runbooks/index.md`, `runbooks/operational.md`, `runbooks/troubleshooting.md`, `runbooks/recovery.md`, `runbooks/maintenance.md`: all exist with required headings per page-formats. Create any missing file; add missing headings to existing files.

**Cursor docs**

- Apply the **Cursor comprehensive checklist** in **@.cursor/skills/common/sync-cursor-docs.md** (section "Comprehensive update (gap-fill)"): every rule, skill, and workflow page exists; every skill/sub-skill page has TOC heading + expandable source with `skill-source-wrap`; all Mermaid diagrams use `flowchart TB`; nav includes every current rule, skill (with nested sub-skills), and workflow. Create missing cursor pages; **update** existing cursor pages that lack any of these.

**Navigation (mkdocs.yml)**

- Regenerate or patch nav so it is complete and consistent: Home, Features (nested by domain, each domain with its features), Architecture, Runbooks (all five), **Cursor** (Overview, Rules with index + each rule, Skills with index + each skill and nested sub-skill pages, Workflows with index + each workflow). Remove or update stale entries (e.g. features or skills no longer in .cursor or specs).

## 4. Links and paths

Use relative links between markdown files as in page-formats. Create parent directories as needed.

## 5. Return

Record the list of created or updated docs file paths (include the mkdocs config file at project root if it was created or updated). Return Pass; list of changed docs paths.
```

</details>
