# Change log

# Entries

## 2026-03-07

Luna uses configured workflow luna_cursor (workflow.type: configured, workflowRef: luna_cursor). Engine uses registered ConfigurableWorkflowRunner; runner.run loads state, runs luna_cursor steps (ask project, ask codeChange, cursor.fullRun, done), persists, returns reply to Discord. CursorCloudGatheringRunner removed.

## 2026-03-06

Implementation updates: DiscordReplySender, DiscordEventSource, GatheringState (ex-LunaConversationState), CursorCloudGatheringWorkflow (ex-LunaGatheringWorkflow), CursorCloudAdapter/CursorCloudAdapterImpl, VinekeepersEngine, Bootstrap, config/bots.yaml. Tests updated: GatheringStateTest, CursorCloudGatheringWorkflowTest, CursorCloudAdapterImplTest, DiscordEventSourceTest, VinekeepersEngineTest.

## Initial

Luna bot implemented: Discord trigger /Luna, GatheringState (ex-LunaConversationState), CursorCloudGatheringWorkflow (ex-LunaGatheringWorkflow), CursorCloudAdapter/CursorCloudAdapterImpl, engine wiring and Discord reply path; config in config/bots.yaml.
