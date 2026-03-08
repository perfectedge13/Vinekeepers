# Update specs index (sub-skill)

**Inputs (handoff):** List of registry file paths to be in the index, plus the authoritative domain-to-registry mapping, e.g. registry files `[ "core-registry.yml", "connectors-registry.yml" ]` and domains `[ { slug: env, spec_file: core-registry.yml }, { slug: connectors, spec_file: connectors-registry.yml } ]`. Current spec index (path from **@.cursor/project.yml** `paths.specs_index` or `specs/specs.yml`) — scope, change_triggers, interfaces, validation.

**Outputs:** Updated specs.yml content or explicit instructions to write it. Ensures **domains** (the registry list) are updated.

## Logic

1. Set **specs** to an array of objects with key **file**: one entry per registry file path (e.g. `{ file: core-registry.yml }`, `{ file: connectors-registry.yml }`). Order: preserve existing order for existing files; append new registry files.
2. Keep **scope**, **change_triggers**, **interfaces**, **validation** unchanged (or merge from current file).
3. If **@specs/schema/specs-index.schema.json** defines optional **domains**: set **domains** from the authoritative domain-to-registry mapping in handoff, not from registry file stems. Preserve existing order when possible; otherwise sort by slug for stability. Conform to the optional schema property. If the schema has no domains property, skip this step.
4. Conform to **@specs/schema/specs-index.schema.json** (required: scope, change_triggers, specs, interfaces).

## Return

Pass; write updated spec index (path from project config) with the new specs[] list (and domains[] when the schema supports it).
