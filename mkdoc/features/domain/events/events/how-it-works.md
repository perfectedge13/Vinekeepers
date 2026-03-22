# How it works

# Overview

EventBus.publish(event) delivers to all subscribers synchronously on the publisher thread. Event has sourceId, kind, payload. Connectors implement EventSource and publish; the running engine is subscribed via AsyncEngineEventSubscriber so the heavy VinekeepersEngine work runs on `vinekeepers-engine-events-*` threads instead of JDA listener threads.

# Flow

1. Source calls EventBus.publish(Event).
2. Bus invokes each subscriber's onEvent on the publisher thread.
3. AsyncEngineEventSubscriber enqueues work; the delegate engine runs on the executor.

# Inputs and outputs

- **Inputs:** Event (sourceId, kind, payload). **Outputs:** Delivery to subscribers.

