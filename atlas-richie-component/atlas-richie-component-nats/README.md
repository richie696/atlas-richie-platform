# Atlas Richie NATS Component

Spring Boot NATS component with Core NATS pub/sub and RPC, JetStream publishing and consumption, DLQ forwarding, context propagation, idempotency, tracing, Key-Value, and Object Store access.

## Quick start

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-component-nats</artifactId>
</dependency>
```

```yaml
platform:
  component:
    nats:
      server: nats://nats-1.local:4222,nats://nats-2.local:4222
      connection:
        name: ${spring.application.name}
      jetstream:
        enabled: true
        streams:
          - name: ORDERS
            subjects: [orders.>]
            consumers:
              - name: order-processor
                filter-subject: orders.created
                ack-policy: explicit
                ack-wait: 10m
                max-ack-pending: 20
                max-deliver: 5
                backoff: [1m, 5m, 15m, 1h, 6h]
                nak-delay: 30s
        dlq:
          enabled: true
```

The component starts its connection and provisions configured JetStream resources through `NatsComponent`.

```java
@RequiredArgsConstructor
@Service
class OrderPublisher {
    private final NatsComponent nats;

    void publish(OrderCreated event) {
        nats.bus().publish("orders.created", event);              // Core NATS
        nats.stream().publish("ORDERS", "orders.created", event); // JetStream
    }
}
```

## APIs

```java
// Core NATS subscription. Release the associated dispatcher when no longer needed.
Subscription subscription = nats.bus().subscribe("orders.>", message -> {
    // handle message
});
nats.bus().unsubscribe(subscription);

// RPC client. The overload without a Duration uses request.default-timeout.
OrderView view = nats.bus().request("orders.get", request, OrderView.class);

// RPC server. Keep the registration and close it to unregister the endpoint.
NatsEndpoint.Registration registration = nats.endpoint().registerHandler(
        "orders.get", GetOrderRequest.class, service::get);

// JetStream continuous consumer: success is acked; an exception is nak'ed.
MessageConsumer consumer = nats.stream().consume("ORDERS", "order-processor", message -> {
    // handle message
});

// Existing JetStream buckets.
KeyValue flags = nats.keyValue("config");
ObjectStore files = nats.objectStore("uploads");
```

`JetStreamBus.publish(streamName, subject, message)` validates that the subject belongs to the named stream before publishing.

## Important configuration

| Property | Default | Description |
| --- | --- | --- |
| `server` | `nats://localhost:4222` | Comma-separated NATS URLs. |
| `tracing.enabled` | `true` | Enable OpenTelemetry spans and W3C propagation. OpenTelemetry API is a required dependency of this component. |
| `header-propagation.enabled` | `true` | Enable propagation of the configured whitelist. |
| `idempotent.enabled` | `false` | Enable consumer-side deduplication. |
| `idempotent.datasource` | `memory` | Use `memory` for a single instance or `redis` with the cache component. |
| `request.default-timeout` | `5s` | Timeout used by RPC overloads without a `Duration`. |
| `jetstream.enabled` | `false` | Enable JetStream provisioning and APIs. |
| `jetstream.streams[].consumers[].ack-wait` | `30s` | Maximum processing window before redelivery; set above the expected task duration. |
| `jetstream.streams[].consumers[].max-ack-pending` | `1000` | Total in-flight task cap for the consumer; use this as the Agent Worker concurrency ceiling. |
| `jetstream.streams[].consumers[].backoff` | empty | Server-side redelivery intervals for unacknowledged tasks; overrides `ack-wait`. |
| `jetstream.streams[].consumers[].nak-delay` | `5s` | Delay used after the handler throws, preventing transient failures from hot-looping. |
| `jetstream.dlq.enabled` | `false` | Enable max-delivery advisory handling and DLQ forwarding. |
| `tls.enabled` | `false` | Configure TLS. With no custom truststore, JVM default trusted CAs are used. |

DLQ configuration is always nested below `platform.component.nats.jetstream.dlq`; there is no top-level `nats.dlq` namespace.

## Lifecycle notes

- Core NATS is fire-and-forget. Use JetStream for durable delivery.
- JetStream consumers provide at-least-once delivery; idempotency is still recommended.
- During `stream().consume`, the component sends `inProgress()` every one-third of the consumer's `ack-wait`; long LLM calls therefore do not expire while still running.
- The component owns the connection lifecycle. Stop returned JetStream consumers and close RPC registrations when they are dynamically created.
