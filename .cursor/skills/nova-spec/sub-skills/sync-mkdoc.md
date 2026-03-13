# Sync mkdoc (sub-skill)

**Inputs:** Updated specs (spec index from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml` + all registry files).

**Outputs:** List of created/updated docs file paths (including mkdocs config at project root if updated).

**References:** **@.cursor/skills/nova-code/mk/page-formats.md**, **@.cursor/skills/common/project-config.md**.

---

After writing spec files, sync the **docs dir** (from **@.cursor/project.yml** `paths.docs_dir`, default `mkdoc`) from the updated specs so that docs reflect features, requirements, and assets. **Project name:** from project.yml `project_name` or first registry `project.name`. **Site name:** from project.yml `docs.site_name` or project name.

## 1. Bootstrap

- If the **docs dir** (paths.docs_dir, default mkdoc) does not exist, create it. If its **index.md** does not exist, create it with minimal content: H1 &lt;project name from config or registry&gt;, optional project logo image block when the home page already uses one (for Vinekeepers this is `<img src="img/Vinekeepers.png" alt="Vinekeepers logo" width="400" />` directly under the H1), `# Overview` (one sentence from README or specs), `# Quick links` (bulleted links to [Features](features/index.md), [Architecture](architecture.md), [Runbooks](runbooks/index.md), [Cursor](cursor/index.md)).
- If **mkdocs config** (paths.mkdocs_file at project root, default mkdocs.yml) does not exist or needs updating, set it so that `mkdocs serve` works when run from the project root: `site_name:` &lt;docs.site_name or project name&gt;, `docs_dir:` &lt;paths.docs_dir&gt;, and a `nav` built as follows. **Same rule for bootstrap and every update:** whenever creating or updating mkdocs.yml, **regenerate the full Features nav** from the **current** domain/feature list (from step 2 below, "Derive domains and features from specs"). Do not append to an existing nav or preserve a flat list; always rebuild the Features section from current data so new domains get new subsections and new features appear under the correct domain. **Features section (nested):** Overview → `features/index.md`, then **Domains** → `features/domain.md` **with** each domain subsection as **children** of that Domains entry, not as siblings. Under Domains, for each domain add a subsection (title = domain display name) containing: (1) the domain index page `features/domain/<domain-slug>.md` (label "Overview" or domain name), (2) each feature summary page using the feature's resolved summary path. Do not assume feature pages live under a directory that matches the domain slug when `doc_path` intentionally points elsewhere. Also include Home → index.md, Architecture → architecture.md, Runbooks: include runbooks/index plus every runbook file listed in page-formats (runbooks section). **Also include the Cursor section** in the nav: Overview → `cursor/index.md`, Rules (Overview + each rule), Skills (Overview + each skill with nested sub-skill pages), Workflows (Overview + each workflow); structure per **@.cursor/skills/common/sync-cursor-docs.md** section 6.

## 2. Derive domains and features from specs

For each registry file in the spec index (path from project config) → `specs[].file`:

- Load the registry. Registry stem is only a fallback owning slice.
- If the registry has a **features** array: use it. Each feature gives (id, slug, title, requirement_ids, asset_ids optional, status, and optionally doc_path, domain_slug, summary). Domain slug = `feature.domain_slug` when present; otherwise use the registry stem. **Feature summary doc path:** When a feature has **doc_path**, use it as the path to the feature summary page (relative to docs_dir). When doc_path is absent, derive per **@.cursor/skills/common/project-config.md** (default feature summary path). So we get a list of `(domain_slug, feature_slug, feature_title, requirement_ids, asset_ids, status, summary_path, summary optional)`.
- If the registry has no features array: derive one feature per domain: slug = registry stem, title = project.name or "Domain &lt;domain&gt;", requirement_ids = all requirement ids in that registry, asset_ids = omit (implied), status = active/draft/deprecated from requirements.
- Do not write back into the registry from sync-mkdoc; **update-registry** is the place that sets doc_path, domain_slug, and summary.

## 3. Write docs structure

Per **@.cursor/skills/nova-code/mk/page-formats.md**. Use **docs_dir** from project config (default mkdoc) as the base path.

- **Index and features index:** &lt;docs_dir&gt;/index.md, &lt;docs_dir&gt;/features/index.md, &lt;docs_dir&gt;/features/domain.md. Content: same as nova-mk (project summary, definition of feature, domains table with links). Use registry `project` and the domain/feature list from step 2.
- **Per-domain index:** For each domain slug, write &lt;docs_dir&gt;/features/domain/&lt;domain-slug&gt;.md with a Features table listing **every** feature in that domain (Feature, Status, link to feature summary page).
- **Architecture:** &lt;docs_dir&gt;/architecture.md — Overview, System context, Major subsystems (domains), Runtime flows, Diagram. Use specs and README.
- **Runbooks:** &lt;docs_dir&gt;/runbooks/index.md, &lt;docs_dir&gt;/runbooks/operational.md (and optionally troubleshooting, recovery, maintenance). Minimal content is acceptable; link from index.
- **Feature dossiers:** For **each** feature in the registry (from step 2), write the feature summary at its resolved summary path from step 2 and write the seven sub-pages under the sibling feature directory (`dirname(summary_path)/<feature-slug>/`). Content **must** be filled from the registry: requirements (behavior, acceptance, traceability), assets (path, role), validation.tests. Do not invent content that is not in the spec. Follow the section order and headings in page-formats.md. In diagrams.md, link to the main architecture page with `../../../../architecture.md` (four levels up from feature sub-pages). Ensure the features index and per-domain page list **every** feature with correct links.

**Step 3b. Sync Cursor docs.** Run **@.cursor/skills/common/sync-cursor-docs.md** (or perform inline): enumerate `.cursor/rules`, `.cursor/skills`, `.cursor/workflows`; write all `cursor/` pages per page-formats (Cursor section); record changed cursor paths. When you regenerate mkdocs.yml (in bootstrap or any update), include the Cursor nav section as described in step 1 and in sync-cursor-docs.md section 6.

## 3c. Comprehensive update (gap-fill)

Apply the **Mkdoc gap-fill checklist** in **@.cursor/skills/common/mkdoc-gap-fill-checklist.md**: create missing docs; update existing docs that lack required sections, markup, or links. Skip only files that already satisfy every requirement in that checklist.

## 4. Links and paths

Use relative links between markdown files as in page-formats. Create parent directories as needed.

## 5. Return

Record the list of created or updated docs file paths (include the mkdocs config file at project root if it was created or updated). Return Pass; list of changed docs paths.
