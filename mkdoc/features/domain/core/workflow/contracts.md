# Contracts

# APIs

None. Workflow.handle(event, context, state) → WorkflowResult. WorkflowRunner.run(event, stateStore, botId) returns reply (load state, run workflow, persist, return reply).

# Schemas

WorkflowResult&lt;S&gt;: nextState (S), actions, done flag.

# Interfaces

- **Workflow&lt;S&gt;:** handle(Event, BotContext, S) → WorkflowResult&lt;S&gt;. WorkflowResult model.
- **WorkflowRunner:** run(Event, StateStore, String botId) → reply (e.g. String or message payload). Loads state, runs workflow, persists, returns reply.
- **WorkflowRunnerFactory:** create(workflowType, workflowParams, workflows, actionRegistry) → WorkflowRunner. Types: `stub`, `configured`. For `configured`, resolves WorkflowDefinition from workflowParams.workflowRef (into workflows map) or inline workflowParams.steps; uses WorkflowActionRegistry for CallActionStep. Luna uses configured workflowRef luna_cursor.
- **WorkflowActionRegistry:** register(id, WorkflowAction), resolve by id; used by ConfigurableWorkflowRunner and CallActionStep. Bootstrap registers Cursor actions (e.g. cursor_cloud, echo).
- **ConfigurableWorkflowRunner:** runs WorkflowDefinition steps in order; step types: ask_input, call_action, branch, done. State: ConfigurableWorkflowState (map + step index).
- **WorkflowStep:** interface for step implementations. **StepResult:** nextStepIndex, payload, done. **WorkflowDefinition:** id, list of step configs. **ConfigurableWorkflowState:** mutable step index and key-value store.
- **Step types:** AskForInputStep (stores user input in state), CallActionStep (invokes action by id from registry), BranchStep (branches by condition), DoneStep (completes with optional message/template).
