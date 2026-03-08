# Static analysis (common)

## Summary

Runs the project static-analysis command (from specs validation or project.yml); reports Pass/Fail and what was run.

## Key points

- Command from specs validation.commands.build_check or project.yml validation_defaults.build_check. Run from project root (shell-safe: Set-Location on PowerShell, no `&&`).
- Report Pass or Fail and what was run (e.g. "mvn compile: Pass"). If validation not configured, report and do not assume a command.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-static-analysis (common)

**Inputs**: Project root, adapter config (from specs index `validation.commands.build_check` if present).
**Outputs**: Pass | Fail; report of static analysis performed.

## Instructions

1. Read **@specs/specs.yml** for `validation.commands.build_check`. If present, use that command; else **@.cursor/project.yml** `validation_defaults.build_check`; else report "validation not configured" and do not assume a static analysis command.
2. Run the command from project root (shell-safe: use Set-Location on PowerShell, no `&&`).
3. Report Pass or Fail and what was run (e.g. "mvn compile: Pass").
```

</details>


