# Audit log for tool calls and outcomes

# Status

active

# Summary

Audit log for tool calls and outcomes (REQ-AUDIT-001). AuditLog records who triggered what, what tools were called, and results/errors for human-facing audit. Assets: AuditLog, AuditRecorder.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-AUDIT-LOG | Audit log interface | src/main/java/com/vinekeepers/audit/AuditLog.java |
| ASSET-AUDIT-RECORDER | Record tool calls and outcomes | src/main/java/com/vinekeepers/audit/AuditRecorder.java |

# Sub-pages

- [How it works](audit/how-it-works.md)
- [Change log](audit/change-log.md)
- [Known issues](audit/known-issues.md)
- [Decisions](audit/decisions.md)
- [Contracts](audit/contracts.md)
- [Tests](audit/tests.md)
- [Diagrams](audit/diagrams.md)
