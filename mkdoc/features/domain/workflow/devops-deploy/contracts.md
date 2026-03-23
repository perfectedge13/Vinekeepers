# Contracts

# APIs

- Discord-only operator surface: menu prompts and thread posts; no public HTTP API for this feature.

# Schemas

- **Deploy targets manifest** (`config/deploy-targets.yaml`, or `config/deploy-targets.docker.yaml` when `DEPLOY_TARGETS_PATH` is set): `targets` (or legacy `projects`) with `id`, `label`, optional Ansible `playbook`, optional `compose` block (`file`, `workingDirectory`, `services`, `hostOpsExecutor`).
- **Workflow state / binds:** `composeOperation` ∈ `up`, `stop`, `restart`, `ps` (aliases normalized by `ComposeOperation`); `deployTargetId`, `deployBranch`, progress thread metadata; `__botId` for outbound `sendAs`.

# Interfaces

- **Workflow actions:** `resolve_deploy_branch` → `deployBranch`. `start_ansible_deploy` spreads `deployAnsibleMessage`, `deployAnsibleTargetId` (and legacy `gadgetDeployMessage` / `gadgetDeployTargetId` for older `done` templates). `run_deploy_compose` spreads `deployComposeMessage`, `deployComposeTargetId`; bind **`composeOperation`** as above.
- **Ansible extra vars:** `project_id`, `branch`, `deploy_target`, `deploy_project`, plus legacy `gadget_project` / `gadget_branch`.
- **Env:** `DEPLOY_*` with `GADGET_*` aliases where noted in `.env.example` (`DEPLOY_TARGETS_PATH`, Ansible, host ops, Cursor Agent binary). The repo's `compose.yaml` sets `DEPLOY_TARGETS_PATH=/app/config/deploy-targets.docker.yaml`, mounts the Docker socket, and bind-mounts the host stack paths declared in `config/deploy-targets.yaml` for direct compose.
