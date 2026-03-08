# Update registry (sub-skill)

**Inputs (handoff):** `registry_file`; one or more domain groups assigned to that registry, where each domain group has requirement_ids[], asset_ids[], and features[] (each feature: id, slug, title, requirement_ids, asset_ids, status, domain_slug). Full project/schema/enums and full assets/requirements/dependencies from existing registries or from the scan so you can filter to only the ids that belong in this registry file.

**Outputs:** Path to the registry file written (same directory as spec index, file = `registry_file`); list of requirement/asset/feature ids in this registry.

**Spec index path:** From **@.cursor/project.yml** `paths.specs_index` if present, else `specs/specs.yml`. Registry files live in the same directory as the spec index.

## Logic

1. **Target file:** &lt;directory of spec index&gt;/&lt;registry_file&gt;. Create or update.
2. **Schema block:** schema.id = req-registry, version, updated_utc = current UTC **only when the normalized registry content changed semantically**.
3. **Project, enums:** Copy from canonical registry (e.g. core-registry.yml) so all required enums and project are present.
4. **Dependencies:** Include only dependency items whose used_by_requirements intersect the requirement ids stored in this registry. If no registry exists yet, copy minimal enums and project from a compatible registry or from schema defaults.
5. **Assets:** Include only assets whose id is assigned to one of the domain groups stored in this registry. Each asset: id, kind, path, role, requires (only requirement ids that are stored in this registry). After features are fixed, derive and set optional `feature_ids` on each asset (feature IDs that list this asset in asset_ids or whose requirement_ids intersect this asset's requires). Preserve traceability.
6. **Requirements:** Include only requirements whose id is assigned to one of the domain groups stored in this registry. Full shape: id, title, statement, status, priority, type, behavior, acceptance, traceability (assets only in this registry), validation. Preserve bidirectional consistency with assets in this registry.
7. **Features:** Include the features[] for every domain group assigned to this registry. Each feature's requirement_ids and asset_ids must be subsets of the ids stored in this registry. Preserve the feature's explicit **domain_slug**; only fall back to the registry file stem when the feature has no domain slug yet. Derive **doc_path** from the feature's effective domain slug as `features/domain/<domain_slug>/<feature.slug>.md` (relative to docs_dir) unless an existing path remains valid and intentionally differs. Set **summary** when missing from the first requirement's title or first sentence of statement for that feature.
8. **Gap-fill:** Within this registry, every requirement has at least one asset; every asset has at least one requirement; every requirement has validation.tests. Add or fix as needed.
9. Conform to **@specs/schema/req-registry.schema.json**.

---

## Registry content (detail)

When writing each registry file, follow these rules. Conform to **@specs/schema/req-registry.schema.json**.

**Bootstrap if missing:** If the spec index (path from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml`) or a referenced registry does not exist, create it. Index: scope, change_triggers, specs, interfaces (and optional validation). Registry: schema (id, version, updated_utc), project, enums (all required enums with at least one value), dependencies.items, assets, requirements.

**Assets:**

- For each significant file or logical unit from the scan, ensure an asset exists: `id` (e.g. `ASSET-ENV-LOADER`, `ASSET-ROUTER`), `kind` (from enums: source, config, spec, workflow), `path`, `role`, `requires` (list of requirement ids).
- Do not remove existing assets. Add missing assets; update `role` or `requires` if the scan shows they implement different or additional requirements.
- Prefer stable, readable ids (e.g. `ASSET-<PACKAGE>-<NAME>` for source files).
- After features are fixed for this domain, set optional **feature_ids** on each asset: list of feature IDs where the feature's `asset_ids` contains this asset or the feature's `requirement_ids` intersects this asset's `requires`. Omit if empty is acceptable per schema.

**Requirements:**

- For each capability inferred from the repo (see candidate requirements), ensure a requirement exists: `id`, `title`, `statement`, `status`, `priority`, `type`, `behavior`, `acceptance`, `traceability`, `validation`.
- Use the registry's `enums` for status, priority, type. Canonical lifecycle vocabulary is `draft | active | deprecated`. During migration, treat legacy `accepted` as a readable alias for `active`, but do not write new `accepted` values.
- Add missing requirements; do not delete existing ones (mark deprecated if no longer relevant). Follow **@.cursor/skills/common/requirement-tracking.md** for when to create vs update vs split.

**Traceability:**

- For every requirement, set `traceability.assets` to the list of asset ids that implement it. For every requirement, set `traceability.symbols` (can be empty array `[]` if no symbols are defined).
- For every asset, set `requires` to the list of requirement ids that asset implements.
- Ensure bidirectional consistency: if asset A has `requires: [REQ-X]`, then REQ-X must have A in `traceability.assets`.

**Validation:**

- For each requirement, ensure `validation.tests` has at least one entry. Prefer `unitTestRef` when a test class/method clearly verifies that requirement: `id`, `title`, `intent`, `testClass`, optional `testMethod`, optional `preconditions`. Otherwise use `manualTest`: `id`, `title`, `intent`, `preconditions`, `steps` (each step: `run`, `expect`).
- Link test classes from the scan to requirements (e.g. EnvLoaderTest → requirement for "load .env at startup").

**dependencies:**

- If the scan finds library or tool usage (e.g. from pom.xml or similar), add or update `dependencies.items` with id, kind, name, scope, criticality, spec, install_hint, `used_by_requirements` (requirement ids that use this dependency). Use schema definitions for dependencyItem.

**Infer features from scan:**

- Use the **candidate features** from the scan. For each candidate feature (slug, title, requirement_ids, asset_ids, optional domain_slug), ensure a feature exists in the registry: `id` FEAT-&lt;UPPERCASE-SLUG&gt;, `slug`, `title`, `requirement_ids`, `asset_ids` (or omit for implied), `status` (draft | active | deprecated from requirements in that group), and explicit `domain_slug`. Add any missing feature; do not remove existing features that still have valid refs.
- **Coverage:** Every requirement must appear in at least one feature's `requirement_ids`; every asset should appear in at least one feature's `asset_ids` (or in a feature that omits asset_ids for "all implied"). If the registry currently has only one umbrella feature, add the per-area features and optionally keep the umbrella or split its requirement_ids/asset_ids across the new features.
- Conform to schema: feature id, slug, title, requirement_ids, optional asset_ids, status. **Optional feature fields for doc search:** Include **domain_slug** (the inferred owning domain, not necessarily the registry stem), **doc_path** (`features/domain/<domain_slug>/<slug>.md` relative to docs_dir unless an intentional override already exists), and **summary** (from first requirement title or first sentence of statement when missing). Preserve existing values if present and valid.

**Gap-fill:**

- **Assets:** Every significant file from the scan (main, key packages, config, spec) has an asset; add any missing. When features exist, ensure every asset has **feature_ids** (derived from features that reference this asset or its requirements).
- **Requirements:** Every capability theme from the scan has a requirement; add any missing.
- **Traceability:** Every asset has `requires` and appears in the traceability of those requirements; every requirement has traceability.assets and symbols.
- **Validation:** Every requirement has at least one validation test (unitTestRef or manualTest).
- **Features:** Every requirement and every asset is in at least one feature (see features inference above). When features exist, ensure every feature has **domain_slug** and a docs-dir-relative **doc_path** derived from the effective domain and feature slug unless an intentional override is already valid; set **summary** when missing from requirement data.

**schema.updated_utc:** Set to current UTC only when the normalized registry content changed semantically (format: `YYYY-MM-DDTHH:MM:SSZ`).

## Return

Pass; path specs/&lt;registry_file&gt;; list of requirement_ids, asset_ids, feature ids written.
