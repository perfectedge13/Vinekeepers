# Tests

# Coverage

EventBusTest verifies publish/subscribe and event delivery. AsyncEngineEventSubscriberTest verifies the engine delegate runs on the executor thread.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-EVENT-BUS | EventBusTest | com.vinekeepers.events.EventBusTest | (various) | Verify publish/subscribe and event delivery |
| UNIT-ASYNC-ENGINE-EVENT-SUBSCRIBER | AsyncEngineEventSubscriberTest | com.vinekeepers.events.AsyncEngineEventSubscriberTest | delegateRunsOnExecutorThreadNotCaller | Verify async handoff off caller thread |

