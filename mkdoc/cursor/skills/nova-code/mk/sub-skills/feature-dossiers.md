# Feature dossiers (mk)

## Summary

For each feature ensures summary + 7 sub-files (how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams); sets status; follows page-formats.md.

## Key points

- See **.cursor/skills/nova-code/mk/sub-skills/feature-dossiers.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-mk-feature-dossiers

**Inputs:** Mk context from mk_prepare (domains, features, updates, **affected_features**), handoff (user request, implement summary).

**Outputs:** For each feature in scope: file `mkdoc/features/domain/<domain>/<feature-name>.md` and seven sub-files (how-it-works.md, change-log.md, known-issues.md, decisions.md, contracts.md, tests.md, diagrams.md). Pass/Fail and count of dossiers updated.

## Instructions

1. Read **@.cursor/skills/nova-code/mk/page-formats.md** for every feature file type (feature summary and the seven sub-pages). Follow the exact section order and headings.
2. **Scope:** When mk context includes **affected_features** (a list of domain/feature pairs), iterate over **affected_features** only. When **affected_features** is absent or denotes "all," iterate over the full features list from prepare.
3. For **each feature** in scope, create the directory **mkdoc/features/domain/<domain>/<feature-name>/** if needed. Then:
   - **Feature summary** — File: `mkdoc/features/domain/<domain>/<feature-name>.md`. Content: H1 feature display name, # Status (draft/active/deprecated; if deprecated link successor), # Summary, # Key assets (from spec assets), # Sub-pages (bulleted links to the seven sub-pages). Create if new feature; update if behavior change, refactor, or deprecation. Write or overwrite.
   - **how-it-works.md** — File: `mkdoc/features/domain/<domain>/<feature-name>/how-it-works.md`. Content: # How it works, # Overview, # Flow, # Inputs and outputs. **Must** be filled from the current registry spec (behavior/requirements); do not invent content that is not in the spec. Write or overwrite.
   - **change-log.md** — File: `.../change-log.md`. Content: # Change log, # Entries (## YYYY-MM-DD). If handoff says "behavior change" or "any change," append a new entry with today's date and short description. Write or overwrite.
   - **known-issues.md** — File: `.../known-issues.md`. Content: # Known issues, # Active, # Resolved. If handoff says "bug/root cause," add or update an entry. Write or overwrite.
   - **decisions.md** — File: `.../decisions.md`. Content: # Decisions, # Entries (## YYYY-MM-DD — title). If handoff says "behavior change," add a decision note. Write or overwrite.
   - **contracts.md** — File: `.../contracts.md`. Content: # Contracts, # APIs, # Schemas, # Interfaces. **Must** be filled from the current registry spec; do not invent content that is not in the spec. Write or overwrite.
   - **tests.md** — File: `.../tests.md`. Content: # Tests, # Coverage, # Test list (from spec validation.tests). **Must** be filled from the current registry spec (validation.tests). Write or overwrite.
   - **diagrams.md** — File: `.../diagrams.md`. Content: # Diagrams, # Architecture (link or Mermaid), # Feature flow. When linking to the main architecture page, use `../../../../architecture.md`. Write or overwrite.
4. Use relative links (e.g. from feature summary to `how-it-works.md`, `change-log.md`, etc.). Apply maintenance rules: new feature → create full dossier; behavior change → update summary + decisions; schema change → contracts; deprecation → set status and link successor. **Key assets, contracts, tests, and how-it-works must be filled from the current registry spec; do not invent content that is not in the spec.**
5. Return: Pass (or Fail), and one line e.g. "Updated 3 feature dossiers (24 files)."
```

</details>


