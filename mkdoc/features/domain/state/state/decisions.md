# Decisions

# Entries

## 2026-03-23 — Plan confidence encodes known-vs-unknown facts

**Context:** Operators need a durable signal of planning substance beyond a single readiness enum.

**Decision:** Persist **PlanConfidence** with explicit **structuredKnownFactCount** / **materialUnknownCount** / **materialUnknownLabels** alongside **confidenceScore** and **confidenceReasons**, computed from plan governance and artifact signals in **PlanReadinessCalculator** and written by **run_plan_critique_and_readiness**.

**Consequence:** Launch approval continues to use **PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD** (**0.85**) via **PlanningApprovalGateSupport**, with reasons and tallies visible for Discord copy and audits.

