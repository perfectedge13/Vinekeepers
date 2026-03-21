# Gadget DevOps bot

# Status

active

# Summary

Operator-driven Docker deploys from Discord (**REQ-GADGET-001**). The **gadget** bot uses `workflowRef: gadget_deploy`, routing with `discordMention: gadget` and `discordChannels` (ops channel id is documented at the top of `config/bots.yaml` and listed under the **gadget** routing rule). Flow: action menu → project pick (`gadgetProjects` / `config/gadget-projects.yaml`) → branch (`main` / `develop` / other) → `create_thread` → `start_gadget_deploy` runs **ansible-playbook** in a background thread; progress posts use **`OutboundDeliveryRouter.sendAs`** so they appear as Gadget. See [How it works](devops-gadget/how-it-works.md).

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-GADGET-PROJECT-REGISTRY | Load deploy targets YAML | src/main/java/com/vinekeepers/gadget/GadgetProjectRegistry.java |
| ASSET-GADGET-DEPLOY-RUNNER | Async ansible + Discord lines | src/main/java/com/vinekeepers/gadget/GadgetDeployRunner.java |
| ASSET-GADGET-PROJECTS-CHOICE-PROVIDER | `gadgetProjects` choices | src/main/java/com/vinekeepers/providers/GadgetProjectsChoiceProvider.java |
| ASSET-GADGET-RESOLVE-BRANCH-ACTION | `gadget_resolve_branch` | src/main/java/com/vinekeepers/workflow/actions/GadgetResolveBranchAction.java |
| ASSET-START-GADGET-DEPLOY-ACTION | `start_gadget_deploy` | src/main/java/com/vinekeepers/workflow/actions/StartGadgetDeployAction.java |
| ASSET-GADGET-PROJECTS-YAML | Project list | config/gadget-projects.yaml |

# Subpages

- [How it works](devops-gadget/how-it-works.md)
- [Contracts](devops-gadget/contracts.md)
- [Tests](devops-gadget/tests.md)
- [Change log](devops-gadget/change-log.md)
- [Known issues](devops-gadget/known-issues.md)
- [Decisions](devops-gadget/decisions.md)
- [Diagrams](devops-gadget/diagrams.md)
