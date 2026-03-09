# Plan-change output: Rich app interactions (Phases 1A–1E)

## Result

**Pass**

## One-line summary

Impact set and change_context produced for implementing Phases 1A–1E (interactions package, engine sink registry, Discord lifecycle and auto-defer, workflow YAML intents, spec and docs); core and connectors registries plus engine, workflow, reasoner, and Discord adapter assets in scope.

## Impact set

**Impacted registry spec file paths:**
- `specs/core-registry.yml`
- `specs/connectors-registry.yml`

**Impacted asset paths / ids:**
- **New:** `src/main/java/com/vinekeepers/interactions/` (package: ResponseIntent, OutboundResponse, ReplyTarget, Capabilities, connector contract interface); `src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java`
- **Changed (core):** `src/main/java/com/vinekeepers/core/VinekeepersEngine.java` (ASSET-ENGINE), `src/main/java/com/vinekeepers/core/Bootstrap.java` (ASSET-BOOTSTRAP), `src/main/java/com/vinekeepers/workflow/WorkflowRunResult.java` (ASSET-WORKFLOW-RUN-RESULT), `src/main/java/com/vinekeepers/workflow/StepResult.java` (ASSET-STEP-RESULT), `src/main/java/com/vinekeepers/reasoner/ReasonerOutput.java` (ASSET-REASONER-OUTPUT), `src/main/java/com/vinekeepers/workflow/ConfigurableWorkflowRunner.java` (ASSET-CONFIGURABLE-WORKFLOW-RUNNER), `src/main/java/com/vinekeepers/workflow/WorkflowDefinition.java` (ASSET-WORKFLOW-DEFINITION), `src/main/java/com/vinekeepers/workflow/steps/PromptForFieldStep.java` (ASSET-PROMPT-FOR-FIELD-STEP), `src/main/java/com/vinekeepers/workflow/steps/CaptureFieldFromEventStep.java` (ASSET-CAPTURE-FIELD-STEP), `src/main/java/com/vinekeepers/bot/NormalizedEventContext.java` (ASSET-NORMALIZED-EVENT-CONTEXT), `config/bots.yaml` (ASSET-BOTS-YAML)
- **Changed (connectors):** `src/main/java/com/vinekeepers/connectors/DiscordGateway.java` (ASSET-DISCORD-GATEWAY-CONTRACT), `src/main/java/com/vinekeepers/connectors/JdaDiscordGateway.java` (ASSET-DISCORD-GATEWAY), `src/main/java/com/vinekeepers/connectors/DiscordEventSource.java` (ASSET-DISCORD-SOURCE)
- **Docs/specs:** `mkdoc/architecture.md`, `mkdoc/features/domain/connectors/discord.md`, `mkdoc/features/domain/workflow/workflow.md`, `mkdoc/features/domain/workflow/workflow-steps.md`, `README.md`, `specs/core-registry.yml`, `specs/connectors-registry.yml`

**removal_or_rename:** false

## Change context

Full change context document for the implement step:

**Path:** `.cursor/out/plan_change_change_context_rich_interactions.md`

The orchestrator should pass this path (or the file contents) to the **implement** step so it can use spec and doc constraints without re-reading full YAML and mkdoc.
