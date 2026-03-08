# Diagrams

# Context

```mermaid
flowchart LR
  Bus[EventBus] --> Engine[VinekeepersEngine]
  Engine --> Router[Router]
  Engine --> Runner[WorkflowRunner]
  Engine --> Reasoner[Reasoner]
  Engine --> Tools[ToolRunner]
  Engine --> Reply[Connector reply path]
```

