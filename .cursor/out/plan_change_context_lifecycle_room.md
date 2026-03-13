# Change context (for plan_change / implement)

## Scope

**Request-derived:** Vinekeepers lifecycle room architecture — Phase 1. Discord intake (Luna) unchanged until Launch; then workflow-driven provisioning: create_channel, provision_bot_instance, create_lifecycle_context, post_channel_message, launch_cursor_run with atomic bind. Arrietty as configured bot template in config; generic runtime bot instances and lifecycle context; Discord and Cursor in their own layers. State-driven post_channel_message; lifecycle context with indexes by channelId and externalRunId; rename direction from LunaCloudRunState to generic run record; bot id vs display name. **Out of scope:** Phase 2 channel-local routing.

**Impacted registry slice:** core-registry.yml (FEAT-CURSOR-GATHERING, FEAT-CONFIG, FEAT-WORKFLOW, FEAT-ENGINE, FEAT-BOT, FEAT-STATE), connectors-registry.yml (FEAT-CONNECTORS-DISCORD).

**Impacted spec files:** specs/core-registry.yml, specs/connectors-registry.yml.

**Impacted asset paths/ids:** ASSET-BOTS-YAML (config/bots.yaml), ASSET-CONFIG-LOADER, ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-LUNA-RUN-STATE → generic run record (src/.../LunaCloudRunState.java), ASSET-CURSOR-FULL-RUN-TOOL, ASSET-CURSOR-RUN-MONITOR, ASSET-LUNA-WORKFLOW, ASSET-LUNA-STATE, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-DISCORD-REPLY, ASSET-STATE-STORE. **New/added:** Arrietty bot template in config; lifecycle context store/type; create_channel (Discord layer); post_channel_message; provision_bot_instance; workflow actions or steps for provisioning sequence; lifecycle context indexes by channelId and externalRunId.

---

## Per feature / requirement / asset

### FEAT-CURSOR-GATHERING (cursor-gathering)

- **Feature:** Cursor-backed gathering workflow. Status: active. doc_path: mkdoc/features/domain/workflow/cursor-gathering.md. Summary: Luna gathers repo and feature input over Discord, launches Cursor cloud agent run, reports progress/PR back to chat. **Extend:** Luna intake unchanged until Launch; after Launch add provisioning sequence (create_channel, provision_bot_instance, create_lifecycle_context, post_channel_message, launch_cursor_run with atomic bind).
- **Requirements:** REQ-LUNA-001 — Luna bot Discord mention trigger, multi-turn gather, Cursor Cloud API; workflowRef luna_cursor; cursor.fullRun stores run state and lastRepo; CursorCloudRunMonitor polls and relays to Discord. **Acceptance (excerpt):** Bot id luna, discordMention luna; luna_cursor prompts/captures then cursor.fullRun; stores run state (rename to generic run record), acknowledges launch; monitor relays updates to Discord.
- **Anti-patterns (critical):** Hardcoding Discord channel in workflow. Storing secrets in state. Do not rely on discordTrigger alone for Luna activation. Do not log Cursor API key, request body, or full response body. Do not assume a single Cursor API error response shape. Do not serialize null fields in Cursor API request payload.
- **Assets:** ASSET-LUNA-RUN-STATE (path: src/main/java/com/vinekeepers/core/cursor/LunaCloudRunState.java; role: in-memory state for launched run and Discord reply target) → **rename direction:** generic run record, indexes by channelId and externalRunId. ASSET-CURSOR-FULL-RUN-TOOL, ASSET-CURSOR-RUN-MONITOR, ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-BOTS-YAML, ASSET-BOOTSTRAP, ASSET-ENGINE.
- **Doc excerpts — decisions:** Luna remains config-driven in YAML; Discord mention activation in routing; repository ops via cursor.fullRun and Cursor adapter. **Contracts:** Luna config in bots.yaml; ConfigurableWorkflowState for gather state; LunaCloudRunState for launched run (→ generic run record). Cursor adapter: Bearer auth, NON_NULL payload, extractErrorMessageAndCode. **How it works:** Luna flow in luna_cursor; after Launch, cursor.fullRun stores run state; monitor polls and relays to Discord.

### FEAT-CONFIG (config)

- **Feature:** Bot config from YAML. Status: active. doc_path: mkdoc/features/domain/config/config.md. Summary: ConfigLoader loads bot definitions; BotConfig models structure; workflow type/params, routing filters, runtime options.
- **Requirements:** REQ-CONFIG-001 — Load bot definitions, routing, workflows, workflow block, bot runtime options, routing filters (discordTrigger, discordMention, discordAuthors) from YAML.
- **Assets:** ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML. **Add:** Arrietty as configured bot template (template or prototype for lifecycle room bot instances); bot id vs display name in config.
- **Doc excerpt:** ConfigLoader parses workflow.type, workflowRef, discordMention, discordAuthors; workflows map defines DSL.

