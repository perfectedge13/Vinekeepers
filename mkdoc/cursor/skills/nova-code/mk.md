# Mk (nova-code sub-skill)

## Summary

Orchestrator for local `mkdoc/` sync. Invoked by the `nova-code` `mk` step. It reads the `nova-mk` workflow, passes structured handoff from `plan_change` and `implement`, runs prepare and gather-context before the page-writing steps, and syncs feature docs, runbooks, architecture, and Cursor docs under the configured docs directory.

## Key points

- **Workflow:** Read **@.cursor/workflows/nova-mk.yml**. Page formats in **@.cursor/skills/nova-code/mk/page-formats.md**.
- **Run order:** `mk_prepare`, `mk_gather_context`, `mk_index`, `mk_architecture`, `mk_runbooks`, `mk_feature_dossiers`, `mk_cursor`.
- **Handoff:** `mk_prepare` receives cumulative `plan_change` and `implement` data so it can compute affected features. `mk_gather_context` compresses spec and existing mkdoc context for downstream steps.
- **Execution model:** The orchestrator uses one sub-agent per workflow step when the agent runtime supports that tool.
- **Output:** Returns a short summary of the updated mkdoc pages after the final step.

## Sub-skills

- [prepare](mk/sub-skills/prepare.md)
- [gather-spec-mkdoc-context](mk/sub-skills/gather-spec-mkdoc-context.md)
- [index](mk/sub-skills/index.md)
- [architecture](mk/sub-skills/architecture.md)
- [runbooks](mk/sub-skills/runbooks.md)
- [feature-dossiers](mk/sub-skills/feature-dossiers.md)
- [cursor-docs](mk/sub-skills/cursor-docs.md)

## Skill source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
---
name: nova-mk
description: Orchestrator for local mkdoc/ sync. Invoked by nova-code mk step. Reads workflow; launches one sub-agent per step; passes handoff (user request, plan_change/implement summary, mk context from prepare). Writes markdown files under mkdoc/; no auth. Bootstraps mkdoc/ when missing.
---

# Nova-mk (Orchestrator)

This skill runs the **mkdoc sync** workflow. You are the **orchestrator**: you **enforce** the workflow. You do **not** run sub-skills yourself; you **launch a sub-agent** for each step that has a `location`.

**Workflow** — Read **@.cursor/workflows/nova-mk.yml**. Page formats (file paths and section structure) are in **@.cursor/skills/nova-code/mk/page-formats.md**. All steps **write local markdown files** under the **docs dir** (from **@.cursor/project.yml** `paths.docs_dir`, default `mkdoc`); no API or credentials. See **@.cursor/skills/common/project-config.md**.

**How to run:**

1. **Bootstrap (optional):** If the **docs dir** (from project config) does not exist, create it and create its **index.md** with minimal content (title from project config or registry, # Overview with one sentence, # Quick links with bulleted links to Features, Architecture, Runbooks). Then proceed.
2. **Read** the workflow and get **run_order**: mk_prepare, mk_index, mk_architecture, mk_runbooks, mk_feature_dossiers.
3. **For each step ID in run_order**, in order: **call the mcp_task tool** to run the sub-skill at that step's **location**. Pass handoff (user request; for steps after mk_prepare, pass the prepare output: domains, features, updates list, affected_features).
4. **Handoff:** Pass the **user request** and any **previous step result**. **When the mk step is invoked by nova-code**, the orchestrator (nova-code) must supply a **structured cumulative handoff**: include **plan_change** (impacted registry spec file paths, impacted asset paths/ids) and **implement** (list of changed file paths). Collect these from the plan_change and implement sub-agents' results before launching the mk step. Then mk_prepare can compute **affected_features** for selective updates.
5. **Output:** The last step (mk_feature_dossiers) returns a short summary (e.g. "Updated index, architecture, runbooks, N feature dossiers"). Return that to the nova-code orchestrator.

**Using Cursor sub-agents:** For every step that has a `location` in the workflow, you **MUST** call the **mcp_task** tool. Use:
- **subagent_type:** `generalPurpose`.
- **prompt:** Include (1) Step id and instruction: "Run the nova-mk step **&lt;step_id&gt;**. Open and follow the sub-skill at **&lt;location&gt;**." (2) Context: "User request: &lt;user request&gt;." (3) Handoff: "Previous step result: &lt;summary&gt;" (e.g. mk_prepare output: domains, features, updates, affected_features). For **mk_prepare**, the initial handoff from nova-code must include plan_change (impacted registries, impacted assets) and implement (list of changed files) when available. (4) "Return: Pass or Fail, and one short line describing what you did."
- **description:** e.g. "nova-mk step: &lt;step_id&gt;".

Do not skip any step. The workflow YAML and run_order are the checklist.
```

</details>


