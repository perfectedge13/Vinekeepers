# Diagrams

# Architecture

See [Architecture](../../../../architecture.md) for the main system diagram (event sources → event bus → engine → router, state, audit, tools).

# Feature flow

```mermaid
flowchart TB
  Bus[EventBus] --> Engine[VinekeepersEngine]
  Engine --> Router[Router]
  Engine --> Runner[WorkflowRunner]
  Engine --> Reasoner[Reasoner]
  Engine --> Tools[ToolRunner]
  Engine --> Reply[Connector reply path]
```