### FEAT-WORKFLOW (workflow)

- **Feature:** Workflow runners and session lifecycle. Status: active. doc_path: mkdoc/features/domain/workflow/workflow.md.
- **Requirements:** REQ-WORKFLOW-001 — WorkflowRunner runs workflow for event; WorkflowRunnerFactory by type; configurable workflows with session keys; CallActionStep can invoke tools.
- **Assets:** ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-CALL-ACTION-STEP, ASSET-WORKFLOW-DEFINITION, ASSET-SESSION-KEY-STRATEGY, ASSET-SESSION-KEY-STRATEGIES. **Add:** Workflow-driven provisioning steps/actions: create_channel, provision_bot_instance, create_lifecycle_context, post_channel_message, launch_cursor_run (atomic bind).
- **Anti-patterns:** (from REQ-LUNA-001) hardcoding Discord channel in workflow.

### FEAT-ENGINE (engine)

- **Feature:** Event-driven engine orchestration. Status: active. doc_path: mkdoc/features/domain/core/engine.md.
- **Requirements:** REQ-CORE-003 — Engine routes events to bots, runs WorkflowRunner, reasoner, delivers via AppReplySink lifecycle (respondImmediately, sendFollowUp, updateMessage); ReplyTarget from event; no defer in engine.
- **Assets:** ASSET-ENGINE. **Extend:** State-driven post_channel_message (e.g. after provisioning, post to new channel); engine or connector layer for delivery to lifecycle room channel.

### FEAT-CONNECTORS-DISCORD (discord)

- **Feature:** Discord event source and reply. Status: active. doc_path: mkdoc/features/domain/connectors/discord.md.
- **Requirements:** REQ-CONNECTORS-DISCORD-001 — DiscordEventSource, gateway receive/send, DiscordAppReplySink lifecycle; channel send and follow-up/update with optional components.
- **Assets:** ASSET-DISCORD-GATEWAY (JdaDiscordGateway), ASSET-DISCORD-GATEWAY-CONTRACT (DiscordGateway), ASSET-DISCORD-REPLY-SINK, ASSET-DISCORD-REPLY. **Add:** create_channel in Discord layer (gateway or dedicated service) for lifecycle room creation with fallback; post_channel_message (state-driven) to send to a given channelId.
- **Doc excerpt:** Gateway contract: send(channelId, messageId, content[, components]), sendFollowUp, updateMessage. Replies delivered via sink to originating channel; extend for sending to a created lifecycle room channel.

### FEAT-STATE (state)

- **Requirements:** REQ-STATE-001 — StateStore persists/loads per-bot, per-conversation workflow state.
- **Assets:** ASSET-STATE-STORE. **Add:** Lifecycle context store (or extend state store) with lifecycle context records indexable by channelId and externalRunId; generic run record (replacing Luna-specific run state) holds channelId, externalRunId, reply target, status, etc.

### FEAT-BOT (bot)

- **Requirements:** REQ-BOT-003 — BotDefinition composes persona, model, workflow, tool policy; bots configured via YAML.
- **Assets:** ASSET-BOT-DEFINITION, ASSET-PERSONA. **Add:** Arrietty as bot template; generic runtime bot instances (provision_bot_instance) with bot id vs display name distinction.

---

## Schema constraints

- **core-registry / connectors-registry:** Use existing requirement/asset keys; no new spec schema keys. New assets can be added with id, kind, path, role, requires, feature_ids. Do not delete requirements; mark deprecated if retiring.
- **Guardrails:** Do not delete requirements. Do not remove required functionality without permission. Do not change schema files or invent new keys. Repair spec drift before coding. Avoid anti_patterns on impacted requirements/assets. Feature slugs are not bot ids.

---

## Phase 1 implementation checklist (from request)

1. **Luna intake unchanged until Launch** — No change to Luna gathering flow before the Launch confirmation step.
2. **Provisioning sequence after Launch:** create_channel → provision_bot_instance → create_lifecycle_context → post_channel_message → launch_cursor_run with atomic bind.
3. **Room creation with fallback** — Create dedicated lifecycle channel (Discord); fallback behavior when creation fails.
4. **State-driven post_channel_message** — Ability to post to a channel (e.g. lifecycle room) from state/context.
5. **Lifecycle context** — Context type/store with indexes by channelId and externalRunId.
6. **Rename direction:** LunaCloudRunState → generic run record (class/type and references).
7. **Bot id vs display name** — Arrietty template and runtime instances: config and runtime distinguish id and display name.
8. **Discord and Cursor in their own layers** — Clear separation; Discord for channel/reply operations, Cursor for run launch and monitor.
9. **Do not implement Phase 2** — No channel-local routing in this change.
