# Tests

# Coverage

Unit tests cover event routing through the engine plus reasoner integration for state patches, proposed tools, and reply selection.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ENGINE | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | registerBotAndOnEventRoutesToBot | Verify engine receives events and routes them to bots |
| UNIT-ENGINE-REASONER-INTEGRATION | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | reasonerAppliesStatePatchRunsToolAndRepliesWhenWorkflowIsSilent | Verify reasoner state patches, tool proposals, and reply selection are applied by the engine |
| UNIT-ENGINE-DISCORD-NO-MENTION-NO-WAITING | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | discordMessageWithoutMentionAndNoWaitingSession_doesNotRouteToLuna | Discord message without @mention and no waiting session does not route to Luna |
| UNIT-ENGINE-DISCORD-MENTION | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | discordMessageWithMention_routesToLuna | Discord message with @mention routes to Luna |
| UNIT-ENGINE-WAITING-SESSION | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | followUpMessageWithoutMention_sameUserAndChannel_continuesWorkflowWhenLunaHasWaitingInput | Follow-up message without @mention in same user and channel continues workflow when Luna has WAITING_INPUT |
| UNIT-ENGINE-SESSION-SCOPE | VinekeepersEngineTest | com.vinekeepers.core.VinekeepersEngineTest | messageFromDifferentUserOrChannel_doesNotContinueSession | Message from different user or channel does not continue session |

