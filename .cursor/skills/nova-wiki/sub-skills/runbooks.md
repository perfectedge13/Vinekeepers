# Nova-wiki-runbooks

**Inputs:** Wiki context from wiki_prepare (updates may include "bug/root cause" → update troubleshooting or recovery), handoff. Credentials from env (WIKIJS_*).

**Outputs:** Pages created or updated: `/vinekeepers/runbooks`, `/vinekeepers/runbooks/operational`, `/vinekeepers/runbooks/troubleshooting`, `/vinekeepers/runbooks/recovery`, `/vinekeepers/runbooks/maintenance`. Pass/Fail and count.

## Instructions

1. Read **@.cursor/skills/nova-wiki/page-formats.md** for the runbooks page types. Each has a required Title and Body structure (Index, Procedures, or Entries).
2. **Authenticate** per **@.cursor/skills/nova-wiki/auth.md**: use WIKIJS_API_KEY as Bearer token, or login with `authentication { login(strategy: "local", username: $username, password: $password) { jwt } }` and use the returned **jwt** as `Authorization: Bearer <jwt>`. If credentials unset, return Fail.
3. For each page:
   - **`/vinekeepers/runbooks`** — Ensure exists. Title: Runbooks. Body: # Index (links to operational, troubleshooting, recovery, maintenance), short intro.
   - **`/vinekeepers/runbooks/operational`** — Ensure exists. Title: Operational. Body: # Procedures (heading per procedure; steps underneath). Add or update from handoff if "operational procedure change."
   - **`/vinekeepers/runbooks/troubleshooting`** — Ensure exists. Title: Troubleshooting. Body: # Entries (each ## &lt;symptom or issue&gt; then cause, steps, resolution). If handoff indicates bug/root cause or incident, add or update an entry.
   - **`/vinekeepers/runbooks/recovery`** — Ensure exists. Title: Recovery. Body: # Procedures (recovery scenarios and steps). Update from handoff if recovery procedure or incident.
   - **`/vinekeepers/runbooks/maintenance`** — Ensure exists. Title: Maintenance. Body: # Procedures (maintenance tasks, schedule, steps). Update from handoff if maintenance procedure change.
4. Use GraphQL variables for page content to avoid escaping issues (see auth.md). If no handoff content for a sub-page, ensure the page exists with the required section headings (placeholder content is fine).
5. Return: Pass (or Fail), and one line e.g. "Updated runbooks (5 pages)."
