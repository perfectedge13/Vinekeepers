# Nova-mk-runbooks

**Inputs:** Mk context from mk_prepare (updates may include "bug/root cause" → update troubleshooting or recovery), handoff.

**Outputs:** Files created or updated for every runbook in the canonical list (page-formats): `<docs_dir>/runbooks/index.md`, `operational.md`, `troubleshooting.md`, `recovery.md`, `maintenance.md`, `configuring-bots.md`. Pass/Fail and count.

## Instructions

1. Read **@.cursor/skills/nova-code/mk/page-formats.md** for the runbooks file types. Each has a required Title and section structure (Index, Procedures, or Entries). The runbook set is defined in the runbooks table there (canonical list).
2. Create `<docs_dir>/runbooks/` if it does not exist.
3. **Per-runbook check:** For each runbook in the canonical list (page-formats), determine if it needs updates: (a) File missing or missing required sections/headings; (b) Handoff indicates an update for that runbook type (e.g. operational procedure change, bug/root cause, recovery, maintenance); (c) When the runbook has a source of truth (e.g. config/bots.yaml for configuring-bots), consider whether content should be refreshed from that source. Then create or update each runbook that needs changes; leave others unchanged if they already satisfy requirements.
4. For **each** runbook in that table: ensure `<docs_dir>/runbooks/<slug>.md` exists (create if missing); ensure runbooks/index.md includes a link to it; ensure the file has the required Title and section structure from page-formats. If the project's mkdocs nav is maintained by this workflow, ensure the Runbooks nav includes every runbook from page-formats.
5. Per-runbook content rules:
   - **`<docs_dir>/runbooks/index.md`** — Ensure exists. Content: # Runbooks, # Index (links to operational, troubleshooting, recovery, maintenance, configuring-bots), short intro. Write or overwrite.
   - **`<docs_dir>/runbooks/operational.md`** — Ensure exists. Content: # Operational, # Procedures (heading per procedure; steps underneath). Add or update from handoff if "operational procedure change." Write or overwrite.
   - **`<docs_dir>/runbooks/troubleshooting.md`** — Ensure exists. Content: # Troubleshooting, # Entries (each ## &lt;symptom or issue&gt; then cause, steps, resolution). If handoff indicates bug/root cause or incident, add or update an entry. Write or overwrite.
   - **`<docs_dir>/runbooks/recovery.md`** — Ensure exists. Content: # Recovery, # Procedures (recovery scenarios and steps). Update from handoff if recovery procedure or incident. Write or overwrite.
   - **`<docs_dir>/runbooks/maintenance.md`** — Ensure exists. Content: # Maintenance, # Procedures (maintenance tasks, schedule, steps). Update from handoff if maintenance procedure change. Write or overwrite.
   - **`<docs_dir>/runbooks/configuring-bots.md`** — Ensure exists. Content: # Configuring bots, # Procedures (how to edit config/bots.yaml: bots, routing, persona, workflow refs; link to config and to vinekeepers-standards bot-config). Optionally refresh from config/bots.yaml when present. Write or overwrite.
6. If no handoff content for a sub-page, ensure the file exists with the required section headings (placeholder content is fine).
7. Return: Pass (or Fail), and one line e.g. "Updated runbooks (6 files)."
