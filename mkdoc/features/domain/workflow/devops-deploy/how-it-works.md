# How it works

# Overview

The **devops_deploy** workflow gives an operator a Discord menu from a direct bot mention in the allowlisted ops channel, or from a thread under that channel when the parent room is allowlisted: run an Ansible playbook against a configured target, or run Docker Compose **up**, **stop**, **restart**, or **status** (`ps`) on the deploy host for allowlisted services. Discord role pings do not satisfy the `discordMention` filter. Long output is posted to a **`deploy-progress`** thread for mutating compose actions and **`DevOps Progress`** for `compose ps`; menus stay in the parent channel. Compose targets may be **compose-only** (no playbook), using a host **`workingDirectory`** so `docker compose` resolves the stack file on the host.

# Flow

1. **Routing** — Ops bot (example id `gadget` in `bots.yaml`) uses `discordMention` + `discordChannels` (and optional excludes on other bots); channel checks honor the current channel or the parent room for Discord thread events.
2. **extract_event** — Stores `channelId` in workflow context.
3. **Main menu** — Ansible playbook deploy, Docker Compose up / stop / restart / status (`ps`), or Cancel.
4. **deploy_resolve_project** — Single target in `config/deploy-targets.yaml`, or `DEPLOY_DEFAULT_PROJECT` / `GADGET_DEFAULT_PROJECT`, sets `deployTargetId` and `deployNeedsProjectPick`; otherwise user picks via **`deployTargets`**.
5. **Ansible path** — Branch choice **`deployGitBranches`** (`git ls-remote` from target `gitRemote`), **`resolve_deploy_branch`** → `deployBranch`, thread **`DevOps Status`**, **`start_ansible_deploy`** calls **`AnsiblePlaybookDeployRunner.submit`** with **`__botId`** for `sendAs`.
6. **Compose paths** — Same target resolution, **`deployComposeServices`** (allowlisted names + `_all`) for up, stop, and restart, thread, **`run_deploy_compose`** with bind **`composeOperation`** (`up` / `stop` / `restart` / `ps`; `ps` may omit service selection via `_all`). **`HostComposeOpsRunner`** runs **`docker compose`** by default or **`cursor agent --print ...`** when `hostOpsExecutor: cursor_agent` on the target.
7. **Kill switches** — `DEPLOY_ANSIBLE_DISABLED` / `GADGET_ANSIBLE_DISABLED`; `DEPLOY_HOST_OPS_DISABLED` / `GADGET_HOST_OPS_DISABLED`.

# Inputs and outputs

- **Inputs:** Discord message (direct bot mention + allowlisted room or thread under that room), deploy target manifest (`config/deploy-targets.yaml` or env override), optional git remote for branch list, env toggles for Ansible/host ops and paths.
- **Outputs:** Thread or parent-channel progress posts; Ansible or compose exit status surfaced in workflow state and Discord text.
- **Host vs container:** Mount `/var/run/docker.sock` and bind-mount each compose **`workingDirectory`** from `deploy-targets.yaml` at the same path inside the Vinekeepers container (e.g. repo root for `vinekeepers`, host stack dirs for **`neo4j`** / **`wikijs`** examples). Prefer **direct** executor in production; Cursor Agent is LLM-mediated. See the project **README** Docker section for image layout and Gadget host mounts.
