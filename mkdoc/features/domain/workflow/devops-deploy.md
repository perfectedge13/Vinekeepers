# DevOps deploy (config-driven)

# Status

active

# Summary

Operator-driven deploys from Discord (**REQ-GADGET-001**). Any bot may use `workflowRef: devops_deploy`; the shipped example bot id is **gadget** in `config/bots.yaml` with `discordMention: gadget` and `discordChannels`. Flow: main menu (Ansible vs Docker Compose up/stop/ps) → `deploy_resolve_project` → optional **`deployTargets`** pick → branch or compose service pick → `create_thread` (`deploy-progress`) → **`start_ansible_deploy`** or **`run_deploy_compose`**. Progress uses **`OutboundDeliveryRouter.sendAs`** with workflow **`__botId`** (never a hardcoded Java default). Manifest: **`config/deploy-targets.yaml`** (or `DEPLOY_TARGETS_PATH` / legacy `GADGET_PROJECTS_PATH`). See [How it works](devops-deploy/how-it-works.md).

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-DEPLOY-TARGET-REGISTRY | Load deploy targets YAML | src/main/java/com/vinekeepers/devops/DeployTargetRegistry.java |
| ASSET-ANSIBLE-PLAYBOOK-DEPLOY-RUNNER | Async ansible + Discord lines | src/main/java/com/vinekeepers/devops/AnsiblePlaybookDeployRunner.java |
| ASSET-HOST-COMPOSE-OPS-RUNNER | Docker Compose / Cursor Agent CLI | src/main/java/com/vinekeepers/devops/HostComposeOpsRunner.java |
| ASSET-DEPLOY-TARGETS-CHOICE-PROVIDER | `deployTargets` choices | src/main/java/com/vinekeepers/providers/DeployTargetsChoiceProvider.java |
| ASSET-DEPLOY-COMPOSE-SERVICES-CHOICE-PROVIDER | `deployComposeServices` | src/main/java/com/vinekeepers/providers/DeployComposeServicesChoiceProvider.java |
| (deploy) | `deployGitBranches` / `deploy_resolve_project` | `GitRemoteBranchesChoiceProvider`, `DeployResolveProjectAction` |
| ASSET-RESOLVE-DEPLOY-BRANCH-ACTION | `resolve_deploy_branch` | src/main/java/com/vinekeepers/workflow/actions/ResolveDeployBranchAction.java |
| ASSET-START-ANSIBLE-DEPLOY-ACTION | `start_ansible_deploy` | src/main/java/com/vinekeepers/workflow/actions/StartAnsibleDeployAction.java |
| ASSET-RUN-DEPLOY-COMPOSE-ACTION | `run_deploy_compose` | src/main/java/com/vinekeepers/workflow/actions/RunDeployComposeAction.java |
| ASSET-DEPLOY-TARGETS-YAML | Target list | config/deploy-targets.yaml |

# Subpages

- [How it works](devops-deploy/how-it-works.md)
- [Contracts](devops-deploy/contracts.md)
- [Tests](devops-deploy/tests.md)
- [Change log](devops-deploy/change-log.md)
- [Known issues](devops-deploy/known-issues.md)
- [Decisions](devops-deploy/decisions.md)
- [Diagrams](devops-deploy/diagrams.md)
