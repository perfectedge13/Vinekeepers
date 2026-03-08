# Contracts

# APIs

None. EventBus.publish(Event); subscribe(EventSubscriber).

# Schemas

Event: sourceId, kind, payload (opaque).

# Interfaces

EventSource: start(EventBus). EventSubscriber: onEvent(Event). EventBus: publish, subscribe.

