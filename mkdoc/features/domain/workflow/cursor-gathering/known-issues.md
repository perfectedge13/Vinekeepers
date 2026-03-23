# Known issues

# Current limitations

- This flow depends on external Cursor Cloud and repository integrations that are not fully exercisable in isolated unit tests.
- The documented gathering path is specialized to the shipped Luna configuration rather than a general-purpose multi-bot pattern.
- Run tracking is intentionally in-memory for v1, so active Cursor runs and pending progress notifications are not recovered after an app restart.

# Resolved

## Readiness / “what are you asking?” copy (2026-03-22)

**Was:** Readiness checkpoints and decision prompts could read like internal signals (status only, or ADR-shaped asks) without explaining the operator’s choices in plain language.

**Now:** **`planReadinessCheckpointGuide`** is spread with critique/readiness when **`planReadinessStatus`** is **`NEEDS_HUMAN_DECISION`**; **`PlanningPromptFormatter`** and profile-driven hints improve **`prompt_for_field`** and decision-log wording. See **decisions** (same date) and **change-log** **2026-03-22**.

## Planning packet and critique showed machine-like tokens (2026-03-22)

**Was:** Intake-thread planning packets, critique summaries, depth-gate reasons, and some role-thread lines could echo internal ids, YAML paths, or enum-style tokens.

**Now:** **`PlanningThreadPacketFormatter`**, **`PlanCritiqueSupport`**, **`RunPlanCritiqueAndReadinessAction`**, **`BuildRolePlanningThreadMessagesAction`**, **`PlanningPacketDepthEvaluator`**, and **`GenericReadinessEvaluator`** route operator-facing strings through **`PlanningUserFacingCopy`** (and profile metadata) per **workflow-registry** assets. See **how-it-works** Phase C and **change-log** **2026-03-22**.

