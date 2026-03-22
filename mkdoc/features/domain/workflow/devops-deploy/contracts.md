# Contracts

- **Workflow actions:** `resolve_deploy_branch` returns `deployBranch`. `start_ansible_deploy` spreads `deployAnsibleMessage`, `deployAnsibleTargetId` (and legacy `gadgetDeployMessage` / `gadgetDeployTargetId` for older `done` templates). `run_deploy_compose` spreads `deployComposeMessage`, `deployComposeTargetId`.
- **Ansible extra vars:** `project_id`, `branch`, `deploy_target`, `deploy_project`, plus legacy `gadget_project` / `gadget_branch`.
- **Env:** `DEPLOY_*` with `GADGET_*` aliases where noted in `.env.example` (`DEPLOY_TARGETS_PATH`, Ansible, host ops, Cursor Agent binary).
