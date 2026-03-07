# How it works

# Overview

After tool execution, engine records event, bot, actions, and results to AuditLog. Audit is long-lived and human-facing (who, what, outcome).

# Flow

1. Engine completes tool run.
2. AuditRecorder or implementation logs event, bot, approved actions, results/errors.
3. Records persist for audit trail.

# Inputs and outputs

- **Inputs:** Event, bot id, actions, results. **Outputs:** Audit record persisted.
