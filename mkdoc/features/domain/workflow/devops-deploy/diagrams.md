# Diagrams

```mermaid
flowchart LR
  Discord[Discord] --> Router[Router]
  Router --> WF[devops_deploy]
  WF --> Ansible[start_ansible_deploy]
  WF --> Compose[run_deploy_compose]
  Ansible --> AP[AnsiblePlaybookDeployRunner]
  Compose --> HC[HostComposeOpsRunner]
```
