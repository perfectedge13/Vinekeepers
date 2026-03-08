# Nova-update-specs

**Inputs:** Change summary, impacted spec paths (from plan_change).

**Outputs:** Updated requirements, validation tests, traceability, acceptance, assets, dependencies, interfaces/change_triggers, and anti_patterns (all within existing schema keys).

## Process (ordered steps)

**Bootstrap when specs missing:** If **specs/specs.yml** does not exist, create it per **@specs/schema/specs-index.schema.json**: include `scope.primary_assets`, `change_triggers.paths`, `specs` (e.g. core-registry.yml), `interfaces.cli`. If a registry file referenced in the index does not exist, create it per **@specs/schema/req-registry.schema.json** (schema, project, enums, dependencies, assets, requirements). In **Scope** below: if plan_change had no impacted registry (no index), treat the bootstrap-created registry as impacted.

1. **Scope** — From plan_change output, list impacted registry files and impacted assets/requirements.

2. **Requirement decisions** — For each impacted registry, decide whether to **create new**, **update existing**, or **split** requirements using the rules in **@.cursor/skills/common/requirement-tracking.md**. For splits, follow the "Procedure for splitting a requirement" in that doc.

3. **Apply updates** (in this order):
   - Add or update requirement entries: include **id**, **title**, **statement**, **status**, **priority**, **type**, **behavior**, **acceptance** (and optional **tags**). Values for priority and type must come from the registry's `enums`.
   - Tests are **per-requirement**: each requirement has its own `validation.tests`; when splitting, assign each test to the new requirement it verifies.
   - Update assets (path, role, **requires**, **symbols**). Ensure each asset's `requires` and each **assets[].symbols[].requires** point only to existing requirement ids.
   - Update traceability (requirements ↔ assets ↔ symbols).
   - Update validation.tests (unitTestRef/manualTest) and link tests to the correct requirement(s).
   - Update dependencies.items[].used_by_requirements.
   - **Update anti_patterns** — Consider recent exchanges in the agent chat (rework, change requests, corrections). If something **did not work** (e.g. failed approach, rework requested, user correction), add a short, factual entry to the **anti_patterns** array of the relevant requirement or asset. Record only what didn't work; do not record preferences or style choices. Keep each entry one clear sentence (e.g. "Do not use X because Y failed"). If nothing in the chat qualifies, leave anti_patterns unchanged.
   - When editing a registry, update **schema.updated_utc** only if the normalized registry content changed semantically (and optionally bump version when appropriate). Do not churn the timestamp on a no-op rewrite.
   - If index scope/entrypoints or touched files changed: update index (interfaces, change_triggers, primary_assets as per schema).

4. **Consistency check** — After applying updates, ensure: every requirement id in assets[].requires and assets[].symbols[].requires exists in requirements[].id; every requirement's traceability.assets lists exactly the assets that implement it (i.e. that list this requirement in their requires or symbols[].requires). Fix any mismatch before proceeding.

5. **Constraints** — Do not invent new keys. Do not delete requirements; mark deprecated or raise an issue. If an update would require a new key, raise an issue.

6. **Wiki–spec consistency** — When a change is driven by a documented decision or deprecation that appears in the wiki, ensure the corresponding requirement or asset status in the registry is updated (e.g. deprecated, or new requirement) so specs remain the source of truth.
