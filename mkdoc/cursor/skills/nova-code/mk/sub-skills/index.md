# Index (mk)

## Summary

Ensures mkdoc/index.md, mkdoc/features/index.md, mkdoc/features/domain.md, and per-domain files exist; follows page-formats.md.

## Key points

- See **.cursor/skills/nova-code/mk/sub-skills/index.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-mk-index

**Inputs:** Mk context from mk_prepare (domains, features), handoff (user request).

**Outputs:** Files created or updated: &lt;docs_dir&gt;/index.md, &lt;docs_dir&gt;/features/index.md, &lt;docs_dir&gt;/features/domain.md, and each &lt;docs_dir&gt;/features/domain/&lt;domain-slug&gt;.md. Pass/Fail and count of files updated. **Docs dir** from **@.cursor/project.yml** `paths.docs_dir` (default mkdoc). **Project name** from project.yml `project_name` or first registry `project.name`.

## Instructions

1. Read **@.cursor/skills/nova-code/mk/page-formats.md** and use the exact format for each file type you own (root index, features index, domain index, per-domain index).
2. Create the docs dir and **&lt;docs_dir&gt;/features/** and **&lt;docs_dir&gt;/features/domain/** directories if they do not exist.
3. For each file in order:
   - **&lt;docs_dir&gt;/index.md** — Ensure exists. Content: H1 &lt;project name from config or registry&gt;, # Overview (project summary from README or specs), # Quick links (bulleted links to [Features](features/index.md), [Architecture](architecture.md), [Runbooks](runbooks/index.md)). Write or overwrite the file.
   - **&lt;docs_dir&gt;/features/index.md** — Ensure exists. Content: # Features (title), # Definition of a feature (paragraph per page-formats), # Domains (table or list with Domain, Description, Link to each `domain/<domain-slug>.md`). Write or overwrite.
   - **&lt;docs_dir&gt;/features/domain.md** — Ensure exists. Content: # Domain index, # Domains (short intro), list of domains with links to `domain/<domain-slug>.md`. Write or overwrite.
   - **&lt;docs_dir&gt;/features/domain/<domain-slug>.md** — For each domain from mk context, ensure file exists. Content: H1 domain display name, # Features (table or list: Feature, Status, Link to feature summary page for that domain). Create parent directory if needed; write or overwrite.
4. Use relative links between markdown files as in page-formats.md.
5. Return: Pass (or Fail if write failed), and one line e.g. "Updated 4 index files."
```

</details>


