# How it works

# Overview

Luna is a bot registered with id `luna` and triggered by `/Luna` on Discord. The engine uses a **ConfigurableWorkflowRunner** for Luna (workflowRef **luna_cursor** from config). The runner loads **ConfigurableWorkflowState** from the StateStore, keyed by conversation (e.g. Discord channel id), and runs the luna_cursor steps defined in `config/bots.yaml` (ask project, ask code change, then **cursor.fullRun** action, then done).

# Flow

1. User sends `/Luna` (or message in Luna session). Discord event becomes an internal Event.
2. Router matches bot `luna` (filter discordTrigger `/Luna`). Engine gets the registered WorkflowRunner for `luna` (a ConfigurableWorkflowRunner for luna_cursor).
3. Runner loads state, runs luna_cursor steps in order: ask_input (project), ask_input (code change), call_action (cursor.fullRun, which uses CursorCloudAdapter: createBranch, runNovaCommit, push, optional createPr), done.
4. Workflow reply message is sent back to Discord via the engine’s DiscordReplySender.

# Inputs and outputs

- **Inputs:** Event (Discord message content), current ConfigurableWorkflowState for the conversation. **Outputs:** WorkflowResult with updated state and message; Cursor adapter side effects (branch, commit, push, PR); reply to Discord.
