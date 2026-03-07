# Update specs index (sub-skill)

**Inputs (handoff):** List of registry file paths (one per domain) to be in the index, e.g. [ "core-registry.yml", "connectors-registry.yml" ]. Current spec index (path from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml`) — scope, change_triggers, interfaces, validation.

**Outputs:** Updated specs.yml content or explicit instructions to write it. Ensures **domains** (the registry list) are updated.

## Logic

1. Set **specs** to an array of objects with key **file**: one entry per registry file path (e.g. `{ file: core-registry.yml }`, `{ file: connectors-registry.yml }`). Order: preserve existing order for existing files; append new registry files.
2. Keep **scope**, **change_triggers**, **interfaces**, **validation** unchanged (or merge from current file).
3. If **@specs/schema/specs-index.schema.json** defines optional **domains**: set **domains** from the current **specs[]** list — for each `specs[].file`, add `{ slug: <file stem>, spec_file: <file> }` (e.g. `core-registry.yml` → slug `core`). Conform to the optional schema property. If the schema has no domains property, skip this step.
4. Conform to **@specs/schema/specs-index.schema.json** (required: scope, change_triggers, specs, interfaces).

## Return

Pass; write updated spec index (path from project config) with the new specs[] list (and domains[] when the schema supports it).
