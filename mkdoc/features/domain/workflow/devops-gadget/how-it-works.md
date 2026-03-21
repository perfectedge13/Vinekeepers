# How it works

1. User mentions **@Gadget** in the allowlisted ops text channel (`discordChannels` in `config/bots.yaml`).
2. **gadget_deploy** stores `channelId` via `extract_event` (`context.channelId`).
3. **Menu** — Deploy to Docker or Cancel (`present_choices`).
4. **Project** — `prompt_for_field` with `choiceProvider: gadgetProjects` reads `config/gadget-projects.yaml`.
5. **Branch** — `main`, `develop`, or **Other** + text capture; **`gadget_resolve_branch`** sets `deployBranch`.
6. **create_thread** — anchor thread `gadget-deploy` under the parent channel; `deliveryChannelId` holds thread id (or `THREAD_CREATE_FAILED`).
7. **start_gadget_deploy** — validates project id, picks progress target (thread or parent channel), calls **`GadgetDeployRunner.submit`**.
8. **GadgetDeployRunner** — background thread runs `ansible-playbook` with `-e gadget_project=…` and `-e gadget_branch=…`; emits Discord lines for TASK/fatal/etc. via **`router.sendAs(..., "gadget")`**. Set **`GADGET_ANSIBLE_DISABLED=true`** to skip playbook (dry run).

**Host access from Docker:** mount `/var/run/docker.sock` and directories your playbooks need; install `ansible` in the image or a derived image. Example playbook: `ansible/playbooks/site.yml`.
