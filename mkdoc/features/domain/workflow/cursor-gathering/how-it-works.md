# How it works

# Overview

The shipped Luna flow is configured in `config/bots.yaml` as workflow `luna_cursor`. Routing activates the `luna` bot from `discordMention: luna`, the configurable runner gathers repository and feature inputs across conversation turns, and `cursor.fullRun` launches an official Cursor Cloud Agent run on a feature branch with auto PR enabled.

# Flow

1. A Discord message referencing `@Luna` matches the `luna` bot through routing.
2. The configured runner executes `luna_cursor`, gathering repository and feature fields into conversation state with `prompt_for_field` and `capture_field`.
3. `CallActionStep` invokes `cursor.fullRun`, which resolves the GitHub repository, launches `POST /v0/agents`, stores a `LunaCloudRunState`, and returns an acknowledgement.
4. `CursorCloudRunMonitor` polls `GET /v0/agents/{id}` and `GET /v0/agents/{id}/conversation` for state changes and assistant feedback.
5. The engine and monitor send launch/progress/final replies back to Discord when the source is Discord.

# Inputs and outputs

- **Inputs:** Discord event content or mention metadata, configured Luna workflow steps, per-conversation gathering state, and Cursor API credentials from environment.
- **Outputs:** Updated gathering state, in-memory run-tracking state, Cursor-backed repository side effects (feature branch and PR), and workflow/monitor replies delivered to Discord.

