# Nova-static-analysis (common)

**Inputs**: Project root, adapter config (from specs index `validation.commands.static_analysis` if present).
**Outputs**: Pass | Fail; report of static analysis performed.

## Instructions

1. Read **@specs/specs.yml** for `validation.commands.static_analysis`. If present, use that command; otherwise use project default (e.g. Vinekeepers: `mvn compile` or `mvn verify` if Checkstyle/SpotBugs configured).
2. Run the command from project root (shell-safe: use Set-Location on PowerShell, no `&&`).
3. Report Pass or Fail and what was run (e.g. "mvn compile: Pass").
