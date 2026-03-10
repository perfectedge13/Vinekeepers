# Diagrams

# Architecture

See [Architecture](../../../../architecture.md) for the main system diagram.

# Feature flow

```mermaid
flowchart TB
  Event[Event] --> Normalized[NormalizedEventContext]
  Normalized --> Filter[RoutingFilter]
  Filter --> Router[Router]
  Router --> Matches[Matched bot ids]
```

