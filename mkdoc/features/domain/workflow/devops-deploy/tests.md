# Tests

# Coverage

Validation tests for **REQ-GADGET-001** cover routing, target registry (including compose-only neo4j/wikijs), branch resolution, Ansible dry-run/sendAs, compose argv and service choices, and workflow YAML branch indices (including `compose_restart` on the main menu).

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-GADGET-RESOLVE-BRANCH | ResolveDeployBranchActionTest | com.vinekeepers.workflow.actions.ResolveDeployBranchActionTest | — | Deploy branch from choice vs custom |
| UNIT-GADGET-PROJECT-REGISTRY | DeployTargetRegistryTest | com.vinekeepers.devops.DeployTargetRegistryTest | — | YAML load, compose fields, neo4j/wikijs paths |
| UNIT-START-GADGET-DEPLOY | StartAnsibleDeployActionTest | com.vinekeepers.workflow.actions.StartAnsibleDeployActionTest | — | Unknown target; dry-run sendAs with `__botId` |
| UNIT-GADGET-ROUTING | RouterTest | com.vinekeepers.bot.RouterTest | routeMatchesGadgetWhenMentionAndChannelAllowlist | DevOps bot routing |
| UNIT-DEPLOY-RESOLVE-PROJECT | DeployResolveProjectActionTest | com.vinekeepers.workflow.actions.DeployResolveProjectActionTest | — | deploy_resolve_project skip vs pick |
| UNIT-DEPLOY-GIT-BRANCHES-CHOICE | GitRemoteBranchesChoiceProviderTest | com.vinekeepers.providers.GitRemoteBranchesChoiceProviderTest | — | Branch choice fallback without git remote |
| UNIT-HOST-COMPOSE-OPS-RUNNER | HostComposeOpsRunnerTest | com.vinekeepers.devops.HostComposeOpsRunnerTest | — | Compose argv / prompt: up, stop, restart, ps |
| UNIT-DEVOPS-DEPLOY-WORKFLOW-YAML | DevopsDeployWorkflowYamlTest | com.vinekeepers.config.DevopsDeployWorkflowYamlTest | — | Branch indices; main menu `compose_restart` |
| UNIT-DEPLOY-COMPOSE-SERVICES-CHOICE | DeployComposeServicesChoiceProviderTest | com.vinekeepers.providers.DeployComposeServicesChoiceProviderTest | — | Service choices from manifest allowlist |
| UNIT-ANSIBLE-DEPLOY-RUNNER-ESCAPE | AnsiblePlaybookDeployRunnerEscapeTest | com.vinekeepers.devops.AnsiblePlaybookDeployRunnerEscapeTest | — | Ansible `-e` escaping |
