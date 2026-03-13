# Nova-wiki-runbooks

**Inputs:** Wiki context from wiki_prepare (updates may include "bug/root cause" → update troubleshooting or recovery), handoff. Credentials from env (WIKIJS_*). If not in process env, load from project root .env per **@.cursor/skills/nova-wiki/auth.md**.

**Outputs:** Pages created or updated: &lt;path_prefix&gt;/runbooks, &lt;path_prefix&gt;/runbooks/operational, and siblings (troubleshooting, recovery, maintenance, configuring-bots). Pass/Fail and count. **Wiki path prefix** from **@.cursor/project.yml** `wiki.path_prefix`; see **@.cursor/skills/common/project-config.md**.

## Instructions

1. Read **@.cursor/skills/nova-wiki/page-formats.md** for the runbooks page types. Each has a required Title and Body structure (Index, Procedures, or Entries). Use **path_prefix** from project config for all paths.
2. **Authenticate** per **@.cursor/skills/nova-wiki/auth.md**: use WIKIJS_API_KEY as Bearer token, or login with `authentication { login(strategy: "local", username: $username, password: $password) { jwt } }` and use the returned **jwt** as `Authorization: Bearer <jwt>`. If credentials unset, return Fail.
3. **Per-runbook check:** For each runbook in the canonical list (page-formats), determine if it needs updates: (a) Page missing or missing required sections; (b) Handoff indicates an update for that runbook type; (c) When the runbook has a source of truth (e.g. config/bots.yaml for configuring-bots), consider whether content should be refreshed. Then create or update each runbook that needs changes.
4. For each page (paths use path_prefix from project config):
   - **`<path_prefix>/runbooks`** — Ensure exists. Title: Runbooks. Body: # Index (links to operational, troubleshooting, recovery, maintenance, configuring-bots), short intro.
   - **`<path_prefix>/runbooks/operational`** — Ensure exists. Title: Operational. Body: # Procedures (heading per procedure; steps underneath). Add or update from handoff if "operational procedure change."
   - **`<path_prefix>/runbooks/troubleshooting`** — Ensure exists. Title: Troubleshooting. Body: # Entries (each ## &lt;symptom or issue&gt; then cause, steps, resolution). If handoff indicates bug/root cause or incident, add or update an entry.
   - **`<path_prefix>/runbooks/recovery`** — Ensure exists. Title: Recovery. Body: # Procedures (recovery scenarios and steps). Update from handoff if recovery procedure or incident.
   - **`<path_prefix>/runbooks/maintenance`** — Ensure exists. Title: Maintenance. Body: # Procedures (maintenance tasks, schedule, steps). Update from handoff if maintenance procedure change.
   - **`<path_prefix>/runbooks/configuring-bots`** — Ensure exists. Title: Configuring bots. Body: # Procedures (how to edit config/bots.yaml: bots, routing, persona, workflow refs). Optionally refresh from config/bots.yaml when present.
5. Use GraphQL variables for page content to avoid escaping issues (see auth.md). If no handoff content for a sub-page, ensure the page exists with the required section headings (placeholder content is fine).
6. Return: Pass (or Fail), and one line e.g. "Updated runbooks (6 pages)."
