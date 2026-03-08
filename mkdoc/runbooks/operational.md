# Operational

# Procedures

## Validate implementation changes

1. Compile: `mvn compile`
2. Run tests: `mvn test`
3. If specs changed, run `npm run validate-specs`
4. Reconcile docs with the `nova-code` `mk` step when feature behavior or contracts changed

## Run the application locally

1. Ensure Java 21 and Maven are installed.
2. Ensure `config/bots.yaml` matches the intended bot and workflow configuration.
3. Optionally place a `.env` file in the project root for connector or adapter configuration.
4. Run `com.vinekeepers.VinekeepersApp` from the IDE or your normal Java launch path.

## Verify conversational workflow configuration

1. Confirm the target bot has the correct `conversationMode` and `sessionKeyStrategy` in `config/bots.yaml`.
2. Confirm its `workflow.type` and `workflow.params` point to the intended configured workflow.
3. If the workflow uses tool-backed actions, confirm the tools are registered in `Bootstrap` and allowed by the bot's `ToolPolicy`.

## Check Discord reply flow

1. Start the app with Discord enabled.
2. Trigger the bot from a Discord event that matches routing.
3. Confirm the workflow or reasoner reply is returned through `DiscordReplySender` and the conversation state is saved under the expected session key.

