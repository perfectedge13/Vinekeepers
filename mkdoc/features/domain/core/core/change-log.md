# Change log

# Entries

## 2026-03-07

Bootstrap now creates per-bot workflow runners with runtime conversation settings, shared tool registration, and configurable workflow support. The engine runs workflow first, then builds reasoner context from workflow output and current state, applies reasoner state patches, executes proposed tools, and delivers Discord replies through the configured reply sender.

## 2026-03-06

Implementation updates: VinekeepersEngine.java, Bootstrap.java; tests VinekeepersEngineTest modified. Engine routes events to bots and delivers workflow replies to Discord when source is Discord.

## 2025-03-05

Nova-spec run: per-area features added to registry (env, config, bot, events, state, tools, audit, reasoner, workflow, connectors, core); mkdoc dossiers and mkdocs nav updated for all 11 features.

## 2025-03-05

Initial mkdoc sync from nova-spec: bootstrapped mkdoc and feature dossier for Core (Vinekeepers core engine and bot framework).

