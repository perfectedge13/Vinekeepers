# How it works

1. User mentions **@Gadget** in the allowlisted ops text channel (`discordChannels` in `config/bots.yaml`).
2. **gadget_deploy** stores `channelId` via `extract_event` (`context.channelId`).
3. **Menu** — Deploy to Docker or Cancel (`present_choices`).
4. **deploy_resolve_project** — if `config/gadget-projects.yaml` has a single project, or `DEPLOY_DEFAULT_PROJECT` / `GADGET_DEFAULT_PROJECT` matches an id, sets `gadgetProject` and skips the next pick; otherwise `deployNeedsProjectPick` is true.
5. **Project** (optional) — `prompt_for_field` with `choiceProvider: gadgetProjects`.
6. **Branch** — `choiceProvider: deployGitBranches` runs `git ls-remote --heads` using the selected row’s `gitRemote` / `repo`, capped and sorted, plus **Other** + text capture; **`gadget_resolve_branch`** sets `deployBranch`.
7. **create_thread** — anchor thread `gadget-deploy` under the parent channel; `deliveryChannelId` holds thread id (or `THREAD_CREATE_FAILED`).
8. **start_gadget_deploy** — validates project id, picks progress target (thread or parent channel), calls **`GadgetDeployRunner.submit`** using workflow **`__botId`** for `sendAs` when present.
9. **GadgetDeployRunner** — background thread runs `ansible-playbook` with `-e project_id`, `-e branch`, legacy `-e gadget_project` / `gadget_branch`, and manifest **`extraVars`**; emits Discord lines for TASK/fatal/etc. via **`router.sendAs`**. Set **`DEPLOY_ANSIBLE_DISABLED`** or **`GADGET_ANSIBLE_DISABLED=true`** to skip playbook (dry run).

**Luna vs ops channel:** Luna’s routing can include `discordChannelsExclude` for the same ops channel id so button/select interactions there do not match Luna (only author checks apply to interactions today).

**Host access from Docker:** the runtime image is **Ubuntu Jammy** and installs **ansible** via **apt** (reliable; avoids Alpine/musl pip issues). Mount `/var/run/docker.sock` and host paths your playbooks need. Example playbook: `ansible/playbooks/site.yml` writes a staging manifest under `/tmp/gadget-deploy-staging` (override with env **`GADGET_DEPLOY_STAGING`**) — extend it with checkout, build, and service tasks.
