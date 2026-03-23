# Workflow runners and session lifecycle

# Status

active

# Summary

Workflow state machine and config-driven runners (REQ-WORKFLOW-001, REQ-WORKFLOW-002). `Workflow<S>` remains the legacy state-machine contract, while `WorkflowRunner` is the engine-facing interface that loads session state, runs a configured or stub workflow, and returns `WorkflowRunResult`. `WorkflowRunnerFactory`, `ConfigurableWorkflowRunner`, `ConfigurableWorkflowState`, and session key strategies handle pause/resume lifecycle across conversational sessions. Workflows may set `workflowSchema: v2` with a `phases` map to use `GraphWorkflowRunner` (phase graph, optional rules, `legacy_action`, `linear_workflow_ref`, and `configurable_steps` capabilities: `linear_workflow_ref` runs a sibling linear workflow; `configurable_steps` runs inline `steps` with the parent v2 `llm` defaults and template policy on the same session store). `PlanningGapEvaluator` aligns effective clarification UI with the ledger in **legacy** mode; **canonical_v1** work profiles (`coordinatorClarification` in `config/work-profiles.yaml`) derive open gaps from plan text via `CoordinatorClarificationGapEvaluator`, reconcile stale OPEN ledger rows, then upsert—so `planningUserInputRequired` still follows OPEN items after sync, without rehydrating from stale question text. Arrietty-style clarification is strict: **one** concrete blocking question when a **required** fact cannot be inferred; ranker/quality-gate layers suppress generic meta prompts; **open questions** in the packet list substantive unresolved lines only (**PlanningArtifactTexts** / **PlanningThreadPacketFormatter**); **coordinatorClarification** gap rules with generic **questionTemplate** text are skipped; user-facing copy avoids exposing internal parse/pipeline failure mechanics (**planningCycleUserVisibleFailure**, progress summaries, merge errors humanized via **PlanningUserFacingCopy**). `WorkflowRulesEngine` predicates include `reviewReady`, `approvalReady`, and `ledgerHasBlockingOpen` for deliberation-style transitions. Optional top-level `deliberation` YAML on v2 workflows is loaded into `WorkflowV2Model` and mirrored to session as `workflowDeliberationMetaJson` for tooling. Generic clarification ledger and progress-event types live under `com.vinekeepers.state.workflow` (`workflowUnresolvedItemsJson`, `workflowProgressEventsJson` with typed `kind` / `message` / `refs`). Planning spreads dual-write `reviewReady` vs `approvalReady` so “packet ready to read” stays separate from “launch approval”. `DeliberationEngine` maps `planningPhase` to a generic `deliberationPhase`. Coordinator-facing template keys are allowlisted via `UserCopyContext` when templates use safe mode. The step DSL is documented separately in the Workflow Steps feature.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-WORKFLOW | Workflow state machine interface | src/main/java/com/vinekeepers/workflow/Workflow.java |
| ASSET-WORKFLOW-RESULT | Workflow result model | src/main/java/com/vinekeepers/workflow/WorkflowResult.java |
| ASSET-WORKFLOW-RUNNER | Interface to run workflow for an event and return a structured workflow result | src/main/java/com/vinekeepers/workflow/WorkflowRunner.java |
| ASSET-WORKFLOW-RUN-RESULT | Structured workflow result with reply, waiting/completed flags, and error details | src/main/java/com/vinekeepers/workflow/WorkflowRunResult.java |
| ASSET-STUB-WORKFLOW-RUNNER | Stub workflow runner implementation | src/main/java/com/vinekeepers/workflow/StubWorkflowRunner.java |
| ASSET-WORKFLOW-RUNNER-FACTORY | Create `WorkflowRunner` from workflow type, params, and bot runtime options from config | src/main/java/com/vinekeepers/workflow/WorkflowRunnerFactory.java |
| ASSET-SESSION-KEY-STRATEGY | Contract for resolving per-bot workflow session keys from events | src/main/java/com/vinekeepers/workflow/SessionKeyStrategy.java |
| ASSET-SESSION-KEY-STRATEGIES | Built-in session key strategies for channel, channel_user, and thread conversations | src/main/java/com/vinekeepers/workflow/SessionKeyStrategies.java |
| ASSET-CONFIGURABLE-WORKFLOW-STATE | Mutable state for configurable workflow including waiting, completed, and error lifecycle data | src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowState.java |
| ASSET-CONFIGURABLE-WORKFLOW-RUNNER | Run workflow from `WorkflowDefinition` with conversational pause/resume, session keys, and tool-backed actions | src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java |
| ASSET-GRAPH-WORKFLOW-RUNNER | Phase-graph runner for `workflowSchema: v2` | src/main/java/com/vinekeepers/workflow/v2/GraphWorkflowRunner.java |
| ASSET-WORKFLOW-V2-LOADER | Loads v2 phase/capability/rules YAML into `WorkflowV2Model` | src/main/java/com/vinekeepers/workflow/v2/WorkflowV2Loader.java |
| ASSET-WORKFLOW-RULES-ENGINE | Evaluates minimal `when`/`then` rules for v2 transitions | src/main/java/com/vinekeepers/workflow/v2/WorkflowRulesEngine.java |
| ASSET-UNRESOLVED-ITEM-LEDGER | Serialize/deserialize generic unresolved items in session JSON | src/main/java/com/vinekeepers/state/workflow/UnresolvedItemLedger.java |
| ASSET-PROGRESS-EVENT-LOG | Deduped append-only progress events in session JSON | src/main/java/com/vinekeepers/state/workflow/ProgressEventLog.java |
| ASSET-PLANNING-DELIBERATION-LEDGER-SYNC | Planning rank/merge sync into generic ledger | src/main/java/com/vinekeepers/workflow/planning/PlanningDeliberationLedgerSync.java |
| ASSET-DELIBERATION-ENGINE | `planningPhase` → `deliberationPhase` bridge | src/main/java/com/vinekeepers/workflow/deliberation/DeliberationEngine.java |
| ASSET-DELIBERATION-DIRTY-PASS-INDEX | Dirty coordinator pass ids after merge | src/main/java/com/vinekeepers/workflow/deliberation/DeliberationDirtyPassIndex.java |
| ASSET-PROGRESS-DEDUPE-HELPER | Normalize + hash text for progress post dedupe | src/main/java/com/vinekeepers/state/workflow/ProgressDedupeHelper.java |
| ASSET-WORKFLOW-SAFE-TEMPLATE-RENDERER | Allowlisted `{{path}}` template rendering for user-facing copy | src/main/java/com/vinekeepers/workflow/template/WorkflowSafeTemplateRenderer.java |
| ASSET-STUB-WORKFLOW | Stub workflow implementation | src/main/java/com/vinekeepers/workflow/StubWorkflow.java |
| ASSET-STUB-STATE | Stub workflow state | src/main/java/com/vinekeepers/workflow/StubState.java |

# Sub-pages

- [How it works](workflow/how-it-works.md)
- [Change log](workflow/change-log.md)
- [Known issues](workflow/known-issues.md)
- [Decisions](workflow/decisions.md)
- [Contracts](workflow/contracts.md)
- [Tests](workflow/tests.md)
- [Diagrams](workflow/diagrams.md)

