---
name: nova-wiki
description: Orchestrator for Wiki.js sync. Invoked by nova-code wiki step. Reads workflow; launches one sub-agent per wiki step; passes handoff (user request, plan_change/implement summary, wiki context from prepare).
disable-model-invocation: true
---

# Nova Wiki (Orchestrator)

This skill runs the **wiki sync** workflow. You are the **orchestrator**: you **enforce** the workflow. You do **not** run sub-skills yourself; you **launch a sub-agent** for each step that has a `location`.

**Workflow** — Read **@.cursor/workflows/nova-wiki.yml**. Page formats are in **@.cursor/skills/nova-wiki/page-formats.md**. Authentication and create/update details are in **@.cursor/skills/nova-wiki/auth.md** (login returns **jwt**; use Bearer token; use variables for content).

**How to run:**

1. **Preflight (optional):** If `WIKIJS_*` are not set in process env, first try loading from project root **.env** (read file, parse `KEY=value` lines; skip `#` and blank lines). If still unset, return **Skip** with message "Wiki step skipped: WIKIJS_* not set." Do not launch sub-agents.
2. **Read** the workflow and get **run_order**: wiki_prepare, wiki_index, wiki_architecture, wiki_runbooks, wiki_feature_dossiers.
3. **For each step ID in run_order**, in order: **call the mcp_task tool** to run the sub-skill at that step's **location**. Pass handoff (user request; for steps after wiki_prepare, pass the prepare output: domains, features, updates list).
4. **Handoff:** Pass the **user request** and any **previous step result** (e.g. plan_change summary, implement summary from nova-code; after wiki_prepare, pass the wiki context so index/architecture/runbooks/feature_dossiers know what to create or update). **When the wiki step is invoked by nova-code**, the orchestrator (nova-code) must supply a **structured handoff** so wiki_prepare can compute **affected_features** (0..n) for selective updates: include **plan_change** (impacted registry spec file paths, impacted asset paths/ids) and **implement** (list of changed file paths). Without this, prepare defaults to affected_features = all features (full sync).
5. **Output:** The last step (wiki_feature_dossiers) returns a short summary (e.g. "Updated index, architecture, runbooks, N feature dossiers"). Return that to the nova-code orchestrator.

**Using Cursor sub-agents:** For every step that has a `location` in the workflow, you **MUST** call the **mcp_task** tool. Use:
- **subagent_type:** `generalPurpose`.
- **prompt:** Include (1) Step id and instruction: "Run the nova-wiki step **&lt;step_id&gt;**. Open and follow the sub-skill at **&lt;location&gt;**." (2) Context: "User request: &lt;user request&gt;." (3) Handoff: "Previous step result: &lt;summary&gt;" (e.g. wiki_prepare output: domains, features, updates, affected_features). For **wiki_prepare**, the initial handoff from nova-code must include plan_change (impacted registries, impacted assets) and implement (list of changed files) when available, so prepare can compute affected_features. (4) "Return: Pass or Fail, and one short line describing what you did."
- **description:** e.g. "nova-wiki step: &lt;step_id&gt;".

Do not skip any step when wiki is run (unless preflight determined env is missing). The workflow YAML and run_order are the checklist.
