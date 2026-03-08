# Common sub-skills

**Single source:** All shared sub-skill content lives here. Nova-code and nova-commit have **no duplicate copies** of these; workflows reference this folder via the `location` field.

## How to execute a workflow

**Mandatory:** You MUST run every phase and every step in the workflow in order. Do not skip any step (except branch_removal_rename when plan_change did not set removal_or_rename, or gates with skip_when). Skipping a step is a workflow violation; the run is incomplete.

Orchestrators (nova-code, nova-commit) run a workflow by doing the following. Do not duplicate the step list in the top-level skill; the workflow YAML is the checklist.

**Nova-code:** The orchestrator **enforces** the workflow: it runs **gates** itself (e.g. validate-specs, validate-drift) and launches **one sub-agent per step** for every step that has a sub-skill (location). It does not run sub-skills itself.

1. **Load the workflow** — Read the workflow YAML (e.g. `.cursor/workflows/nova-code.yml`). If the workflow has a top-level **run_order** array, execute those step IDs in that order (look up each id in the workflow's `phases` or `steps` and run it). Otherwise execute `phases` in order; if a phase has type **sequence** (e.g. execution_loop), expand it as below.
2. **Sequence expansion** — When a phase has **type: sequence** (e.g. execution_loop), you MUST expand it: treat it as N separate steps. For each step name in that phase's `steps` array, in order, look up the step in the workflow's `steps` map; then run that step (if gate: run the `run` command; if step: open the step's `location` and follow that sub-skill). Do not treat the sequence as a single phase or run only a subset; run each of the N steps one by one.
3. **For each phase/step:**
   - **type: gate** — Run the `run` command (from the phase or from the `steps` map). If it fails (non-zero exit): **STOP**, output a **Spec Drift Issue** (file paths, errors, minimal edits), do not proceed. If the phase has `skip_when: schema_not_present`, skip and note when schemas are absent.
   - **type: step** (or phase has `skill` + `location`) — Open the file at `location` and follow that sub-skill. If no `location`, follow the phase's `description`.
4. **Branching** — If the workflow defines a branch (e.g. `when: plan_change.removal_or_rename`), run that sequence (e.g. `removal_rename_sequence`) then rejoin at the next step.
5. **Do not skip gates** unless the workflow explicitly allows it (e.g. `skip_when`).
6. **Before finishing**, verify you ran every phase and every step in the workflow (use the workflow YAML or run_order as the checklist).

Sub-skills that run commands (e.g. run-tests, build-check) use the project adapter: read `specs/specs.yml` for `validation.commands.test`, canonical `validation.commands.build_check`, legacy `validation.commands.static_analysis`, and `validation.shell` when present.

## Sub-skills in this folder

Guardrails are now a selectively applied rule: **@.cursor/rules/guardrails.mdc** (referenced from spec-workflow-core).

| File | Purpose |
|------|---------|
| **requirement-tracking.md** | When to create, update, or split requirements; id and traceability rules. |
| **discovery.md** | Read spec index, load registry specs (scope from context or "all"), summarize index, registries, README. |
| **schema-gate.md** | Run validate-specs; Pass/Fail + Spec Drift Issue. |
| **drift-gate.md** | Run validate-drift or manual drift checks; Pass/Fail + Spec Drift Issue. |
| **run-tests.md** | Run project test command; full output and counts; Pass/Fail/Blocked. |
| **static-analysis.md** | Run project build-check command (compile/build verification; legacy static_analysis alias supported); Pass/Fail. |
| **reconcile.md** | Reconcile specs, code, and README; no dangling refs; fix or raise issues. |
| **update-readme.md** | Review all 12 impacted artifact categories; update only impacted (incl. README/docs). Do not assume only code changes. |

Workflows set `location: .cursor/skills/common/<file>.md` for these steps.

**Nova-code layer alignment:** Nova-code uses common for the **design** layer (discovery), **pre_change** (schema_gate, drift_gate), **validation** (run-tests, build-check), **wrap_up** (reconcile), and **documentation** (update-readme). The **implementation** and **documentation** layers also use nova-code–specific sub-skills under `.cursor/skills/nova-code/implementation/` and `.cursor/skills/nova-code/documentation/`. See [nova-code/layers.yml](.cursor/skills/nova-code/layers.yml) for the full step-to-layer map.

**Nova-wiki:** The nova-code workflow includes a **wiki** step that invokes the **nova-wiki** skill (`.cursor/skills/nova-wiki/`). Nova-wiki syncs a Wiki.js instance from specs and handoff. It requires `WIKIJS_URL` and either `WIKIJS_EMAIL`/`WIKIJS_PASSWORD` or `WIKIJS_API_KEY` in the environment (see `.env.example`). If these are not set, the wiki step reports **Skip** and does not block the run.
