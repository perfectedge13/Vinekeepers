# Diagrams

# Architecture

GitHub connector implements EventSource; Bootstrap wires it to the EventBus. See [Architecture](../../../../architecture.md).

# Feature flow

External GitHub → GitHubEventSource → Event → EventBus.publish.

