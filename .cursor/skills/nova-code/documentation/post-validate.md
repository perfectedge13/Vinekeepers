# Nova-post-validate

**Inputs**: Modified spec paths, index, registries.
**Outputs**: Traceability complete (yes/no); if not, repair steps or output issue.

## Instructions

1. From the updated specs, **identify impacted requirements** (from assets.requires and traceability).
2. Check that **traceability is complete**: every requirement has traceability to assets and (where applicable) tests; no dangling asset or requirement refs.
3. If incomplete: repair traceability (update spec within schema) and re-check. If repair is not possible without new keys, raise an issue and document what is missing.
