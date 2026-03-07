# Nova-wiki-prepare

**Inputs:** User request, handoff from nova-code. Handoff should include **plan_change** (impacted registry spec file paths, impacted asset paths/ids) and **implement** (list of changed file paths) when provided by the orchestrator. Spec index path from **@.cursor/project.yml** `paths.specs_index` if present, else `specs/specs.yml`.

**Outputs:** Wiki context object: `{ domains, features, updates, affected_features }`. No Wiki.js API calls.

## Instructions

1. Read the project spec index (path from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml`) and load every registry listed in `specs[].file`. **Wiki–spec alignment:** The feature list and domain list must match the current spec index and registries; if a registry was removed from specs, do not include it (do not create new wiki pages for removed registries).

2. **Domains:** Derive from registry ids (e.g. `vinekeepers.core` → slug `core`, `vinekeepers.bots.xyz` → `bots-xyz`). List: domain slug, display name, description (from `project.name` / `project.description`).

3. **Features:** For v1, one feature per domain (registry name/description). List: feature slug, domain slug, display name, **status**. **Feature status (draft/active/deprecated):** Derive from the registry's requirements: if any requirement with status `deprecated` applies to this feature, or the registry is marked deprecated, set feature status **deprecated**; else if any requirement is `draft`, set **draft**; else **active**.

4. **Affected_features (0..n):** If handoff includes impacted registry paths and/or impacted asset paths (or changed file paths from implement): for each such path, determine which registry owns it (each registry's `assets[].path`; match by path or by which registry file lists that asset). Collect the set of registries that own at least one impacted path; map each registry to its domain/feature. Output **affected_features** = list of (domain_slug, feature_slug) for those registries. If handoff has no impact data, set **affected_features** = all features (full sync). So: 0 = no features touched when impact set is empty; n = only features whose registry/assets were touched when impact data is present.

5. **Updates:** From the user request and handoff (implement summary, change_triggers), classify what to update:
   - New feature (new registry or new requirement group) → create dossier
   - Behavior change → update feature summary + add decision note
   - Bug/root cause → update known-issues or runbook
   - Schema/interface change → update contracts
   - Major refactor → update architecture + affected feature pages
   - Deprecation → mark status and link successor

6. Output the wiki context `{ domains, features, updates, affected_features }` so the next steps (wiki_index, wiki_architecture, wiki_runbooks, wiki_feature_dossiers) can use it. Return: Pass, and the short summary of domains count, features count, update types, and affected_features count (or "all").
