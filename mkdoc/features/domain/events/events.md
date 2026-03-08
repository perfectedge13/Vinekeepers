# Event bus and publish/subscribe

# Status

active

# Summary

Event bus publish and subscribe (REQ-EVENTS-001). EventBus allows sources to publish and subscribers to receive; Event carries sourceId, kind, payload; EventSource and EventSubscriber interfaces.

# Key assets

| Asset | Role | Path |
|-------|------|------|
| ASSET-EVENT-BUS | Publish and subscribe to events | src/main/java/com/vinekeepers/events/EventBus.java |
| ASSET-EVENT | Event payload and metadata | src/main/java/com/vinekeepers/events/Event.java |
| ASSET-EVENT-SOURCE | Event source interface | src/main/java/com/vinekeepers/events/EventSource.java |
| ASSET-EVENT-SUBSCRIBER | Event subscriber interface | src/main/java/com/vinekeepers/events/EventSubscriber.java |

# Sub-pages

- [How it works](events/how-it-works.md)
- [Change log](events/change-log.md)
- [Known issues](events/known-issues.md)
- [Decisions](events/decisions.md)
- [Contracts](events/contracts.md)
- [Tests](events/tests.md)
- [Diagrams](events/diagrams.md)

