# Nova-plausibility-review

**Inputs**: All loaded registry specs (every requirement across all registries).
**Outputs**: Plausibility table — for each requirement: Implemented? (true/false), brief justification. If any accepted requirement is false, treat as failure.

## Instructions

1. For **every requirement** in every loaded registry, determine whether it is **implemented** based on assets, symbols, tests, and code.
2. Build a table (or list) with: requirement id, title or short label, status (e.g. accepted/deprecated), **Implemented?** (true/false), and a brief justification (e.g. "Asset A-CORE-MAIN exists; test T-CORE-001 in ConfigTest").
3. If any requirement with **status: accepted** has **Implemented? false**: treat as failure; raise an issue; do not proceed to commit. Deprecated requirements: note only, do not fail the workflow.
4. Output the plausibility table so it can be included in the final report.
