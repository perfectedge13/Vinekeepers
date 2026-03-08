# Cursor Cloud Specific Instructions

Use the repository's spec-driven workflow for any implementation run:

- Follow `.cursor/rules/spec-workflow.mdc`, `.cursor/rules/spec-workflow-core.mdc`, and `.cursor/rules/guardrails.mdc`.
- Read `specs/specs.yml` first and keep impacted registry specs in sync with code.
- Do not invent new spec keys or delete active requirements (treat legacy `accepted` as migration-only if it still appears).
- Run the repo validation commands before finishing:
  - `npm run validate-specs`
  - `npm run validate-drift`
  - `mvn test`
  - `mvn compile`
  - `npm run validate-docs`
- Update `README.md` and the relevant docs-dir feature pages (currently `mkdoc/`) when runtime behavior changes.
- Prefer repo-local Cursor skills and workflows when they fit the task, especially `nova-code`.
- Assume Luna-triggered runs should work on a feature branch and finish with a pull request.
