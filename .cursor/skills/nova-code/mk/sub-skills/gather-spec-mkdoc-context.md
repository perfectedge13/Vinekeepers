# Nova-mk-gather-spec-mkdoc-context

**Inputs:** Mk context from **mk_prepare** (domains, features, updates, **affected_features**). Handoff: user request, plan_change/implement when available. Spec index path from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml`; **docs_dir** from `paths.docs_dir` (default `mkdoc`).

**Outputs:** **Compressed context** (single markdown document or structured bundle) added to handoff for subsequent mk steps. Pass/Fail and one-line summary (e.g. "Compressed spec+doc for N features.").

**References:** **@.cursor/skills/common/project-config.md**, **@specs/schema/req-registry.schema.json**. Registry feature/requirement/asset shape per schema.

---

## Purpose

Scan specs (index + registries) and mkdoc (feature dossiers) for information relevant to the upcoming doc update. Compress that information into one handoff artifact so downstream steps (especially **mk_feature_dossiers**) can fill and update docs without re-reading full YAML and markdown.

---

## 1. Scope

- If **affected_features** is a non-empty list of (domain_slug, feature_slug) pairs, scope to those features only.
- If **affected_features** is "all" or absent or empty, scope to every feature in the **features** list from prepare (all domains).

---

## 2. Load specs

- Read the spec index (path from project config) and load every registry listed in `specs[].file`.
- For each scoped feature, locate its registry (by domain_slug = registry file stem) and the feature entry (by slug or id).

---

## 3. Extract from specs (per scoped feature)

For each feature in scope, collect **only** the following (no full YAML):

**Feature:** id, slug, title, status, doc_path (if present), domain_slug (if present), summary (if present).

**Requirements** (from feature's requirement_ids; resolve from the same registry):

- For each requirement: **id**, **title**, **statement** (one line), **acceptance.criteria** (list of strings), **traceability.assets** (asset ids), **validation.tests** (for each test: id, title, intent, and either testClass/testMethod or a one-line summary of steps). Omit full `behavior` sub-objects; if how-it-works needs rules, include a single short "rules" line derived from behavior.rules.

**Assets** (from feature's asset_ids, or from requirement traceability for those requirements):

- For each asset: **id**, **path**, **role**, **requires** (requirement ids).

---

## 4. Extract from mkdoc (per scoped feature)

- Resolve the feature summary path: use **feature.doc_path** from the registry when present (path = docs_dir + "/" + doc_path). Otherwise use `features/domain/<domain_slug>/<feature_slug>.md` under docs_dir.
- For the feature summary file and each of the seven sub-pages (how-it-works.md, change-log.md, known-issues.md, decisions.md, contracts.md, tests.md, diagrams.md): if the file exists, read it and **excerpt** — keep section headings (# and ##) and the **first 150–250 characters** (or first 2–3 lines) of content under each heading. If the file is short (e.g. under 500 characters), keep full content.
- If a page does not exist, note "not present" for that page.

---

## 5. Compression rules

- Do not emit raw YAML. Emit only the fields listed above in the output format below.
- Optional **token/word cap** for the whole bundle (e.g. max ~4000 words or ~15k tokens). If over cap, truncate doc excerpts first, then shorten requirement statements to first sentence.
- Output a **single artifact** in the format below.

---

## 6. Output format (compressed context)

Produce **one markdown document** so downstream steps can consume it as handoff text. Structure:

```markdown
# Compressed spec + mkdoc context (for update)

## Scope
Affected features: <list of domain_slug/feature_slug or "all">

## Per feature

### <domain_slug>/<feature_slug>
- **Feature:** <title>, <status>, doc_path: <path or "derived">, summary: <if any>
- **Requirements:**
  - <id>: <title> — <statement (one line)>. Criteria: <short list>. Tests: <id, title, intent; testClass or steps summary>
  (repeat for each requirement)
- **Assets:**
  - <id>: path=<path>, role=<role>
  (repeat for each asset)
- **Existing doc excerpts:**
  - **summary:** <section headings + first 150–250 chars per section, or "not present">
  - **how-it-works:** ...
  - **change-log:** ...
  - **known-issues:** ...
  - **decisions:** ...
  - **contracts:** ...
  - **tests:** ...
  - **diagrams:** ...

(Repeat ### for each scoped feature.)
```

Ensure the document is well-bounded; use the optional word/token cap if the scope is large.

---

## 7. Return

- **Pass** and a one-line summary (e.g. "Compressed spec+doc for N features.").
- Add to handoff (for the orchestrator to pass to subsequent steps): **compressed_context** = the full markdown string of the document above. Alternatively, write the document to a temp file under the project and pass **compressed_context_path** in handoff; the orchestrator then includes that path or the file contents in handoff to mk_index, mk_architecture, mk_runbooks, mk_feature_dossiers.

---

## Approach notes

- **Scope strictly to affected features** when not "all" to keep the bundle small.
- **Structured extract only** — no full behavior/acceptance sub-objects unless a one-line summary is needed for how-it-works.
- **Doc excerpts** (section + first 150–250 chars) avoid sending entire dossiers; steps can re-read full files when needed.
- **Markdown output** is easier for LLM sub-agents to use in a single handoff than large JSON.
