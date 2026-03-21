# Tests

| Test | Class | Intent |
|------|-------|--------|
| UNIT-GADGET-RESOLVE-BRANCH | `GadgetResolveBranchActionTest` | Branch resolution from choice vs custom |
| UNIT-GADGET-PROJECT-REGISTRY | `GadgetProjectRegistryTest` | YAML load; optional `gitRemote` / `extraVars` |
| UNIT-START-GADGET-DEPLOY | `StartGadgetDeployActionTest` | Unknown project; dry-run sendAs |
| UNIT-GADGET-ROUTING | `RouterTest.routeMatchesGadgetWhenMentionAndChannelAllowlist` | Mention + channel allowlist |
| — | `RouterTest.routeDoesNotMatchWhenChannelInDiscordChannelsExclude` | Excluded channel |
| — | `DeployResolveProjectActionTest` | Single vs multi project / default env |
| — | `GitRemoteBranchesChoiceProviderTest` | Fallback when no `gitRemote` |
| — | `GadgetDeployRunnerEscapeTest` | Ansible `-e` value escaping |
| — | `DiscordAppReplySinkTest.channelTargetWithReplyAsBotIdUsesThatBotsGatewayNotDefault` | Handling bot gateway |
| — | `ConfigLoaderTest.buildRouterParsesDiscordChannelsExclude` | YAML `discordChannelsExclude` |
