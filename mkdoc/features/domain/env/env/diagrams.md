# Diagrams

# Architecture

See [Architecture](../../../../architecture.md).

# Feature flow

Load .env → set system properties → Env.get reads → Bootstrap checks `HEALTH_PORT` → optional HealthServer serves `/` and `/health`.

