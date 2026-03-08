# Troubleshooting

# Entries

## Workflow stays waiting and never resumes

Cause: The bot's `sessionKeyStrategy` does not resolve the same conversation key for the follow-up event, or the configured workflow is waiting on a different field than the incoming event provides.

Steps:
1. Check the bot's `conversationMode` and `sessionKeyStrategy` in `config/bots.yaml`.
2. Inspect the configured workflow steps to confirm `prompt_for_field` and `capture_field` use the same field name.
3. Confirm the follow-up event still matches routing and reaches the same bot.

Resolution: Align the session key strategy with the connector event shape and ensure the capture step reads the field that the prompt requested.

## Tool-backed workflow action is rejected

Cause: `CallActionStep` resolved a tool name, but `ToolRunner` rejected it because the tool is missing or disallowed by the bot's `ToolPolicy`.

Steps:
1. Verify the tool is registered in `Bootstrap`.
2. Confirm the workflow action id matches the registered tool name or a legacy workflow action.
3. Check the bot's allow and deny rules in `ToolPolicy`.

Resolution: Register the tool, fix the action id, or adjust the bot policy so the intended tool is allowed.

## Discord event replies do not appear

Cause: The event reached the engine, but no reply sender was configured or the workflow and reasoner both returned no reply text.

Steps:
1. Verify Discord is the source for the incoming event.
2. Confirm `DiscordEventSource` is wired as the engine's `DiscordReplySender`.
3. Check whether the workflow returned a waiting state instead of a message.
4. Check whether the reasoner produced `replyText` or only a state patch and proposed tool calls.

Resolution: Rewire the reply sender in `Bootstrap`, or update the workflow or reasoner so one stage produces the expected user-facing reply.

