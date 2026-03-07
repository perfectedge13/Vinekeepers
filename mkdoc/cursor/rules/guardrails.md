# Guardrails

## Summary

Pre-implementation checklist for spec-driven changes. Applied selectively via spec-workflow-core (before implementing any code change). If the request would violate any item, STOP and report.

## Key points

- **Do not delete requirements** — Mark deprecated or raise an issue; never remove a requirement that was previously present.
- **Do not remove required functionality** without explicit user permission.
- **Do not change schema files** or invent new keys in spec files; stay within existing schema.
- **Repair spec drift before coding** — If specs and code are out of sync, fix specs or restore assets first; prefer truth over consistency.
- **Consider and avoid anti-patterns** — Before and during implementation, read **anti_patterns** on impacted requirements and assets in the registry specs; avoid those approaches when coding.
