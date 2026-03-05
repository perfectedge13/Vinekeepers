# Nova-update-readme (common)

**Inputs**: Change summary, impacted areas, root README path (default README.md).
**Outputs**: All impacted artifact categories reviewed and updated as needed; README and other docs updated; summary of changes.

## Strong rule

**Do not assume only code changes.** Every change must trigger a review of code, specs, validation, docs, config, and operational guidance. Only the **impacted** categories need edits, but **all** must be checked.

## Review impacted categories

For every change, review and update (where impacted) all of the following. Check each category; edit only those that are affected by the change.

1. **Implementation** — Source code, entrypoints, interfaces, shared utilities, and affected modules.
2. **Specs** — Requirements, acceptance criteria, constraints, traceability, dependencies, and status (handled by update-specs / reconcile; this step verifies docs match).
3. **Validation** — Test cases, manual validation steps, automated checks, expected outcomes, and test coverage mapping.
4. **Documentation** — README, usage docs, setup instructions, examples, workflows, and any user-facing guidance. Update root README.md for commands, outputs, prereqs, options, new/removed files, and deprecations. Do not remove content unless functionality was explicitly removed.
5. **Configuration** — Config files, environment variables, flags, defaults, and runtime assumptions.
6. **Commands / tooling** — CLI commands, scripts, build steps, task runners, and developer workflows (e.g. from spec index `interfaces.cli` and `validation.commands`).
7. **Data contracts** — Schemas, request/response shapes, stored fields, payloads, and data mapping assumptions.
8. **Architecture / design artifacts** — Diagrams, flow/logic docs, system behavior descriptions, and component relationships.
9. **Operational behavior** — Scheduling, job behavior, startup/shutdown, monitoring, logging, and troubleshooting guidance.
10. **Dependencies / integrations** — External services, internal integrations, libraries, version assumptions, and compatibility notes.
11. **Examples / reference artifacts** — Sample inputs, sample outputs, templates, reference commands, and example scenarios.
12. **Change impact records** — Changelog notes, deprecations, migration notes, known issues, and follow-up actions.

## Instructions

1. **Check all categories** — Using the change summary, determine which of the 12 categories above are impacted. Do not assume only implementation changed; consider specs, validation, documentation, configuration, commands, data contracts, architecture, operational behavior, dependencies, examples, and changelog/deprecations.
2. **Update only impacted** — For each impacted category, make the necessary edits (code is usually handled in the Implement step; specs in Update specs; this step focuses on documentation and cross-cutting updates). Ensure README and other user-facing docs reflect the change (commands, config, setup, examples, workflows). Update config/docs/tooling/architecture/operational/dependencies/examples/changelog artifacts when they are impacted.
3. **Documentation (README)** — Maintain root README.md and any usage/setup/workflow docs so they stay accurate: entrypoints, build/run commands, configuration and env vars, project layout, skill/workflow description. Consider `anti_patterns` in specs when editing.
4. **Output** — Summarize which categories were checked, which were impacted, and what was updated (e.g. "Updated README Run section and config env vars; validation and specs unchanged; no changelog entry added."). If no updates were needed after review, say so and note that all categories were checked.
