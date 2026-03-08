# Nova-output-format

**Purpose:** Single source of truth for the structure of the nova-code run report. Used by the output step to produce a consistent, workflow-aware report that visualizes what ran and validates that the workflow executed as intended.

**Inputs:** Step results from the run (phase/step outcomes, gate results, change summary, files/specs changed, test counts, etc.).

**Output:** A final report that follows the section order and structure below.

---

## Report structure (required order)

Produce the report in this order. If the run stopped at a gate or step, report up to that point; mark the failed step as **Fail** and subsequent steps as **Not run**; still include Workflow executed and Per-step outcome so the user sees where execution stopped.

### 1. Workflow executed

List **every** step from the workflow's **run_order** (or phases then execution_loop.steps), in order, with a one-word status per step: **Pass** | **Skip** | **Fail** | **Not run**.

- **Source of order:** The workflow YAML `run_order` array (or phases, then execution_loop.steps). If a branch was taken (e.g. removal/rename), include removal_rename_sequence steps in order between branch_removal_rename and the next step.
- **Completeness:** Every step that must run must appear in the list. If any step that is required (i.e. not branch_removal_rename when skipped) has status **Not run**, the report **MUST** include a **Workflow incomplete** line: list the missing step IDs and state that the run is incomplete.
- **Example:**  
  discovery → Pass  
  guardrails → Pass  
  schema_gate → Pass  
  drift_gate → Pass  
  execution_loop: plan_change → Pass, branch_removal_rename → Skip, pre_change_lock → Pass, implement → Pass, update_specs → Pass, update_readme → Pass, post_schema → Pass, traceability → Pass, run_tests → Pass, build_check → Pass, reconcile → Pass, mk → Pass, output → Pass  
- If the run stopped early (e.g. gate failure): list steps up to and including the failed step with Fail; subsequent steps as **Not run**.

### 2. Per-step outcome

For each phase/step that actually ran, one short line (or two) stating: **Step id**, **Result** (Pass/Fail/Skip), and **What happened / what changed**.

- Examples: "schema_gate: Pass. All 9 specs valid." | "implement: Pass. Added dummy.txt." | "run_tests: Pass. Tests run: 17, Passed: 17, Failed: 0."

### 3. Workflow validation

A short checklist the reader can use to confirm the run is successful and complete:

- Schema Gate: Pass / Fail / Skipped  
- Drift Gate: Pass / Fail / Skipped  
- Pre-change lock (if applicable): Pass / Fail  
- Post-change schema: Pass / Fail  
- Tests: Pass / Fail / Blocked (with counts: run, passed, failed)  
- Static analysis: Pass / Fail  
- Reconcile: OK / Issues  
- Mk: Pass / Fail (docs-dir sync from specs and handoff)
- No unresolved spec drift or blocked tests  

### 4. Detail sections

Then include the following sections in this order, populated from step results:

4. **Summary of change** — What was implemented or changed.  
5. **Changed files** — Add/modify/delete list.  
6. **Specs updated** — Index and registry specs modified.  
7. **Schema validation results** — Per modified spec: Pass/Fail (with errors if Fail); note "skipped" if schemas not present.  
8. **Drift Gate result** — Pass/Fail and any Spec Drift Issue.  
9. **Test results** — Pass/Fail/Blocked; include **counts** (tests run, passed, failed, skipped); if Fail include failed test class/method; if Blocked state missing prerequisite.  
10. **Static analysis** — What was run and result.  
11. **Reconcile results** — Any mismatches found and fixes applied or issues raised.  
12. **Mk results** — If mk step ran: Pass / Fail; if Pass, one-line summary (e.g. "Updated index, architecture, runbooks, N feature dossiers").  
13. **README changes** — Summary of README updates.  
14. **Issues raised** — Any spec drift issues, blocked tests, or unmet requirements (do not delete requirements).
