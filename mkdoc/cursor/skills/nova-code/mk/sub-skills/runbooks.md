# Runbooks (mk)

## Summary

Creates/updates mkdoc/runbooks/ (index, operational, troubleshooting, recovery, maintenance); follows page-formats.md.

## Key points

- See **.cursor/skills/nova-code/mk/sub-skills/runbooks.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-mk-runbooks

**Inputs:** Mk context from mk_prepare (updates may include "bug/root cause" → update troubleshooting or recovery), handoff.

**Outputs:** Files created or updated: `mkdoc/runbooks/index.md`, `mkdoc/runbooks/operational.md`, `mkdoc/runbooks/troubleshooting.md`, `mkdoc/runbooks/recovery.md`, `mkdoc/runbooks/maintenance.md`. Pass/Fail and count.

## Instructions

1. Read **@.cursor/skills/nova-code/mk/page-formats.md** for the runbooks file types. Each has a required Title and section structure (Index, Procedures, or Entries).
2. Create **mkdoc/runbooks/** if it does not exist.
3. For each file:
   - **mkdoc/runbooks/index.md** — Ensure exists. Content: # Runbooks, # Index (links to operational, troubleshooting, recovery, maintenance), short intro. Write or overwrite.
   - **mkdoc/runbooks/operational.md** — Ensure exists. Content: # Operational, # Procedures (heading per procedure; steps underneath). Add or update from handoff if "operational procedure change." Write or overwrite.
   - **mkdoc/runbooks/troubleshooting.md** — Ensure exists. Content: # Troubleshooting, # Entries (each ## &lt;symptom or issue&gt; then cause, steps, resolution). If handoff indicates bug/root cause or incident, add or update an entry. Write or overwrite.
   - **mkdoc/runbooks/recovery.md** — Ensure exists. Content: # Recovery, # Procedures (recovery scenarios and steps). Update from handoff if recovery procedure or incident. Write or overwrite.
   - **mkdoc/runbooks/maintenance.md** — Ensure exists. Content: # Maintenance, # Procedures (maintenance tasks, schedule, steps). Update from handoff if maintenance procedure change. Write or overwrite.
4. If no handoff content for a sub-page, ensure the file exists with the required section headings (placeholder content is fine).
5. Return: Pass (or Fail), and one line e.g. "Updated runbooks (5 files)."
```

</details>
