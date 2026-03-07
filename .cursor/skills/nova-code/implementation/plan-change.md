# Nova-plan-change

**Inputs**: User request, index, loaded registry specs. Optional: **@.cursor/project.yml** for `paths.docs_dir` (default `mkdoc`) when building change_context.

**Outputs**: Impact set (impacted spec files, impacted assets), `removal_or_rename` (boolean), **change_context** (one markdown document, size-capped, for implement).

## Instructions

1. Using the index and loaded registries, identify **impacted specs** via `change_triggers.paths` and `scope.primary_assets` relative to the user's request (files/features being changed).
2. Determine if the request involves **deletion or rename** of any file or asset. If yes, set `removal_or_rename: true`.
3. **Build change_context** (for the orchestrator to pass to implement):
   - **Scope for context:** From the user request and impacted specs/assets, collect feature names/slugs, requirement ids (e.g. `REQ-*-*`), asset ids, and file paths. Include every requirement and asset that is in the impacted set or referenced by name/id in the request. If the registry has a `features` structure, scope by affected features and include their requirement_ids and asset_ids. If nothing matches, scope to all requirements/assets in the impacted registry slice.
   - **Extract from specs (per scoped feature/requirement/asset):** For each in scope, collect only: **Feature:** id, slug, title, status, doc_path, summary (if any). **Requirements:** id, title, statement (one line), acceptance.criteria (short list), traceability.assets, validation.tests (id, title, intent, testClass/testMethod or one-line steps), **anti_patterns** (critical for implement; see **@.cursor/rules/guardrails.mdc**). **Assets:** id, path, role, requires (requirement ids), **anti_patterns** if present. No raw YAML.
   - **Extract from mkdoc:** Resolve docs_dir from project config (**@.cursor/skills/common/project-config.md**). For each scoped feature (or per-registry if no features): resolve doc path from feature.doc_path when present, else `features/domain/<domain_slug>/<feature_slug>.md` under docs_dir. For the feature summary and sub-pages (how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams): if the file exists, **excerpt** — keep `#`/`##` headings and first ~150–250 characters per section; prefer **decisions**, **contracts**, and **known-issues** for implementation context. If a page is missing, note "not present".
   - **Compression:** Apply a word/token cap (e.g. ~3000–4000 words) for the whole bundle. If over cap: truncate doc excerpts first, then shorten requirement statements to first sentence, then trim criteria to first 2–3 items. Emit a **single markdown document**.
   - **Output format (change_context):** Produce one markdown document with structure:
     - `# Change context (for plan_change / implement)`
     - `## Scope` — request-derived: list features/requirements/assets or "impacted registry slice"
     - `## Per feature` (or per requirement/asset group if no features): for each, **Feature** (title, status, doc_path, summary), **Requirements** (id, title, statement, criteria, tests, anti_patterns), **Assets** (id, path, role), **Doc excerpts** (decisions, contracts, known-issues: headings + first 150–250 chars or "not present").
4. **Return:** Pass or Fail; one-line summary; **impact set** (impacted registry spec file paths, impacted asset paths/ids, removal_or_rename); **change_context** (full markdown string of the document above, or a path to a temp file containing it). The orchestrator passes change_context to the **implement** step so it can use spec and doc constraints without re-reading full YAML and mkdoc.

Optional: add a short "Schema constraints" subsection (e.g. allowed requirement/asset keys from req-registry) in change_context if useful for implement.
