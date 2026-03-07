# How it works

# Overview

Engine builds ReasonerInput (event, context, state); calls Reasoner.think(input). ReasonerOutput: statePatch, proposedActions, nextState. StubReasoner or real LLM/rules implementation used by engine.

# Flow

1. Engine loads state, builds ReasonerInput.
2. reasoner.think(input) → ReasonerOutput.
3. Engine applies statePatch, passes proposedActions to ToolPolicy and ToolRunner.

# Inputs and outputs

- **Inputs:** ReasonerInput (event, context, state). **Outputs:** ReasonerOutput (statePatch, proposedActions, nextState).
