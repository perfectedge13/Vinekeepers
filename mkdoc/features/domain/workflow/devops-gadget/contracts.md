# Contracts

- **Workflow actions:** `gadget_resolve_branch` returns a map with `deployBranch`. `start_gadget_deploy` returns spread `gadgetDeployMessage` and `gadgetDeployTargetId` for the final `done` step.
- **Ansible extra vars:** `gadget_project` (project id), `gadget_branch` (branch name).
- **Env:** `GADGET_ANSIBLE_ROOT`, `GADGET_ANSIBLE_BINARY`, `GADGET_ANSIBLE_INVENTORY`, `GADGET_ANSIBLE_DISABLED` (see `.env.example`).
