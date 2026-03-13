# Propose registry layout (sub-skill)

**Inputs (handoff):** (1) List of domain_slugs with their current registry file (from current specs.yml `domains[]` and registry contents). (2) Candidate features and their suggested_domain (optional, from scan). (3) Layout strategy from project config: **@.cursor/skills/common/project-config.md** (resolve `specs.registry_layout`; default `preserve`) — one of `preserve` | `one_file_per_domain` | (future) `explicit`.

**Outputs:** Authoritative **registry layout**: list of `{ registry_file, domain_slugs[], exists }` where `exists` is true if that registry file already exists (present in current specs.yml `specs[]` and on disk). This becomes the handoff to update-specs-index (specs[] and domains[]) and to update-registry (per-file domain groups).

---

## Logic

1. **Resolve strategy:** Read **@.cursor/project.yml** for `specs.registry_layout`. If absent, use `preserve`.
2. **preserve:** For each domain_slug in the current domain list (from infer-domains or from current specs.yml `domains[]`), keep its current `spec_file`. Build the layout by grouping domain_slugs by registry_file. For each registry_file in the result, set `exists = true` if that file is in current `specs[]` (and assume present on disk). Output one entry per registry_file with that file's domain_slugs and exists.
3. **one_file_per_domain:** For each domain_slug, set `registry_file = <domain_slug>-registry.yml`, `domain_slugs = [domain_slug]`. Set `exists = true` only for files that are in current specs[] and present. New files (e.g. env-registry.yml when today env is in core-registry) get `exists: false`. Deduplicate by registry_file; output one entry per registry_file.
4. **explicit** (when supported): Use the config list of `{ file, domains[] }`. Set `exists` by checking specs[] and filesystem. Output one entry per file with its domain_slugs and exists.
5. **Output format:** List of `{ registry_file, domain_slugs[], exists }`. Order: preserve existing file order for existing files; append new files. This list defines which registry files will be in specs[] and which domain_slug maps to which file (for domains[]).

---

## Return

Pass; list of `{ registry_file, domain_slugs[], exists }` for use by update-specs-index and by the orchestrator when calling update-registry per file.
