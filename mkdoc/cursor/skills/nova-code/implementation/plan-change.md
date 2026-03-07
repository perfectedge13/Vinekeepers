# Plan change

## Summary

Identifies impacted specs via change_triggers and primary_assets; sets removal_or_rename if applicable. Output feeds implement and later steps.

## Key points

- Read spec index and registries; determine which specs/assets are impacted by the user request.
- If the request involves file removal or rename, set removal_or_rename so the workflow runs removal_rename_sequence.
- See **.cursor/skills/nova-code/implementation/plan-change.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-plan-change

**Inputs**: User request, index, loaded registry specs.
**Outputs**: Impact set (impacted spec files, impacted assets), `removal_or_rename` (boolean).

## Instructions

1. Using the index and loaded registries, identify **impacted specs** via `change_triggers.paths` and `scope.primary_assets` relative to the user's request (files/features being changed).
2. Determine if the request involves **deletion or rename** of any file or asset. If yes, set `removal_or_rename: true`.
3. Output: list of impacted registry spec file paths, list of impacted asset paths/ids, and `removal_or_rename`. The orchestrator uses this to decide whether to run the removal/rename sequence before the rest of the execution loop.
```

</details>
