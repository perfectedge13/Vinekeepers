# Diagrams

# Architecture

See [Vinekeepers architecture](../../../../architecture.md) for system context. This feature sits in the workflow + devops packages and uses Discord as the operator surface.

# Feature flow

```mermaid
flowchart LR
  Discord[Discord] --> Router[Router]
  Router --> WF[devops_deploy]
  WF --> Ansible[start_ansible_deploy]
  WF --> Compose[run_deploy_compose]
  Ansible --> AP[AnsiblePlaybookDeployRunner]
  Compose --> HC[HostComposeOpsRunner]
```
