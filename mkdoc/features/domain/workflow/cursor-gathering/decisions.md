# Decisions

# Selected decisions

- Luna remains config-driven in YAML; the feature documents the shipped workflow rather than hardcoded bot registration.
- Discord mention activation belongs to routing, while the gathered project/code-change sequence belongs to this workflow feature.
- Repository operations are delegated through `cursor.fullRun` and the Cursor adapter instead of being embedded directly in workflow steps.

