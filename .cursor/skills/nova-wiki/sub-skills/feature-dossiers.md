# Nova-wiki-feature-dossiers

**Inputs:** Wiki context from wiki_prepare (domains, features, updates, **affected_features**), handoff (user request, implement summary). Credentials from env (WIKIJS_*).

**Outputs:** For each feature in scope: page `/vinekeepers/features/domain/<domain>/<feature-name>` and seven sub-pages (how-it-works, change-log, known-issues, decisions, contracts, tests, diagrams). Pass/Fail and count of dossiers updated.

## Instructions

1. Read **@.cursor/skills/nova-wiki/page-formats.md** for every feature page type (feature summary and the seven sub-pages). Follow the exact section order and headings.
2. **Authenticate** per **@.cursor/skills/nova-wiki/auth.md**: use WIKIJS_API_KEY as Bearer token, or login with `authentication { login(strategy: "local", username: $username, password: $password) { jwt } }` and use the returned **jwt** as `Authorization: Bearer <jwt>`. If credentials unset, return Fail.
3. **Scope:** When wiki context includes **affected_features** (a list of domain/feature pairs), iterate over **affected_features** only. When **affected_features** is absent or denotes "all," iterate over the full features list from prepare (backward compatible).
4. For **each feature** in scope (see step 3):
   - **Feature summary** — Path: `/vinekeepers/features/domain/<domain-slug>/<feature-slug>`. Title: feature display name. Body: # Status (draft/active/deprecated; if deprecated link successor), # Summary, # Key assets (from spec assets), # Sub-pages (bulleted links to the seven sub-pages). Create if new feature; update if behavior change, refactor, or deprecation.
   - **how-it-works** — Path: .../how-it-works. Create/update with # Overview, # Flow, # Inputs and outputs. **Must** be filled from the current registry spec (behavior/requirements); do not invent content that is not in the spec.
   - **change-log** — Path: .../change-log. Create/update with # Entries (## YYYY-MM-DD). If handoff says "behavior change" or "any change," append a new entry with today's date and short description.
   - **known-issues** — Path: .../known-issues. Create/update with # Active, # Resolved. If handoff says "bug/root cause," add or update an entry.
   - **decisions** — Path: .../decisions. Create/update with # Entries (## YYYY-MM-DD — title). If handoff says "behavior change," add a decision note.
   - **contracts** — Path: .../contracts. Create/update with # APIs, # Schemas, # Interfaces. **Must** be filled from the current registry spec; do not invent content that is not in the spec. If handoff says "schema/interface change," update from spec.
   - **tests** — Path: .../tests. Create/update with # Coverage, # Test list (from spec validation.tests). **Must** be filled from the current registry spec (validation.tests). Update when validation.tests change.
   - **diagrams** — Path: .../diagrams. Create/update with # Architecture (link or Mermaid), # Feature flow. Update on refactor/flow change.
5. Use GraphQL variables for all page content (see auth.md). For pages.update you need the page id from pages.list or pages.single. Apply maintenance rules: new feature → create full dossier; behavior change → update summary + decisions; schema change → contracts; deprecation → set status and link successor. **Key assets, contracts, tests, and how-it-works must be filled from the current registry spec; do not invent content that is not in the spec.**
6. Return: Pass (or Fail), and one line e.g. "Updated 3 feature dossiers (24 pages)."
