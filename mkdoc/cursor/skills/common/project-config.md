# Project config (common)

## Summary

Single reference for resolving project name, paths (specs_index, docs_dir, mkdocs_file), validation commands, wiki path prefix, scan roots, and gate commands from .cursor/project.yml with fallbacks.

## Key points

- Project name: project_name → first registry project.name → README title. Specs index: paths.specs_index → specs/specs.yml. Docs dir: paths.docs_dir → mkdoc.
- Validation: prefer specs validation block; else project.yml validation_defaults; else report "validation not configured".
- Gate commands and scan roots from project.yml with documented defaults. Portability checklist when copying .cursor/ to another project.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Project config (common)

When a skill needs **project name**, **paths**, **validation commands**, or **wiki path prefix**, read **@.cursor/project.yml**. If a key is missing, use the fallback below.

## Resolving values

1. **Project name:** `project_name` from project.yml → first registry file's `project.name` → README title or "Project".
2. **Specs index path:** `paths.specs_index` from project.yml → `specs/specs.yml`.
3. **Docs dir:** `paths.docs_dir` from project.yml → `mkdoc`.
4. **MkDocs file:** `paths.mkdocs_file` from project.yml → `mkdocs.yml` (at project root).
5. **Docs site name:** `docs.site_name` from project.yml → project name (from above).
6. **Wiki path prefix:** `wiki.path_prefix` from project.yml → slug of project name (e.g. lowercase, single segment like `vinekeepers`).
7. **Validation (test, static_analysis, shell, entrypoint):** Prefer **specs/specs.yml** `validation` block. If absent, use project.yml `validation_defaults.test`, `validation_defaults.static_analysis`, `validation_defaults.shell`, `validation_defaults.entrypoint`. If still absent, report "validation not configured" and do not assume Maven or any stack.
8. **Scan roots:** `scan.root_config_files` and `scan.source_dirs` from project.yml → if absent, default to `[pom.xml, README.md, .env.example]` and `[src/main/java, src/test/java]`.
9. **Gate commands:** `validation.gate_validate_specs` and `validation.gate_validate_drift` from project.yml → if absent, workflow uses its default (e.g. `npm run validate-specs`).

Reference this doc from skills that need any of these values.

## Portability checklist (when copying .cursor/ to another project)

1. Copy `.cursor/` (and optionally `specs/`) to the new repo.
2. Copy and edit **.cursor/project.yml**: set `project_name`, `paths`, `docs.site_name`, `wiki.path_prefix`, `validation_defaults` (and optional `scan`, gate commands).
3. Ensure `specs/specs.yml` exists and has `validation` if you want tests/static analysis without project.yml defaults.
4. If using npm gate commands, ensure `package.json` has `validate-specs` and `validate-drift` scripts, or set gate commands in project.yml and reference them from the workflow.
```

</details>
