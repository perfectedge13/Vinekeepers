# Diagrams

# Architecture

See [Architecture](../../../../architecture.md) for system context. Luna runs as a bot with configured workflow **luna_cursor**; the engine uses ConfigurableWorkflowRunner and StateStore.

# Feature flow

```mermaid
flowchart TB
  subgraph Input
    Discord[/Luna or message]
  end
  subgraph Engine
    Bus[EventBus]
    Engine[VinekeepersEngine]
    Router[Router]
    Runner[ConfigurableWorkflowRunner luna_cursor]
    Store[StateStore]
    Adapter[CursorCloudAdapter]
  end
  Discord --> Bus
  Bus --> Engine
  Engine --> Router
  Router --> Runner
  Runner --> Store
  Runner --> Adapter
  Adapter --> Runner
  Runner --> Engine
  Engine --> Discord
```

Flow: Discord → EventBus → Engine → Router → ConfigurableWorkflowRunner (luna_cursor) + StateStore → cursor.fullRun action → CursorCloudAdapter; reply path: WorkflowResult message → Engine → DiscordReplySender → Discord.
