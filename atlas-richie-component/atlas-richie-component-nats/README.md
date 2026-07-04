# Atlas Richie NATS Component (atlas-richie-component-nats)

> Production-grade **NATS** client component. Connection management, message bus, RPC endpoints, distributed tracing, context propagation, idempotency, **JetStream** persistent streams, and Key-Value / Object Store.

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this component is — and what it isn't](#what-this-component-is-—-and-what-it-isnt)
- [✨ Features](#✨-features)
  - [Core capabilities](#core-capabilities)
  - [Design choices](#design-choices)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
  - [1. Add the dependency](#1-add-the-dependency)
  - [2. Configure](#2-configure)
  - [3. Publish a message](#3-publish-a-message)
  - [4. Subscribe](#4-subscribe)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Core pub/sub](#1-core-pub/sub)
  - [2. Request / reply (RPC)](#2-request-/-reply-rpc)
  - [3. JetStream persistent streams](#3-jetstream-persistent-streams)
  - [4. Key-Value & Object Store](#4-key-value-&-object-store)
  - [5. Idempotency](#5-idempotency)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: NATS vs Kafka — when to choose?](#q1-nats-vs-kafka-—-when-to-choose?)
  - [Q2: Can I use both NATS core and JetStream in the same app?](#q2-can-i-use-both-nats-core-and-jetstream-in-the-same-app?)
  - [Q3: How do I trace NATS calls across services?](#q3-how-do-i-trace-nats-calls-across-services?)
  - [Q4: What happens if the broker is down?](#q4-what-happens-if-the-broker-is-down?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-nats` |
| **Category** | Messaging — NATS pub/sub + JetStream |
| **Hard dependencies** | `io.nats:jnats` (JetStream client) |
| **Compatible with** | NATS Server 2.10+, JetStream enabled |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| Connection management + auto-reconnect | A NATS server (run nats-server separately) |
| Request / reply RPC pattern | Cluster orchestration (use nats-box / helm) |
| JetStream durable streams + KV / Object Store | Long-running saga (use Temporal) |
| Idempotency via JetStream message dedup | MQTT 5.0 (use [`atlas-richie-component-mqtt`](../atlas-richie-component-mqtt/README.md)) |
| Tracing integration (W3C traceparent) | Schema registry (define Protobuf manually) |

## ✨ Features

### `Core` capabilities

- ✅ **Connection management** — auto-reconnect, backoff, drain on shutdown.
- ✅ **Core NATS pub/sub** — fire-and-forget, queue groups, subject wildcards.
- ✅ **Request / reply** — built-in RPC with timeout, headers, inbox.
- ✅ **JetStream** — durable streams, consumers, ack policies, replay, dedup.
- ✅ **Key-Value Store** — distributed config / state.
- ✅ **Object Store** — large blob storage on JetStream.
- ✅ **Idempotency** — message-level dedup window.
- ✅ **Context propagation** — `traceparent`, `tenant_id`, `user_id` via headers.

### `Design` choices

- ✅ **JetStream-first** — durable by default; core NATS only for ephemeral.
- ✅ **Subject hierarchies** — `tenant.{tenantId}.service.{svc}.event.{type}`.
- ✅ **Backoff with jitter** — exponential backoff up to 30s.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-nats
├── config/
│   ├── NatsAutoConfiguration
│   └── NatsProperties
├── connection/
│   ├── NatsConnectionFactory
│   ├── ConnectionListener
│   └── ReconnectHandler
├── publish/
│   ├── NatsPublisher                  ← facade
│   ├── JetStreamPublisher
│   └── MessageHeadersAdapter
├── subscribe/
│   ├── NatsSubscriber                 ← @NatsListener annotation
│   ├── JetStreamConsumer
│   └── DispatcherPerConsumer
├── request/
│   ├── NatsRpcClient                  ← request/reply
│   └── RpcTimeoutManager
├── store/
│   ├── KeyValueStore
│   └── ObjectStore
└── tracing/
    ├── NatsTracingFilter
    └── TraceparentPropagator
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-nats</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    nats:
      servers:
        - nats://nats-1.local:4222
        - nats://nats-2.local:4222
        - nats://nats-3.local:4222
      connection:
        name: ${spring.application.name}
        reconnect-wait-seconds: 2
        reconnect-wait-max-seconds: 30
        ping-interval-seconds: 60
      jetstream:
        enabled: true
        domain: richie
```

### 3) `Publish` a message

```java
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {
    private final NatsPublisher publisher;

    public void publishOrderCreated(OrderCreatedEvent event) {
        publisher.publish(
                "orders." + event.getTenantId() + ".created",
                JsonUtils.toBytes(event),
                NatsHeaders.of("trace_id", TraceContext.traceId())
        );
    }
}
```

### 4) `Subscribe`

```java
@Component
public class OrderEventListener {
    @NatsListener(subject = "orders.*.created", queue = "billing-service")
    public void onOrderCreated(NatsMessage msg) {
        OrderCreatedEvent event = msg.bodyAs(OrderCreatedEvent.class);
        // process
        msg.ack();
    }
}
```

## 🔧 Core Capabilities

### 1) `Core` pub/sub

```java
// Publish (fire-and-forget)
publisher.publish("subject", bytes);

// Subscribe
@NatsListener(subject = "orders.>")     // > matches multiple levels
```

### 2) `Request` / reply (`RPC`)

```java
// Server
@NatsRpcHandler(subject = "users.get")
public User getUser(GetUserRequest req) { return userService.findById(req.getId()); }

// Client
User user = rpcClient.request("users.get", request, User.class, Duration.ofSeconds(2));
```

### 3) `JetStream` persistent streams

```yaml
platform:
  component:
    nats:
      jetstream:
        streams:
          - name: orders
            subjects: ["orders.>"]
            retention: limits
            max-age: 7d
            storage: file
            replicas: 3
```

```java
// Durable publish
jetStreamPublisher.publish("orders.123.created", bytes);

// Durable consume
@NatsListener(stream = "orders", durableName = "billing-svc", ackPolicy = AckPolicy.EXPLICIT)
```

### 4) `Key`-`Value` & `Object` `Store`

```java
KeyValue kv = natsClient.keyValue("config");
kv.put("feature.flag", "true".getBytes());
String flag = new String(kv.get("feature.flag"));

ObjectStore os = natsClient.objectStore("blobs");
os.put("uploads/abc.pdf", bytes);
os.get("uploads/abc.pdf");
```

### 5) `Idempotency`

```java
// Server side: dedup window
jetStreamPublisher.publish(
    "orders.123.created",
    bytes,
    PublishOptions.builder()
        .messageId("order-123-event-456")  // dedup key
        .build()
);
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `servers` | List<String> | – | NATS server URLs |
| `connection.name` | String | – | Client name (visible in `nats conns`) |
| `connection.reconnect-wait-seconds` | int | `2` | Initial reconnect wait |
| `connection.reconnect-wait-max-seconds` | int | `30` | Max reconnect wait |
| `connection.ping-interval-seconds` | int | `60` | PING interval |
| `jetstream.enabled` | boolean | `true` | Enable JetStream |
| `jetstream.domain` | String | – | JS domain (prefix) |

## 🎯 Best Practices

1. **Always use subject hierarchies** — `tenant.{id}.service.{name}.event.{type}`.
2. **Prefer JetStream for anything that matters** — core NATS is for ephemeral pub/sub only.
3. **Set dedup keys for exactly-once** — use business event IDs.
4. **Tune ping interval to broker SLA** — `60s` default; shorter for tighter failover.
5. **Use queue groups for load balancing** — `@NatsListener(subject = "x", queue = "y")`.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No built-in schema validation** | Wrong message types fail at consumer | Protobuf / Avro + CI |
| **Limited cross-region replication** | Geo-replicated JetStream is complex | Use leaf nodes + sync replicas |
| **JetStream dedup window is per-stream** | Configurable but finite | Set `max-age` ≥ dedup window |

## ❓ FAQ

### `Q1`: `NATS` vs `Kafka` — when to choose?

NATS: low-latency pub/sub, RPC, IoT. Kafka: log-style event streaming, high throughput, complex event sourcing.

### `Q2` — `Can` `I` use both `NATS` core and `JetStream` in the same app?

Yes — they share the same connection. Configure both as needed.

### `Q3` — `How` do `I` trace `NATS` calls across services?

The component auto-propagates `traceparent` headers. Wire with [`atlas-richie-component-tracing`](../atlas-richie-component-tracing/README.md).

### `Q4` — `What` happens if the broker is down?

Auto-reconnect with backoff. Pending publishes are buffered; JetStream publishes are persisted on reconnect.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Tracing** — [`../atlas-richie-component-tracing/README.md`](../atlas-richie-component-tracing/README.md)
- **Microservice** — [`../atlas-richie-component-microservice/README.md`](../atlas-richie-component-microservice/README.md)
- External: [NATS docs](https://docs.nats.io/) · [JetStream](https://docs.nats.io/nats-concepts/jetstream)

---

**atlas-richie-component-nats** 🚀
