# How DevOps deploy works

1. **Routing** — Ops bot (example id `gadget` in `bots.yaml`) uses `discordMention` + `discordChannels` (and optional excludes on other bots).
2. **devops_deploy** stores `channelId` via `extract_event` (`context.channelId`).
3. **Main menu** — `mainMenu`: Ansible playbook deploy, Docker Compose up / stop / status, or Cancel.
4. **deploy_resolve_project** — single target in `config/deploy-targets.yaml`, or `DEPLOY_DEFAULT_PROJECT` / `GADGET_DEFAULT_PROJECT`, sets `deployTargetId` and `deployNeedsProjectPick`; otherwise user picks via **`deployTargets`**.
5. **Ansible path** — branch choice **`deployGitBranches`** (`git ls-remote` from target `gitRemote`), **`resolve_deploy_branch`** → `deployBranch`, thread **`deploy-progress`**, **`start_ansible_deploy`** calls **`AnsiblePlaybookDeployRunner.submit`** with **`__botId`** for `sendAs`.
6. **Compose paths** — same target resolution, **`deployComposeServices`** (allowlisted names + `_all`), thread, **`run_deploy_compose`** with bind `composeOperation` (`up` / `stop` / `ps`). **`HostComposeOpsRunner`** runs **`docker compose`** by default or **`cursor agent --print ...`** when `hostOpsExecutor: cursor_agent` on the target.
7. **Kill switches** — `DEPLOY_ANSIBLE_DISABLED` / `GADGET_ANSIBLE_DISABLED`; `DEPLOY_HOST_OPS_DISABLED` / `GADGET_HOST_OPS_DISABLED`.

**Host vs container:** mount `/var/run/docker.sock` and bind-mount compose working directories so paths in YAML match the runtime. Prefer **direct** executor in production; Cursor Agent is LLM-mediated. See the project **README** Docker section for image layout.
