# Tests

| Spec id | Test class | Intent |
|---------|------------|--------|
| UNIT-GADGET-RESOLVE-BRANCH | `ResolveDeployBranchActionTest` | Branch from choice vs custom |
| UNIT-GADGET-PROJECT-REGISTRY | `DeployTargetRegistryTest` | YAML `targets` / legacy `projects`, compose block |
| UNIT-START-GADGET-DEPLOY | `StartAnsibleDeployActionTest` | Unknown target; dry-run sendAs with `__botId` |
| — | `AnsiblePlaybookDeployRunnerEscapeTest` | Ansible `-e` escaping |
| — | `HostComposeOpsRunnerTest` | Compose argv / prompt building |
| — | `DeployComposeServicesChoiceProviderTest` | Service choices from manifest |
