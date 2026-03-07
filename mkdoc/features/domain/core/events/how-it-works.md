# How it works

# Overview

EventBus.publish(event) delivers to all subscribers. Event has sourceId, kind, payload. Connectors implement EventSource and publish; engine implements EventSubscriber and receives.

# Flow

1. Source calls EventBus.publish(Event).
2. Bus delivers event to all registered subscribers.
3. Subscribers (e.g. engine) handle event.

# Inputs and outputs

- **Inputs:** Event (sourceId, kind, payload). **Outputs:** Delivery to subscribers.
