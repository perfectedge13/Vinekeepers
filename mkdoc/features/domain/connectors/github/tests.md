# Tests

# Coverage

Unit + manual: the GitHub stub starts and stops without publishing placeholder startup events, and the connector still compiles as an `EventSource`.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-GITHUB-EVENT-SOURCE-STUB | GitHubEventSourceTest | `com.vinekeepers.connectors.GitHubEventSourceTest` | `startThenStopSetsRunningFlagWithoutPublishingStubEvent` | Verify the GitHub stub does not publish placeholder startup events |
| MANUAL-CONNECTORS-GITHUB | GitHub connector compiles and implements interface | — | — | Verify GitHubEventSource exists and implements EventSource |

