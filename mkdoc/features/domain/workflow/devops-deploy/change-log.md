# Change log

# Entries

## 2026-03-21

Initial ops bot flow, Ansible runner, REQ-GADGET-001, FEAT-DEVOPS-GADGET.

## 2026-03-22

Renamed workflow to `devops_deploy`, package `com.vinekeepers.devops`, `config/deploy-targets.yaml`, compose + optional Cursor Agent CLI; removed Java hardcoding of bot id (`__botId` only).

Compose menu adds **restart**; `deploy-targets.yaml` adds example **`neo4j`** / **`wikijs`** stacks; corrected `devops_deploy` branch step indices for compose paths; README documents host Docker socket + bind mounts for compose working dirs.

## 2026-03-23

Added `compose.host-ops.yaml` and `config/deploy-targets.docker.yaml` so containerized Vinekeepers can reach host Docker and mounted stack paths without shadowing `/app`.

`HostComposeOpsRunner` now logs startup warnings when direct compose targets are configured in a container but the Docker socket, stack directory, or compose file is missing.

Shipped Wiki.js compose allowlist uses service keys **`wikijs`** and **`db`** (not the Postgres `container_name` **`wikijs-db`**), matching `docker compose` service arguments.
