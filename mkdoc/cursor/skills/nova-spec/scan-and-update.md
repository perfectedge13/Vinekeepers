# Scan and update

## Summary

Main step for nova-spec: scan phase, update phase (infer domains, categorize features, update specs index, update registries), write spec files, sync mkdoc. Returns report per output-format.md.

## Key points

- See **.cursor/skills/nova-spec/scan-and-update.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-spec: scan and update

**Inputs:** None required. Optional: a path list to restrict scan scope; default = full repository.

**Outputs:** A final report per **@.cursor/skills/nova-spec/output-format.md** (Outcome, Summary, Phases executed, Specs updated, Mkdoc updated, Domains, Issues/notes).

**References:** **@specs/schema/specs-index.schema.json**, **@specs/schema/req-registry.schema.json**, **@.cursor/skills/common/requirement-tracking.md** (for id conventions), **@.cursor/skills/common/project-config.md**. Use only existing schema keys and the registry's `enums`; do not invent new keys. Asset and requirement ids must match idToken pattern: `^[A-Z0-9][A-Z0-9\-_.]*$`. **Spec index path:** From **@.cursor/project.yml** `paths.specs_index` if present, else `specs/specs.yml`.

---

## 1. Scan phase

Run sub-skill **@.cursor/skills/nova-spec/sub-skills/scan.md** (or perform the scan inline). Obtain: **candidate features** (slug, title, requirement_ids, asset_ids, suggested_domain); **current spec state** (specs.yml and all registry files); candidate assets and candidate requirements.

---

## 2. Update phase

**Sub-agents:** For steps 2.1–2.4, the orchestrator may run the sub-skill **inline** or **launch a sub-agent** (mcp_task) with the sub-skill path and handoff. Prefer sub-agent for **categorize-features-to-domains** (2.2) and **update-registry** (2.4) when the workload is large (many domains/features). Sub-skills live under **@.cursor/skills/nova-spec/sub-skills/**.

### 2.0 Update dimensions

When updating specs, consider and apply updates to:

- **Domains:** Add or remove registry files; ensure specs[] in specs.yml lists all registry files (one domain per registry). Via **update-specs-index** sub-skill.
- **Features:** Add/update/remove features in the correct registry. Via **update-registry** sub-skill per domain.
- **Categorization:** Assign each feature (and its requirements/assets) to exactly one domain (which registry file it lives in); update when moving features between domains. Via **categorize-features-to-domains** and then **update-registry**.

### 2.1 Infer domains

Run sub-skill **@.cursor/skills/nova-spec/sub-skills/infer-domains.md** (or launch sub-agent). Input: candidate features (with suggested_domain from scan), current spec index (path from project config; specs[].file), existing registry file paths. Output: list of { domain_slug, registry_file, exists }.

### 2.2 Categorize features to domains

Run sub-skill **@.cursor/skills/nova-spec/sub-skills/categorize-features-to-domains.md** (or launch sub-agent). Input: candidate features, list of domains from 2.1, current registry contents. Output: feature_slug → domain_slug; per domain_slug: { requirement_ids[], asset_ids[], features[] }.

### 2.3 Update specs index

Run sub-skill **@.cursor/skills/nova-spec/sub-skills/update-specs-index.md** (or launch sub-agent). Input: list of registry files (from 2.1, one per domain). Output: updated spec index with specs[] set to that list. Write the spec index file (path from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml`).

### 2.4 Update registries

For **each** domain from 2.1, run sub-skill **@.cursor/skills/nova-spec/sub-skills/update-registry.md** (or launch one sub-agent per domain). Input: domain_slug; per-domain requirement_ids, asset_ids, features from 2.2; full project/schema/enums and full assets/requirements from canonical registry or scan for filtering. The sub-skill creates or overwrites `specs/<domain_slug>-registry.yml`. Internal logic (assets, requirements, traceability, validation, features, gap-fill, schema.updated_utc) is defined in **update-registry.md**.

### 2.5 Consistency check

After applying updates:

- Every id in `assets[].requires` and `assets[].symbols[].requires` must exist in `requirements[].id`.
- Every id in `requirements[].traceability.assets` must exist in `assets[].id`, and that asset must list that requirement in its `requires` (or in a symbol's `requires`).
- If the registry has a `features` array: every id in `features[].requirement_ids` must exist in `requirements[].id`; every id in `features[].asset_ids` must exist in `assets[].id`.
- Avoid orphan requirements (each requirement should have at least one asset); avoid orphan assets (each asset should have at least one requirement unless the schema explicitly allows it).
- Fix any mismatch before finishing.

---

## 3. Write spec files

Write updated spec index (path from project config) and all modified or created registry files. Re-read the consistency rules in 2.5 and fix any remaining issues.

---

## 4. Update mkdoc from specs

Sync the docs dir (from **@.cursor/project.yml** `paths.docs_dir`, default `mkdoc`) from the updated specs **comprehensively**. Run **@.cursor/skills/nova-spec/sub-skills/sync-mkdoc.md** (or perform inline): bootstrap mkdoc and mkdocs.yml at project root if missing; derive domains and features from each registry; write index, domain pages, architecture, runbooks, and feature dossiers per **@.cursor/skills/nova-code/mk/page-formats.md**; run **sync-cursor-docs** (step 3b) so all cursor pages exist and satisfy the Cursor gap-fill checklist. **Comprehensive update:** Create any missing doc; **update** any existing doc that lacks required sections, markup, or links (e.g. index Quick links including Cursor, feature dossier sub-pages, architecture diagram with vertical flow, cursor skill pages with TOC heading + expandable source + `skill-source-wrap`, diagrams using `flowchart TB`, nav including all current rules/skills/workflows). Skip only files that already satisfy every requirement. Record changed mkdoc paths.

---

## 5. Return

Produce the final report following **@.cursor/skills/nova-spec/output-format.md**: Outcome (Pass/Fail), Summary, Phases executed, Specs updated, Mkdoc updated, Domains, Issues/notes. Return that report to the user.
```

</details>


