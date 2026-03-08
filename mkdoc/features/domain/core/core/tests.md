# Tests

# Coverage

Unit tests cover EnvLoader, VinekeepersApp, VinekeepersEngine, ConfigLoader, Router, ToolPolicy, EventBus, StateStore. Manual or compile-time checks cover spec validation, bot definition structure, tools, audit, reasoner, workflow, connectors.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ENV-LOADER | EnvLoader loads and Env reads | com.vinekeepers.env.EnvLoaderTest | (various) | Verify .env loading and Env.get behavior |
| UNIT-VINEKEEPERS-APP | VinekeepersAppTest | com.vinekeepers.VinekeepersAppTest | (various) | Verify application entrypoint and bootstrap |
| UNIT-ENGINE | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | (various) | Verify engine receives events and routes to bots |
| UNIT-CONFIG-LOADER | ConfigLoaderTest | com.vinekeepers.config.ConfigLoaderTest | (various) | Verify YAML config loading |
| UNIT-ROUTER | RouterTest | com.vinekeepers.bot.RouterTest | (various) | Verify event routing and filter matching |
| UNIT-TOOL-POLICY | ToolPolicyTest | com.vinekeepers.bot.ToolPolicyTest | (various) | Verify tool policy enforcement |
| UNIT-EVENT-BUS | EventBusTest | com.vinekeepers.events.EventBusTest | (various) | Verify publish/subscribe and event delivery |
| UNIT-STATE-STORE | StateStoreTest | com.vinekeepers.state.StateStoreTest | (various) | Verify state load/save and key scoping |
| MANUAL-SPEC-VALIDATE | Validate spec index and registry | — | — | Ensure specs exist and pass schema validation |
| MANUAL-BOT-DEF | Bot definition structure | — | — | Verify BotDefinition and related models |
| MANUAL-TOOLS | Tool registry and runner | — | — | Verify tools used by engine and policy |
| MANUAL-AUDIT | Audit log usage | — | — | Verify audit invoked by engine |
| MANUAL-REASONER | Reasoner interface | — | — | Verify reasoner used in core loop |
| MANUAL-WORKFLOW | Workflow interface | — | — | Verify workflow part of bot definition |
| MANUAL-CONNECTORS | Connectors compile and implement interface | — | — | Verify connector sources exist |

