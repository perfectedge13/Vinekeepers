# Nova-build-check (common)

**Inputs**: Project root, adapter config (from specs index `validation.commands.build_check` when present).
**Outputs**: Pass | Fail; report of build or compile verification performed.

## Instructions

1. Read **@specs/specs.yml** for `validation.commands.build_check`. If present, use that command. During migration, accept legacy `validation.commands.static_analysis` as an alias. If the index has neither, use **@.cursor/project.yml** `validation_defaults.build_check` and then legacy `validation_defaults.static_analysis`. If still absent, report "validation not configured" and do not assume a build-check command.
2. Run the command from project root (shell-safe: use Set-Location on PowerShell, no `&&`).
3. Report Pass or Fail and what was run (e.g. "mvn compile: Pass"). Use honest language such as "build check" or "compile verification" rather than "static analysis" when the command is only a compile/build step.
