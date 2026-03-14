# Contracts

# APIs

Spec index: specs/specs.yml (scope, change_triggers, specs, interfaces, validation). Registry schema: specs/schema/req-registry.schema.json.

# Schemas

specs-index.schema.json: scope, change_triggers, specs, interfaces, optional validation, optional domains. req-registry.schema.json: schema, project, enums, dependencies, assets, requirements, optional features.

# Interfaces

VinekeepersApp main; Bootstrap builds EventBus, StateStore, Router, Engine; loadConfig(path) loads YAML and registers bots and workflows.
