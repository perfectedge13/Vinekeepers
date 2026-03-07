# Change log

# Entries

## 2026-03-07

Bootstrap creates WorkflowRunner per bot via WorkflowRunnerFactory (from bot's workflow.type/params) and registerRunner(botId, runner). Engine uses registered runners; runner.run(event, stateStore, botId) for workflow execution.

## 2026-03-06

Implementation updates: VinekeepersEngine.java, Bootstrap.java; tests VinekeepersEngineTest modified. Engine routes events to bots and delivers workflow replies to Discord when source is Discord.

## 2025-03-05

Nova-spec run: per-area features added to registry (env, config, bot, events, state, tools, audit, reasoner, workflow, connectors, core); mkdoc dossiers and mkdocs nav updated for all 11 features.

## 2025-03-05

Initial mkdoc sync from nova-spec: bootstrapped mkdoc and feature dossier for Core (Vinekeepers core engine and bot framework).
