# Nova-static-analysis (common)

**Inputs**: Project root, adapter config (from specs index `validation.commands.static_analysis` if present).
**Outputs**: Pass | Fail; report of static analysis performed.

## Instructions

1. Read **@specs/specs.yml** for `validation.commands.static_analysis`. If present, use that command; else **@.cursor/project.yml** `validation_defaults.static_analysis`; else report "validation not configured" and do not assume a static analysis command.
2. Run the command from project root (shell-safe: use Set-Location on PowerShell, no `&&`).
3. Report Pass or Fail and what was run (e.g. "mvn compile: Pass").
