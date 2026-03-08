# Cursor docs (mk)

## Summary

Syncs cursor/ from .cursor (rules, skills, workflows); ensures mkdocs nav includes Cursor. Runs sync-cursor-docs logic.

## Key points

- See **.cursor/skills/nova-code/mk/sub-skills/cursor-docs.md** and **.cursor/skills/common/sync-cursor-docs.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Cursor docs (nova-mk sub-skill)

**Inputs:** Mk context from previous steps (optional). Docs dir from **@.cursor/project.yml** `paths.docs_dir` (default `mkdoc`).

**Outputs:** List of created/updated `cursor/*` file paths; Pass or Fail.

**References:** **@.cursor/skills/common/sync-cursor-docs.md**, **@.cursor/skills/nova-code/mk/page-formats.md** (Cursor section).

---

Run the logic in **@.cursor/skills/common/sync-cursor-docs.md**:

1. **Enumerate** `.cursor/rules/*.mdc`, `.cursor/skills/` (top-level = dirs with SKILL.md; sub-skills = .md under each except SKILL.md and page-formats.md), `.cursor/workflows/*.yml` (top-level only).
2. **Write** all `cursor/` pages under the docs dir per page-formats (Cursor section): cursor/index.md, cursor/rules/index.md and per-rule pages, cursor/skills/index.md and per-skill and per-sub-skill pages, cursor/workflows/index.md and per-workflow pages.
3. **Nav:** Ensure **mkdocs.yml** at project root includes a **Cursor** nav section (Overview, Rules, Skills with nested sub-skills, Workflows). Add the section if missing; preserve existing Home, Features, Architecture, Runbooks.

Return **Pass** and the list of cursor paths created or updated; or **Fail** with a short reason if something could not be written.
```

</details>


