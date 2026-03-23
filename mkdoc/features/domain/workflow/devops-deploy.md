# DevOps deploy — Discord menu, Ansible and Docker Compose

# Status

active

# Summary

Operator-driven deploys from Discord (**REQ-GADGET-001**). Any bot may use `workflowRef: devops_deploy`; the shipped example bot id is **gadget** in `config/bots.yaml` with `discordMention: gadget` and `discordChannels`. `gadget` activates only from a **direct bot/app mention** in the allowlisted ops room (or a thread under that room via parent-channel matching); Discord role pings do **not** satisfy `discordMention`. Flow: main menu (Ansible vs Docker Compose up/stop/restart/status) → `deploy_resolve_project` → optional **`deployTargets`** pick → branch or compose service pick → `create_thread` (`deploy-progress` for mutating compose actions, `DevOps Progress` for `compose ps`) → **`start_ansible_deploy`** or **`run_deploy_compose`**. The shipped menu copy uses standard punctuation in Discord, including `Docker Compose — status (ps)`. Progress uses **`OutboundDeliveryRouter.sendAs`** with workflow **`__botId`** (never a hardcoded Java default). Direct compose operations require the Vinekeepers runtime image to include the Docker CLI + Compose plugin and the container to mount `/var/run/docker.sock` plus each target `workingDirectory` at a path visible inside the container. Host-native runs keep **`config/deploy-targets.yaml`**; the repo's containerized host-ops path uses **`compose.host-ops.yaml`** plus **`config/deploy-targets.docker.yaml`** via `DEPLOY_TARGETS_PATH`. Vinekeepers now logs startup warnings when direct compose targets are configured but the socket, stack directory, or compose file is missing in a containerized runtime. See [How it works](devops-deploy/how-it-works.md).

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-GADGET-PROJECT-REGISTRY | Load deploy targets YAML | src/main/java/com/vinekeepers/devops/DeployTargetRegistry.java |
| ASSET-GADGET-DEPLOY-RUNNER | Async ansible-playbook + Discord lines | src/main/java/com/vinekeepers/devops/AnsiblePlaybookDeployRunner.java |
| ASSET-GADGET-PROJECTS-CHOICE-PROVIDER | `deployTargets` dynamic choices | src/main/java/com/vinekeepers/providers/DeployTargetsChoiceProvider.java |
| ASSET-GADGET-RESOLVE-BRANCH-ACTION | `resolve_deploy_branch` | src/main/java/com/vinekeepers/workflow/actions/ResolveDeployBranchAction.java |
| ASSET-START-GADGET-DEPLOY-ACTION | `start_ansible_deploy` | src/main/java/com/vinekeepers/workflow/actions/StartAnsibleDeployAction.java |
| ASSET-GADGET-PROJECTS-YAML | Target list (Ansible + compose) | config/deploy-targets.yaml |
| ASSET-GADGET-PROJECTS-DOCKER-YAML | Containerized target list for direct compose host-ops | config/deploy-targets.docker.yaml |
| ASSET-COMPOSE-HOST-OPS-YAML | Docker Compose for Vinekeepers (Gadget mounts + monitoring network) | compose.yaml |
| ASSET-HOST-COMPOSE-OPS-RUNNER | `docker compose` / optional Cursor Agent CLI | src/main/java/com/vinekeepers/devops/HostComposeOpsRunner.java |
| ASSET-RUN-DEPLOY-COMPOSE-ACTION | `run_deploy_compose` | src/main/java/com/vinekeepers/workflow/actions/RunDeployComposeAction.java |
| ASSET-DEPLOY-COMPOSE-SERVICES-CHOICE-PROVIDER | `deployComposeServices` allowlist | src/main/java/com/vinekeepers/providers/DeployComposeServicesChoiceProvider.java |
| ASSET-BOTS-YAML | Operator bot + `workflowRef` | config/bots.yaml |
| ASSET-BOOTSTRAP | Register actions and choice providers | src/main/java/com/vinekeepers/core/Bootstrap.java |
| ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY | Named provider wiring | src/main/java/com/vinekeepers/workflow/DynamicChoiceProviderRegistry.java |
| ASSET-CREATE-THREAD-ACTION | DevOps progress/status threads | src/main/java/com/vinekeepers/workflow/actions/CreateThreadAction.java |

# Sub-pages

- [How it works](devops-deploy/how-it-works.md)
- [Change log](devops-deploy/change-log.md)
- [Known issues](devops-deploy/known-issues.md)
- [Decisions](devops-deploy/decisions.md)
- [Contracts](devops-deploy/contracts.md)
- [Tests](devops-deploy/tests.md)
- [Diagrams](devops-deploy/diagrams.md)
