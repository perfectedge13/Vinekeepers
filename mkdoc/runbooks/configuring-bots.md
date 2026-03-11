# Configuring bots

# Procedures

## Locate bot configuration

- **Primary config:** `config/bots.yaml` in the project root. This file defines all bots and routing.
- **Project reference:** `.cursor/project.yml` may set `scan.bots_config: config/bots.yaml` so tooling knows where bot config lives.

## Define a bot

1. Add an entry under `bots:` in `config/bots.yaml`.
2. Set **required** `id` (e.g. `luna`). This id is used in routing and when registering workflows/reasoners in Bootstrap.
3. Set **persona** with `name` and `systemPrompt` (and optional `model`, `toolPolicy`, `memory` as supported).
4. Set **workflow** — e.g. `type: configured` and `params.workflowRef: <workflow_id>` to reference a workflow from the `workflows:` DSL in the same file (or a custom workflow registered in Bootstrap for that bot id).

Structure must match what ConfigLoader expects: `bots[].id`, `bots[].persona` (name, systemPrompt), optional model/toolPolicy/memory; see [vinekeepers-standards](../cursor/rules/vinekeepers-standards.md) and `config/bots.yaml` at the project root.

**Template bots (lifecycle room):** The **Arrietty** bot in `config/bots.yaml` is a template for lifecycle room instances provisioned at runtime per channel. Provisioning workflows use `call_action` steps: `create_channel` (Discord), `create_lifecycle_context`, `provision_bot_instance`, `post_channel_message`, and `launch_cursor_run`. Runtime instances are stored as `RuntimeBotInstance`; context as `LifecycleContext` in `LifecycleContextStore`.

## Configure routing

1. Under `routing:` add an entry with `botId` matching a bot’s `id`.
2. Set **filter** to control when events are routed to that bot, e.g.:
   - `discordMention: "luna"` — bot is invoked when mentioned on Discord.
   - `discordAuthors: ["novawilde13_72571"]` — restrict to specific Discord user IDs.
   - Other filter keys (e.g. discordChannels, repos, prLabels, prAuthors) as supported by the Router.

Events that match the filter are sent to the engine for that `botId`; the engine runs the bot’s workflow then reasoner, persists state, and replies (e.g. via Discord) when the source is Discord.

## Workflow refs and DSL

- The `workflows:` block in `config/bots.yaml` defines workflow DSLs by id (e.g. `luna_cursor`, `simple_echo`).
- A bot’s `workflow.params.workflowRef` must reference one of these ids (or a custom workflow registered in code for that bot).
- Workflow steps use types such as `ask_input`, `prompt_for_field`, `capture_field`, `call_action`, `branch`, `done`; see the existing `workflows:` section in `config/bots.yaml` for examples (choiceProvider, intent, branches, storeIn).

## Adding a new bot end-to-end

1. Edit `config/bots.yaml`: add a new object to `bots:` with `id`, `persona`, and `workflow` (with `workflowRef` pointing to a workflow in `workflows:` or a custom one).
2. Add a corresponding `routing:` entry with `botId` and the desired `filter`.
3. If the bot needs a custom Workflow or Reasoner implementation, register it in Bootstrap for that bot id; otherwise the engine may use StubWorkflow/StubReasoner.
4. Restart the application so ConfigLoader reloads `config/bots.yaml`.
