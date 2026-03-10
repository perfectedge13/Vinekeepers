# Diagrams

# Architecture

See [Architecture](../../../../architecture.md) for the main system diagram.

# Feature flow

```mermaid
flowchart TB
  Def[WorkflowDefinition] --> Step[WorkflowStep]
  Step --> Result[StepResult]
  Result --> Outcome{StepOutcome}
  Outcome --> Continue[Continue or branch]
  Outcome --> Wait[Waiting]
  Outcome --> Complete[Complete]
  Outcome --> Error[Error]
```

