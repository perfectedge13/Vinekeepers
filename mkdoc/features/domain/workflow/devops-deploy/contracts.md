# Contracts

# APIs

- Discord-only operator surface: menu prompts and thread posts; no public HTTP API for this feature.

# Schemas

- **Deploy targets manifest** (`config/deploy-targets.yaml`): `targets` (or legacy `projects`) with `id`, `label`, optional Ansible `playbook`, optional `compose` block (`composeFile`, `workingDirectory`, `composeServices`, `hostOpsExecutor`).
- **Workflow state / binds:** `composeOperation` ∈ `up`, `stop`, `restart`, `ps` (aliases normalized by `ComposeOperation`); `deployTargetId`, `deployBranch`, progress thread metadata; `__botId` for outbound `sendAs`.

# Interfaces

- **Workflow actions:** `resolve_deploy_branch` → `deployBranch`. `start_ansible_deploy` spreads `deployAnsibleMessage`, `deployAnsibleTargetId` (and legacy `gadgetDeployMessage` / `gadgetDeployTargetId` for older `done` templates). `run_deploy_compose` spreads `deployComposeMessage`, `deployComposeTargetId`; bind **`composeOperation`** as above.
- **Ansible extra vars:** `project_id`, `branch`, `deploy_target`, `deploy_project`, plus legacy `gadget_project` / `gadget_branch`.
- **Env:** `DEPLOY_*` with `GADGET_*` aliases where noted in `.env.example` (`DEPLOY_TARGETS_PATH`, Ansible, host ops, Cursor Agent binary).
