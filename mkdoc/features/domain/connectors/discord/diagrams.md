# Diagrams

# Architecture

Discord connector implements EventSource; Bootstrap wires it to the EventBus. See [Architecture](../../../../architecture.md).

# Feature flow

External Discord → DiscordEventSource → Event → EventBus.publish.
