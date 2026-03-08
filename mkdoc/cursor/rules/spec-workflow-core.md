# Spec-workflow-core

## Summary

Core spec-driven workflow: gates, removal/rename protocol, and final output. Guardrails are a separate rule (guardrails.mdc) applied before implementing. Validation commands come from the spec index or project config.

## Key points

- **Schema Gate (0a):** Spec index and registries must validate against JSON Schema; run gate command from project config or `npm run validate-specs`. Stop on failure.
- **Spec Drift Gate (0b):** Index and registry integrity (paths exist, refs valid); run drift command or validate manually. Stop on failure.
- **Workflow (1):** Read specs → pre-change lock → implement → update specs (schema keys only) → post-change schema gate → run tests → static analysis → re-validate integrity.
- **Removal/rename (2):** Reference map first; apply removal/rename; update all references; re-run gates; verify.
- **Shell:** Use index or project.yml shell; Windows PowerShell: avoid `&&`, use `Set-Location` or `cmd /c`.
- **Final response:** Summary, changed files, schema result, test results, static analysis, any spec issues.


