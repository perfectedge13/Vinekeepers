# Architecture (mk)

## Summary

Creates/updates mkdoc/architecture.md (Overview, System context, Major subsystems, Runtime flows, Diagram); follows page-formats.md.

## Key points

- See **.cursor/skills/nova-code/mk/sub-skills/architecture.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-mk-architecture

**Inputs:** Mk context from mk_prepare (domains), handoff (user request). Handoff may indicate "major refactor" or "new domain" so architecture should be updated.

**Outputs:** File `mkdoc/architecture.md` created or updated. Pass/Fail and short description.

## Instructions

1. Read **@.cursor/skills/nova-code/mk/page-formats.md** for the architecture format: Title: Architecture. Sections: # Overview, # System context, # Major subsystems, # Runtime flows, # Diagram.
2. Gather content from **@specs/specs.yml**, **@README.md**, and loaded registry specs: project summary, external systems, domains (major subsystems), how components interact (runtime flows). Optionally include a Mermaid diagram (e.g. from README or generate from domains).
3. Create **mkdoc/** if it does not exist. Write or overwrite **mkdoc/architecture.md** with the formatted body (markdown; use a Mermaid code block in the Diagram section if desired).
4. Return: Pass (or Fail), and one line e.g. "Updated architecture page."
```

</details>
