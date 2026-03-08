# Vinekeepers project standards

## Summary

Vinekeepers coding standards, app flow, and config-based bots. Always-applied rule.

## Key points

- **Java 21**; package layout per README: core, config, bot, events, workflow, connectors, state, env, audit, tools, reasoner.
- Prefer **final** classes and clear constructors; avoid mutable shared state.
- Follow spec-workflow and guardrails (Schema Gate, Spec Drift Gate, no new spec keys, repair drift first).
- Validation: run `mvn test` and `mvn compile`; use **PowerShell** for multi-step commands.
- **Flow:** VinekeepersApp → Bootstrap → loadConfig → Engine; EventBus → Engine; Connectors publish to bus; Engine routes to bots, runs workflow then reasoner, persists state, audit, reply to Discord when source is Discord.
- Bots and routing from YAML (config/bots.yaml); no hardcoded bot list. Adding a bot: add entry in YAML; optionally register custom Workflow or Reasoner in Bootstrap.


