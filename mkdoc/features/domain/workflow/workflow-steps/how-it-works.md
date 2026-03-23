# How it works

# Overview

Configured workflows are defined by `WorkflowDefinition` and executed as ordered `WorkflowStep` instances. Each step returns a `StepResult` with a `StepOutcome`, optional stored values, and next-step information so the runner can continue, wait, branch, complete, or surface an error.

# Flow

1. Config loads a `WorkflowDefinition` with ordered step configs.
2. `ConfigurableWorkflowRunner` resolves each config into a concrete step implementation.
3. A step executes and returns `StepResult` describing the next outcome.
4. The runner stores state updates, advances, waits, branches, or completes based on `StepOutcome`. For `branch` steps, blank or empty string values are not truthy; use value-based `when: { key, value: "" }` to route on blank input.
5. Coordinator planning paths that call **`OpenAiChatClient`** with a non-blank **`OpenAiCallContext.activitySummary`** (from **`run_request_expansion_llm`**, **`run_llm_planning_synthesis`**, **`StructuredLlmArtifactUpsertPass`**, and related wiring) invoke **`OpenAiPlanningProgressSink`** immediately before each HTTP completion; production **`Bootstrap`** registers **`OpenAiPlanningProgressPoster`**, which posts Discord lines prefixed with **`Update:`** (markdown bold in the rendered message) via **`post_channel_message`** unless **`OPENAI_PLANNING_DISCORD_PROGRESS`** is false. The same client logs per-call duration at INFO and, when **`OPENAI_LOG_PLANNING_BODIES`** is not false, truncated `sk-*`-redacted system, user, and assistant text (or a failure hint), capped by **`OPENAI_LOG_BODY_MAX_CHARS`**.

# Inputs and outputs

- **Inputs:** Workflow definition, current configurable workflow state, event payload (including interaction payload: interactionId, token, customId, values when kind is interaction), and optional registered actions or tools.
- **Outputs:** Step outcomes, stored state values, next-step selection, optional reply text, and optional `OutboundResponse` (richReply with `ResponseIntent`) for the engine to deliver via the connector sink.
- **Planning OpenAI observability (env):** **`OPENAI_PLANNING_DISCORD_PROGRESS`** (default true), **`OPENAI_LOG_PLANNING_BODIES`** (default true), **`OPENAI_LOG_BODY_MAX_CHARS`** (default 4096, clamped 256–32768), plus existing planning API keys documented under the **env** feature and **README** / **`.env.example`**.

