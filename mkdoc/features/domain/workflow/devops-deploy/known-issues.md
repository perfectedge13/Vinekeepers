# Known issues

- Long ansible or compose output can hit Discord rate limits; runners cap lines posted (`DEPLOY_HOST_OPS_MAX_DISCORD_LINES`).
- Cursor Agent mode is slower and non-deterministic; use `hostOpsExecutor: direct` when possible.
- Runtime image includes **ansible** (apt) for playbook deploy; size vs slim images is a tradeoff.
