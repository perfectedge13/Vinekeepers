# Contracts

# APIs

None. Routing is an internal runtime capability consumed by the engine.

# Schemas

`Routing` holds filter configuration, and `NormalizedEventContext` exposes connector-neutral fields that filters can evaluate.

# Interfaces

- **`Router`:** returns the bot ids whose routing rules match the event.
- **`EventFilter`:** contract for evaluating an event against routing criteria.
- **`RoutingFilter`:** built-in filter implementation for configured predicates.
- **`NormalizedEventContext`:** normalized view of connector payload fields used for routing and session decisions.

