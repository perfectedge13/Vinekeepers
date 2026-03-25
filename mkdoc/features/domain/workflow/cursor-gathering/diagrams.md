# Diagrams

# Architecture

See [Architecture](../../../../architecture.md) for the main system diagram.

# Feature flow

```mermaid
flowchart TB
  Discord[Discord event] --> Routing[Routing matches luna]
  Routing --> Runner[ConfigurableWorkflowRunner]
  Runner --> Gather[Gather repo and change]
  Gather --> Provision[Create room and intake thread]
  Provision --> Arrietty[arrietty_room_v2 planning flow]
  Arrietty --> Launch[launch_cursor_run]
  Launch --> Adapter[CursorCloudAdapter]
  Runner --> Reply[Discord reply]
```

