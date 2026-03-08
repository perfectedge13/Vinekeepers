# How it works

# Overview

StateStore.load(botId, conversationKey) returns state or empty. StateStore.save persists state for that bot and conversation. Engine loads state before reasoner, saves after applying state patch.

# Flow

1. Engine gets botId and conversation key from event.
2. Load state from StateStore.
3. After reasoner and actions, save updated state.

# Inputs and outputs

- **Inputs:** botId, conversationKey, state object. **Outputs:** Loaded state; persistence.

