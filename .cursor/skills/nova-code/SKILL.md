---
name: nova-code
description: Orchestrator for spec-driven implementation. Reads workflow; runs gates; launches one sub-agent per step. Use when the user requests nova-code, spec-driven implementation, or when working against specs.yml and registry specs.
disable-model-invocation: true
---

# Nova Code (Orchestrator)

This skill runs the spec-driven **implementation** workflow. You are the **orchestrator**: you **enforce** the workflow. You do **not** run sub-skills yourself; you run gate commands and **launch a sub-agent** for each step that has a sub-skill.

**Workflow** — Read **@.cursor/workflows/nova-code.yml**. The project rule **@.cursor/rules/spec-workflow.mdc** is the source of truth for gate semantics.

**How to run:**

1. **Read** the workflow and get **run_order** (the ordered list of step IDs).
2. **For each step ID in run_order**, in order:
   - **If the step is a gate** (schema_gate, drift_gate, pre_change_lock, post_schema, or gates inside removal_rename_sequence): **you** run the gate command from the workflow (e.g. `npm run validate-specs`, `npm run validate-drift`). On failure: **STOP**, output a **Spec Drift Issue**, do not launch further sub-agents.
   - **If the step is a step** (has a `location` in the workflow): **call the mcp_task tool** (see "Using Cursor sub-agents" below) to run that step. Do not run the sub-skill yourself.
3. **Handoff:** Pass the **user request** into each sub-agent task. Optionally pass a short summary from the previous sub-agent (e.g. plan_change output) so the next has context. **plan_change** returns impact set (impacted registries, impacted assets, removal_or_rename) **and change_context** (a compressed markdown bundle of relevant spec + mkdoc for the request). For the **implement** step, handoff **must** include **change_context** (from plan_change result) so implement can use requirements, anti_patterns, and doc excerpts without re-reading full specs and mkdoc. Sub-agents can read the repo as needed. **For the update_readme step:** pass a **structured cumulative handoff** so the sub-agent can classify impact without a broad repo scan: include **plan_change** (impacted registry spec file paths, impacted asset paths/ids, change_context when available), **implement** (list of changed source file paths), **update_tests** (list of changed test file paths), and **update_specs** (impacted spec paths plus short summary of doc-facing deltas when available). That step **only edits README.md**; the **mk** step syncs the docs dir (e.g. mkdoc). **For the mk step:** you **must** pass a **structured cumulative handoff** so the mk workflow can map coding changes to 0..n feature updates: include **plan_change** (impacted registry spec file paths, impacted asset paths/ids) and **implement** (list of changed file paths). Collect these from the relevant sub-agents' results before launching each downstream step.
4. **Branch (removal/rename):** When the step is `branch_removal_rename` and plan_change set removal_or_rename, run the **removal_rename_sequence** from the workflow: for each step in that sequence, either run the gate yourself or launch a sub-agent for the step's location (one sub-agent per step of the sequence). Then rejoin run_order at the next step.
5. **Output:** The last step is **output**. Its sub-agent produces the final report (per output-format). Return that report to the user.

**Using Cursor sub-agents:** For every step that has a `location` in the workflow, you **MUST** call the **mcp_task** tool (Cursor's sub-agent launcher). Do not run that step yourself. Use:
- **subagent_type:** `generalPurpose` (or the type that runs arbitrary instructions and returns a result).
- **prompt:** A single task description that includes:
  1. Step id and instruction: "Run the nova-code step **&lt;step_id&gt;**. Open and follow the sub-skill at **&lt;location&gt;** (from the workflow)."
  2. Context: "User request: &lt;exact user request&gt;."
  3. Handoff (for steps after plan_change): "Previous step result: &lt;short summary&gt;" (e.g. plan_change: impacted registries, removal_or_rename, **change_context**; implement: list of changed files). For **implement**, include **change_context** (from plan_change) in handoff so the sub-agent has compressed spec + mkdoc context. For **update_readme** (README.md only; not mkdocs), handoff is **required** and must include plan_change (impacted registries/assets and change_context when available), implement (changed source file paths), update_tests (changed test file paths), and update_specs (impacted spec paths and doc-facing summary when available). For the **mk** step, handoff is **required** and must include plan_change (impacted registry paths, impacted asset paths/ids) and implement (list of changed file paths).
  4. Required output: "Return: Pass or Fail, and one short line describing what you did or what changed."
- **description:** Short label for the task, e.g. "nova-code step: &lt;step_id&gt;".

Collect each sub-agent's result before launching the next. If a sub-agent returns Fail, you may stop and report, or continue according to the workflow (e.g. output step still runs to report the failure). For steps inside **removal_rename_sequence**, use mcp_task for each step that has a `location` (reference_map, apply_removal, verify); run any gate in that sequence yourself (e.g. gates_again).

**Gate commands** are in the workflow (e.g. `run: npm run validate-specs` under schema_gate, drift_gate, pre_change_lock, post_schema). Look up each step in `phases` or `steps` to see if it has `type: gate` and a `run` field.

Do not skip any step (except branch_removal_rename when plan_change did not set removal_or_rename). The workflow YAML and run_order are the checklist.
