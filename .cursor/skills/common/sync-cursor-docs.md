# Sync Cursor docs (common)

**Inputs:** Docs dir from **@.cursor/project.yml** `paths.docs_dir` (default `mkdoc`). Optional: existing mkdocs.yml (for merging Cursor into nav).

**Outputs:** List of created/updated `cursor/**` file paths; updated mkdocs.yml at project root if nav was changed.

**References:** **@.cursor/skills/nova-code/mk/page-formats.md** (Cursor docs section), **@.cursor/skills/common/project-config.md**.

---

Sync Cursor docs from `.cursor/` into the docs dir so that **rules**, **skills**, and **workflows** are documented in mkdoc with their own nav category. When run (e.g. by nova-spec), **update all cursor documents comprehensively**: create missing pages and **update existing pages** that lack required sections or markup. Skip only pages that already satisfy the full checklist below.

## 1. Enumerate

- **Rules:** All `.cursor/rules/*.mdc`. Slug = filename without extension (e.g. `spec-workflow`, `spec-workflow-core`).
- **Skills (top-level):** Directories under `.cursor/skills/` that contain `SKILL.md`: e.g. `nova-code`, `nova-commit`, `nova-pr`, `nova-spec`, `nova-wiki`. Do **not** treat `common` as a top-level skill (no SKILL.md at its root).
- **Sub-skills:** Under each skill dir, all `.md` files **except** `SKILL.md` and `page-formats.md`. Preserve path relative to the skill dir (e.g. `implementation/plan-change.md`, `sub-skills/scan.md`, `mk/sub-skills/prepare.md`). One cursor doc page per file at `cursor/skills/<skill-slug>/<relative-path>.md` (use forward slashes).
- **Common:** All `.md` files under `.cursor/skills/common/` **except** `README.md`. Slug = filename without extension (e.g. `discovery`, `drift-gate`, `project-config`, `reconcile`, `requirement-tracking`, `run-tests`, `schema-gate`, `static-analysis`, `sync-cursor-docs`, `update-readme`). One cursor doc page per file at `cursor/skills/common/<slug>.md`. Common is not a top-level skill (no SKILL.md); it is a documented group of shared sub-skills used by nova-code and other workflows.
- **Nested skill (subfolder with SKILL.md):** If a skill dir has a subfolder that contains its own `SKILL.md` (e.g. `nova-code/mk/SKILL.md`), generate **one** overview page at `cursor/skills/<skill-slug>/<subfolder>.md` (e.g. `cursor/skills/nova-code/mk.md`) from that SKILL.md. Treat it like a skill page: Summary, Key points, Diagram, Sub-skills (links to that subfolder's sub-skill pages), Expandable skill source.
- **Workflows:** All `.yml` files **directly** under `.cursor/workflows/` (not under `reports/` or any subdir). Slug = filename without extension (e.g. `nova-code`, `nova-commit`).

## 1b. Comprehensive update (gap-fill)

For **each** cursor page, after writing or when considering updates: **create** the file if missing; if the file **exists**, check and add/fix any of the following that are missing or wrong:

- **Skill and sub-skill pages:** (1) Level-2 heading for source section: `## Skill source (markdown)` or `## Source (markdown)` so it appears in the table of contents. (2) Expandable block: `<details class="skill-source-wrap"><summary>Click to expand</summary>` and a fenced code block containing the **current** full contents of the corresponding `.cursor/` source file, then `</details>`. (3) Mermaid diagrams: use `flowchart TB` (vertical), not `flowchart LR`. (4) Sub-skill list on skill pages: links to every sub-skill page under that skill; add any new sub-skills; remove links to deleted sub-skills.
- **Rules pages:** Summary and key points from current .mdc; update if rule content changed.
- **Workflows pages:** Sequence (run_order) and description from current .yml; optional Mermaid with `flowchart TB`. Update if workflow changed.
- **Index pages (rules, skills, workflows):** Table includes **every** current rule/skill/workflow with correct link; add new entries; remove stale entries.
- **Nav (mkdocs.yml):** Cursor section includes every enumerated rule, every skill with nested sub-skill pages, every workflow. Add new items; remove or update stale items.

Skip a page only if it already satisfies all of the above for its type.

## 2. Write cursor/index.md

Path: `<docs_dir>/cursor/index.md`. Create or **update** so content per page-formats: **Title:** Cursor. **Overview:** Short intro to project Cursor config (rules, skills, workflows). **Sections:** Links to [Rules](rules/index.md), [Skills](skills/index.md), [Workflows](workflows/index.md). Create parent dir if needed.

## 3. Write rules pages

- **cursor/rules/index.md:** Create or **update**. **Title:** Rules. **Overview:** What rules are (alwaysApply / optional). **Table:** For **each** `.cursor/rules/*.mdc`, row: Rule name, Description, Link to `cursor/rules/<slug>.md`. Add new rules; remove stale rows.
- **cursor/rules/<slug>.md** (one per rule): Create or **update**. Read the **current** `.mdc` file. **Title:** Rule name (humanized from slug or frontmatter). **Summary:** 1–2 sentences from frontmatter `description`. **Key points:** Bullet list of main obligations from the rule body. Do not duplicate the full rule text. If the page exists but content is outdated, refresh from the source file.

## 4. Write skills pages

- **cursor/skills/index.md:** Create or **update**. **Title:** Skills. **Overview:** What skills are (orchestrators, sub-skills). **Table:** For **each** top-level skill (dirs with SKILL.md), row: Skill name, short description (from SKILL.md), Link to `cursor/skills/<skill-slug>.md`. Add new skills; remove stale rows.

- **cursor/skills/<skill-slug>.md** (one per top-level skill): Create or **update**. Read the **current** `.cursor/skills/<skill-slug>/SKILL.md`. **Title:** Skill name. **Summary:** What the skill does. **Key points:** Bullet list. **Diagram:** Mermaid with **vertical** flow (`flowchart TB`); if the page has a diagram with `flowchart LR`, change it to `flowchart TB`. **Sub-skills:** Bulleted list with links to **every** sub-skill page under this skill (from enumeration); add new sub-skills; remove broken links. **Expandable skill source:** If missing, add `## Skill source (markdown)` then `<details class="skill-source-wrap"><summary>Click to expand</summary>` and a fenced code block with the **full current** contents of SKILL.md, then `</details>`. If the section exists but the embedded source is outdated, refresh it from the current SKILL.md. Create the skill dir under cursor/skills if needed.

- **cursor/skills/<skill-slug>/<subfolder>.md** (when that subfolder contains SKILL.md, e.g. nova-code/mk): Create or **update**. Read the **current** `.cursor/skills/<skill-slug>/<subfolder>/SKILL.md`. **Title:** Sub-skill/orchestrator name (e.g. Mk). **Summary:** What it does. **Key points:** Bullet list. **Diagram:** Optional Mermaid (`flowchart TB`). **Sub-skills:** Links to every sub-skill page under that subfolder (e.g. mk/sub-skills/prepare.md, …). **Expandable skill source:** `## Skill source (markdown)` then `<details class="skill-source-wrap">` with full SKILL.md content. Create parent dirs as needed.

- **cursor/skills/<skill-slug>/<sub-path>.md** (one per sub-skill .md file): Create or **update**. Read the **current** source .md file. **Title:** Sub-skill name (from first H1 or filename). **Summary:** What the sub-skill does. **Key points:** Bullet list. **Diagram:** Optional Mermaid; if present use `flowchart TB`. **Expandable source:** If missing, add `## Source (markdown)` then `<details class="skill-source-wrap"><summary>Click to expand</summary>` and a fenced code block with the **full current** contents of the source file, then `</details>`. If the section exists but content is outdated, refresh it. Preserve path hierarchy. Create parent dirs as needed.

## 4b. Write common skill pages

- **cursor/skills/common/index.md:** Create or **update**. **Title:** Common. **Overview:** Short intro (shared sub-skills used by nova-code, nova-commit, etc.; not a top-level skill with SKILL.md). **Table:** For each enumerated common .md file, row: Name (humanized from slug), Short purpose, Link to `cursor/skills/common/<slug>.md`.
- **cursor/skills/common/<slug>.md** (one per enumerated common file): Create or **update**. Same format as other sub-skill pages: **Title** (from first H1 or filename), **Summary**, **Key points**, **Source (markdown):** `<details class="skill-source-wrap"><summary>Click to expand</summary>` and a fenced code block with the **full current** contents of `.cursor/skills/common/<file>.md`, then `</details>`. Use `flowchart TB` for any Mermaid.

## 5. Write workflows pages

- **cursor/workflows/index.md:** Create or **update**. **Title:** Workflows. **Overview:** What workflows are. **Table:** For **each** workflow .yml (top-level only), row: Workflow name, Description, Link to `cursor/workflows/<slug>.md`. Add new workflows; remove stale rows.

- **cursor/workflows/<slug>.md** (one per workflow): Create or **update**. Read the **current** `.yml` file. **Title:** Workflow name. **Summary:** From workflow `description`. **Sequence:** Ordered list or table of `run_order` step IDs. **Diagram:** Optional Mermaid flowchart; if present use **vertical** flow (`flowchart TB`). **Phases/steps:** Short reference if useful. If the page exists but run_order or description changed, refresh content.

## 6. Nav (mkdocs.yml)

When **generating** or **updating** mkdocs.yml at project root, include a **Cursor** section in `nav` with this structure (nested so that Skills expand by skill and sub-skill):

- **Cursor**
  - Overview → `cursor/index.md`
  - **Rules**
    - Overview → `cursor/rules/index.md`
    - &lt;each rule slug&gt; → `cursor/rules/<slug>.md`
  - **Skills**
    - Overview → `cursor/skills/index.md`
    - &lt;each skill (e.g. Nova-code)&gt;
      - Overview → `cursor/skills/<skill-slug>.md`
      - &lt;each sub-skill path segment or group&gt; (e.g. implementation, sub-skills)
        - &lt;sub-skill page&gt; → `cursor/skills/<skill-slug>/<path>.md`
    - **Common** (shared sub-skills)
      - Overview → `cursor/skills/common/index.md`
      - &lt;each common slug&gt; → `cursor/skills/common/<slug>.md`
  - **Workflows**
    - Overview → `cursor/workflows/index.md`
    - &lt;each workflow slug&gt; → `cursor/workflows/<slug>.md`

If the caller is doing a **full** nav regeneration (e.g. sync-mkdoc), integrate this Cursor block with Home, Features, Architecture, Runbooks. If the caller is only ensuring Cursor exists (e.g. mk_cursor step), read existing mkdocs.yml and add or update the Cursor section without removing Features/Architecture/Runbooks. Use `paths.docs_dir` so paths in nav are relative to docs_dir (e.g. `cursor/index.md`).

## 7. Return

Record every created or updated file under `<docs_dir>/cursor/`. If mkdocs.yml was updated, include it in the list. Return Pass; list of changed paths.
