# Diagrams

# Context

```mermaid
flowchart LR
  Event[Event] --> Normalized[NormalizedEventContext]
  Normalized --> Filter[RoutingFilter]
  Filter --> Router[Router]
  Router --> Matches[Matched bot ids]
```

