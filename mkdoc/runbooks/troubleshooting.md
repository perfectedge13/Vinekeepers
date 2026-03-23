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

## No bot matched for Discord event

Cause: The router did not match any bot for the event (e.g. `discordAuthors` or `discordMention` filter not satisfied, or event kind/channel not in routing).

Steps:
1. Enable DEBUG logging for the router/engine to see which event was received (source, kind, authorId, actorUsername, mentions, channelId).
2. Check `config/bots.yaml` routing for the intended bot: `discordAuthors` accepts numeric Discord user id (matches `actorId`) or username (matches `actorUsername`, case-insensitive); `discordMention` applies only to message events.
3. For interaction events (buttons, modals), trigger and mention are not checked—only author, channel, and other criteria apply.

Resolution: Align routing filter with the event payload (use numeric id when possible) or add the user/channel to the bot's routing filter.

## Discord event replies do not appear

Cause: The event reached the engine, but no reply sender was configured or the workflow and reasoner both returned no reply text.

Steps:
1. Verify Discord is the source for the incoming event.
2. Confirm `DiscordEventSource` is wired as the engine's `DiscordReplySender`.
3. Check whether the workflow returned a waiting state instead of a message.
4. Check whether the reasoner produced `replyText` or only a state patch and proposed tool calls.

Resolution: Rewire the reply sender in `Bootstrap`, or update the workflow or reasoner so one stage produces the expected user-facing reply.

## Coordinator intake thread shows internal paths or enum-like tokens

Cause: Older planning packet, critique, or readiness formatting echoed artifact ids, YAML paths, or internal status tokens in Discord text.

Steps:
1. Confirm the deployment includes **`PlanningUserFacingCopy`** and updated **`PlanningThreadPacketFormatter`**, **`PlanCritiqueSupport`**, **`RunPlanCritiqueAndReadinessAction`**, **`PlanningPacketDepthEvaluator`**, and **`GenericReadinessEvaluator`** (see **workflow-registry** and **cursor-gathering** feature docs).
2. Check **`config/work-profiles.yaml`** for human-readable field titles used when mapping internal paths.
3. Compare thread copy with **`planReadinessStatus`** in workflow state: branches stay machine-readable; only template/interpolation should show plain-language labels.

Resolution: Upgrade to the build that routes coordinator Discord copy through **`PlanningUserFacingCopy`**; if a new gap appears, trace the emitting action and extend **`PlanningUserFacingCopy`** or the work profile rather than surfacing raw ids in YAML templates.

