# How it works

# Overview

Workflow interface: handle(Event, BotContext, S state) → WorkflowResult&lt;S&gt;. Defines state machine or step graph per bot. The Engine does not invoke Workflow directly; it uses a **WorkflowRunner** per bot. WorkflowRunner.run(event, stateStore, botId) loads state, runs the workflow, persists state, and returns the reply. Runners are created by **WorkflowRunnerFactory** from the bot's workflow.type (stub or configured) and optional workflow.params in YAML. For type **configured**, the factory builds ConfigurableWorkflowRunner from the YAML `workflows:` section (workflowRef or inline steps) and a WorkflowActionRegistry; steps run in order: ask_input, call_action, branch, done. Luna uses configured workflow luna_cursor. StubWorkflow/StubState and StubWorkflowRunner used when no custom workflow.

# Flow

1. Config defines each bot with optional workflow.type and workflow.params (for configured: workflowRef or inline steps).
2. Bootstrap builds a WorkflowActionRegistry, registers Cursor actions (e.g. cursor_cloud, echo), builds a WorkflowRunner per bot via WorkflowRunnerFactory, and registers it with the engine (registerRunner(botId, runner)).
3. On event: Engine gets runner for botId; runner.run(event, stateStore, botId) loads state, invokes workflow (or runs ConfigurableWorkflowRunner step loop), persists, returns reply.
4. WorkflowResult: nextState, actions, done. StepResult (for configurable steps): nextStepIndex, payload, done.

# Inputs and outputs

- **Inputs:** Event, BotContext, state. **Outputs:** WorkflowResult (nextState, actions, done). Runner.run inputs: Event, StateStore, botId; output: reply (e.g. message to send to Discord). ConfigurableWorkflowState: step index + key-value map; steps read/write state via WorkflowActionRegistry for call_action.
