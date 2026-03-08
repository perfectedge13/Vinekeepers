# Recovery

# Procedures

## Recover a broken conversational session

1. Identify the bot id and the expected session key strategy for the conversation.
2. Inspect the persisted workflow state for the affected bot and conversation key.
3. If the stored state is no longer compatible with the workflow definition, clear or repair the session entry in the state store.
4. Re-run the interaction from the first prompt to confirm the workflow now pauses and resumes correctly.

## Recover after connector reply path regression

1. Verify the connector still publishes inbound events and the engine still receives them.
2. Reconnect the reply sender wiring in `Bootstrap` so Discord replies can be delivered.
3. Run the relevant unit tests for engine and connector reply behavior.
4. Re-trigger a Discord workflow and confirm the reply appears in the channel or thread.

