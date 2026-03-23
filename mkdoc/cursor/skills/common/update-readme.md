# Update readme (common)

## Summary

Classifies all 12 artifact categories from cumulative handoff to decide whether **root README.md** is stale, then updates **only README.md** when needed. **Does not** edit the project docs directory (e.g. `mkdoc/`); that is the **mk** sub-skill in the nova-code workflow.

## Key points

- **Scope:** Edits are **README.md only** (repo root by default). Docs dir, feature dossiers, and MkDocs nav are owned by **mk** (`@.cursor/skills/nova-code/mk/SKILL.md`).
- **Evidence first:** Start from `plan_change`, `implement`, `update_tests`, and `update_specs` handoff instead of rediscovering the change from scratch.
- **12 categories:** Still classified so README accuracy is judged holistically; other artifacts are not edited in this step.
- **Fast path:** If the handoff shows no README-facing impact, report classification complete and **no README.md edits**.
- **Reads:** Open **README.md** when implicated; other files (specs, config, docs-dir pages) are **read-only** for fact-checking — **no docs-dir writes** here.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-update-readme (common)

**Inputs**: Change summary, impacted areas, root README path (default README.md), and upstream handoff when available: **plan_change** (impacted registry paths, impacted asset paths/ids, change_context), **implement** (changed source file paths), **update_tests** (changed test file paths), **update_specs** (impacted spec paths and short summary of spec/doc-facing deltas).

**Outputs**: All impacted artifact categories classified; **only `README.md` edited** when the evidence shows it is out of date; summary of categories checked, whether README was updated, and whether the fast path was used.

## Scope (hard boundary)

This step **updates the root README only** (default `README.md` at the repo root unless the project defines another single entry README path).

**Do not** create, edit, or sync files under the project **docs directory** (e.g. `mkdoc/` or whatever **`paths.docs_dir`** is in **@.cursor/project.yml**). Long-form docs, feature dossiers, architecture pages, and MkDocs nav are owned by the **mk** step in the nova-code workflow — see **@.cursor/skills/nova-code/mk/SKILL.md** and **@.cursor/workflows/nova-code.yml** (`mk`).

If handoff shows that narrative docs beyond the README need changes, **note that in your step output** for the orchestrator; do not perform those edits here.

## Strong rule

**Do not assume only code changes.** Every change must still be **classified** against code, specs, validation, docs, config, and operational guidance so you can tell whether **README** text is stale. Only **README.md** receives edits in this step. Start from upstream evidence first; do not default to a broad repo scan.

## Review impacted categories (classification → README only)

For every change, **classify** all of the following. Use the classification to decide whether **README.md** needs updates — not to edit other artifacts (those belong to implement, update_specs, reconcile, **mk**, etc.).

1. **Implementation** — Source code, entrypoints, interfaces, shared utilities, and affected modules.
2. **Specs** — Requirements, acceptance criteria, constraints, traceability, dependencies, and status (handled by update-specs / reconcile; this step checks whether README claims still match).
3. **Validation** — Test cases, manual validation steps, automated checks, expected outcomes, and test coverage mapping.
4. **Documentation (README surface)** — What contributors and operators read in **README.md**: commands, outputs, prereqs, options, layout, quickstart. Do not treat the docs dir as in scope for **edits** here.
5. **Configuration** — Config files, environment variables, flags, defaults, and runtime assumptions (reflect in README if user-facing commands or env vars changed).
6. **Commands / tooling** — CLI commands, scripts, build steps, task runners, and developer workflows (e.g. from spec index `interfaces.cli` and `validation.commands`).
7. **Data contracts** — Schemas, request/response shapes, stored fields, payloads, and data mapping assumptions.
8. **Architecture / design artifacts** — High-level behavior the README summarizes (detail lives in specs and **mk** docs, not edited here).
9. **Operational behavior** — Scheduling, job behavior, startup/shutdown, monitoring, logging, and troubleshooting guidance **as summarized in README** when relevant.
10. **Dependencies / integrations** — External services, libraries, version assumptions, and compatibility notes **if README documents them**.
11. **Examples / reference artifacts** — Sample commands or flows **if README lists them**.
12. **Change impact records** — Deprecations, migrations, or follow-ups **if README should mention them**.

## Evidence-first procedure

1. **Build an impact packet** — Start from the upstream handoff instead of re-discovering the change from scratch. Use: changed source paths from **implement**; changed test paths from **update_tests**; impacted registries/assets plus **change_context** from **plan_change**; spec-facing deltas from **update_specs** when available.
2. **Classify categories before deep reads** — Map the impact packet to the 12 categories above. Treat this classification as the mandatory "checked all categories" step. If the packet already shows a category is not implicated for **README accuracy**, do not open unrelated files just to confirm it again.
3. **Deep-read README when implicated** — When the impact packet suggests README may be stale (e.g. handoff touches `README.md`, command/config/entrypoint changes, or user-facing behavior), open and verify **README.md**. You may **read** specs, config, or docs-dir pages **read-only** to fact-check README wording; **do not** edit the docs dir in this step.
4. **Fast path for non-README-facing changes** — If the impact packet is limited to source and test files and does not indicate anything that would change README commands, layout, prereqs, or operator guidance, do **not** perform a broad README review. Instead, return a concise result stating that all 12 categories were classified from the handoff, no README-facing impact was found, and **no README.md edits** were needed.
5. **Targeted reads only** — When README may need updates, open **README.md** and only the minimal other files (read-only) needed to verify facts. Reuse **change_context** before reading large specs or docs again.
6. **Edit only README.md** — Apply edits **only** to the root README. Do not remove README content unless functionality was explicitly removed. Consider `anti_patterns` in specs when editing README guidance.
7. **Output** — Summarize which categories were classified, which implied README updates, which files were opened (read-only vs README written), and whether the fast path was used (e.g. "Classified all 12 categories; commands/config impacted; updated README Run section." or "Fast path: no README-facing impact; no README.md edits.").
```

</details>

