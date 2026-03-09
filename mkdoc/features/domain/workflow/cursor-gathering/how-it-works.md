# How it works

# Overview

The shipped Luna flow is configured in `config/bots.yaml` as workflow `luna_cursor`. Routing activates the `luna` bot from `discordMention: luna` (and optional `discordAuthors` to restrict which Discord users can trigger Luna), the configurable runner gathers repository and feature inputs across conversation turns, and `cursor.fullRun` launches an official Cursor Cloud Agent run on a feature branch with auto PR enabled.

# Flow

1. A Discord message referencing `@Luna` matches the `luna` bot through routing (and satisfies `discordAuthors` when that filter is configured).
2. The configured runner executes `luna_cursor`: the first repo step uses `choiceProvider: githubRepos` to show "Use last repo", GitHub repos (from API, sort=updated), and "Custom repo"; if the user picks "Custom repo", a follow-up prompt captures free-text repo; then a prompt/capture for the code change; then a confirmation step (Launch / Edit repo / Edit request / Cancel) with summary (repo, branch, change). Discord can render choices as buttons or a select menu.
3. After the user confirms Launch, `CallActionStep` invokes `cursor.fullRun`, which resolves the GitHub repository, launches `POST /v0/agents`, stores a `LunaCloudRunState` and `luna:lastRepo:{authorId}`, and returns an acknowledgement.
4. `CursorCloudRunMonitor` polls `GET /v0/agents/{id}` and `GET /v0/agents/{id}/conversation` for state changes and assistant feedback.
5. The engine and monitor send launch/progress/final replies back to Discord when the source is Discord.

# Inputs and outputs

- **Inputs:** Discord event content or mention metadata, configured Luna workflow steps, per-conversation gathering state, and Cursor API credentials from environment.
- **Outputs:** Updated gathering state, in-memory run-tracking state, Cursor-backed repository side effects (feature branch and PR), and workflow/monitor replies delivered to Discord.

