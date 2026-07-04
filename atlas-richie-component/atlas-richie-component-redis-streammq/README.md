# Atlas Richie Redis StreamMQ Component (atlas-richie-component-redis-streammq)

> Reliable **Redis Stream** message queue. Built on [Spring Data Redis Stream](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis-streams) — provides consumer groups, automatic retry, dead-letter, idempotency, observability (OTLP metrics / traces), and Spring Boot autoconfig. A drop-in replacement for the older [`atlas-richie-component-cache`](../atlas-richie-component-cache/README.md) Stream MQ code.

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
  - [3. Publish](#3-publish)
  - [4. Consume](#4-consume)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Consumer groups](#1-consumer-groups)
  - [2. Retry + DLQ](#2-retry-+-dlq)
  - [3. Idempotency](#3-idempotency)
  - [4. Observability](#4-observability)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: When should I use this vs Kafka?](#q1-when-should-i-use-this-vs-kafka?)
  - [Q2: Can I share a stream with multiple consumers?](#q2-can-i-share-a-stream-with-multiple-consumers?)
  - [Q3: How do I replay messages from the beginning?](#q3-how-do-i-replay-messages-from-the-beginning?)
  - [Q4: What if Redis is down?](#q4-what-if-redis-is-down?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-redis-streammq` |
| **Category** | Messaging — reliable message queue on Redis Stream |
| **Hard dependencies** | `spring-boot-starter-data-redis`, Redis 5.0+ (with Streams) |
| **Compatible with** | Redis 6.0+ recommended; Redis Cluster 6.0+ |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| Reliable pub/sub on Redis Stream | A high-throughput event log (use Kafka for > 100K msg/s) |
| Consumer groups + ack + retry | Exactly-once delivery (use idempotency keys) |
| Dead-letter queue | Schema registry (define JSON / Protobuf manually) |
| OTLP metrics + traces out of the box | Long-running saga (use Temporal / Cadence) |

## ✨ Features

### `Core` capabilities

- ✅ **Consumer groups** — parallel consumption with scale-out.
- ✅ **Automatic retry** — exponential backoff with max attempts.
- ✅ **Dead-letter queue** — failed messages go to `<stream>.dlq`.
- ✅ **Idempotency** — message-level dedup via Redis SET.
- ✅ **Spring Boot autoconfig** — drop-in; one `StreamMQ` bean.
- ✅ **OTLP metrics** — `stream.messages`, `stream.duration`, `stream.errors`.
- ✅ **OTLP traces** — span per message with producer / consumer kind.

### `Design` choices

- ✅ **Spring Data Redis Stream** under the hood — no custom protocol.
- ✅ **OTLP-native observability** — Micrometer + OpenTelemetry, platform-aligned.
- ✅ **Configurable error strategies** — `SKIP` / `RETRY` / `DEAD_LETTER` / `FAIL_FAST`.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-redis-streammq
├── config/
│   ├── StreamMqAutoConfiguration
│   ├── StreamMqProperties
│   ├── RedisStreamTracingAutoConfiguration
│   ├── RedisStreamTracingProperties
│   ├── OtlpMeterRegistryAutoConfiguration
│   └── MonitorAutoConfiguration
├── stream/
│   ├── StreamMq                       ← facade
│   ├── StreamPublisher
│   ├── StreamConsumer
│   └── StreamMessageConverter
├── consumer/
│   ├── ConsumerGroupRegistry
│   ├── RetryExecutor
│   └── DeadLetterPublisher
├── observability/
│   ├── StreamMetricsBinder           ← Micrometer
│   ├── StreamTracingFilter           ← OpenTelemetry
│   └── OtlpExporter
├── idempotency/
│   ├── IdempotencyStore              ← Redis SET
│   └── DeduplicationKey
└── error/
    └── ErrorStrategy                 ← SKIP | RETRY | DEAD_LETTER | FAIL_FAST
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-redis-streammq</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

platform:
  component:
    redis-streammq:
      streams:
        - name: order-events
          consumer-group: order-service
          consumer-name: order-service-1
          target-type: com.richie.app.event.OrderEvent
          count: 50                          # batch size per poll
          concurrency: 8                      # parallel consumer threads
          auto-ack: false
          error-strategy: DEAD_LETTER
          max-retries: 3
          idempotency:
            enabled: true
            ttl-seconds: 86400                # 24h
          auto-start: true
          max-len: 100000                     # cap stream length
```

### 3) `Publish`

```java
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {
    private final StreamMQ streamMQ;

    public void publish(OrderEvent event) {
        streamMQ.stream("order-events")
                .publish(JsonUtils.toBytes(event));
    }
}
```

### 4) `Consume`

```java
@Component
public class OrderEventListener {

    @StreamConsumer(stream = "order-events")
    public void onMessage(OrderEvent event, StreamContext ctx) {
        log.info("Received: {}", event);
        // process
        // auto-ack if success, retry / DLQ on exception
    }
}
```

## 🔧 Core Capabilities

### 1) `Consumer` groups

```yaml
platform:
  component:
    redis-streammq:
      streams:
        - name: order-events
          consumer-group: order-service    # all instances share this group
          consumer-name: order-service-${random.uuid}   # unique per instance
          concurrency: 16                  # parallel pollers per instance
```

### 2) `Retry` + `DLQ`

```yaml
error-strategy: DEAD_LETTER
max-retries: 3
```

After 3 failed attempts, message → `order-events.dlq` stream. Consume DLQ separately for ops review.

### 3) `Idempotency`

```yaml
idempotency:
  enabled: true
  ttl-seconds: 86400                    # 24h dedup window
```

Duplicate messages within window are dropped silently. Dedup key is `stream-name + message-id`.

### 4) `Observability`

OTLP metrics auto-exported:

```
stream.messages.total{stream,group,status}  counter
stream.messages.duration{stream,group}      histogram
stream.messages.errors{stream,group,error}  counter
stream.consumer.lag{stream,group}          gauge
```

OTLP traces:

```
span: "order-events.consume"
  kind: CONSUMER
  attributes: { stream, group, attempt, error? }
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | String | – | Stream name |
| `consumer-group` | String | – | Consumer group (load balancing) |
| `consumer-name` | String | auto | Unique per instance |
| `target-type` | Class | – | Event class to deserialize to |
| `count` | int | `10` | Batch size per poll |
| `concurrency` | int | `1` | Parallel consumer threads |
| `auto-ack` | boolean | `true` | Auto-ack on success |
| `error-strategy` | enum | `RETRY` | `SKIP` / `RETRY` / `DEAD_LETTER` / `FAIL_FAST` |
| `max-retries` | int | `3` | Max attempts before DLQ |
| `idempotency.enabled` | boolean | `true` | Enable dedup |
| `idempotency.ttl-seconds` | long | `86400` | Dedup window |
| `auto-start` | boolean | `true` | Auto-start consumer on app start |
| `max-len` | long | `unlimited` | Cap stream length (XADD MAXLEN) |

## 🎯 Best Practices

1. **Always set `error-strategy: DEAD_LETTER`** — never lose messages silently.
2. **Use business event IDs as idempotency keys** — `orderId-eventType-sequence` is better than auto-generated.
3. **Set `max-len` on every stream** — Redis Streams grow forever without cap.
4. **Tune `concurrency` to CPU count** — over-provisioning wastes threads.
5. **Monitor `stream.consumer.lag`** — alert when lag > threshold.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Redis single-threaded command loop** | Throughput ceiling ~50K msg/s per node | Shard by stream name; use Cluster |
| **No exactly-once delivery** | Duplicates possible | Idempotency keys + dedup |
| **Cluster mode adds complexity** | Streams are per-node; cross-node needs special handling | Use one stream per node, or XADD with `NODE_ID` in message |

## ❓ FAQ

### `Q1` — `When` should `I` use this vs `Kafka`?

- **Redis StreamMQ** — simpler infra (just Redis), < 50K msg/s, single-team setups.
- **Kafka** — multi-DC, very high throughput, long retention, event sourcing.

### `Q2` — `Can` `I` share a stream with multiple consumers?

Yes — each gets its own consumer group. E.g., `order-events` consumed by `billing-service`, `analytics-service`, `notification-service` — each in its own group.

### `Q3` — `How` do `I` replay messages from the beginning?

```java
streamMQ.stream("order-events")
        .replay(Instant.now().minus(Duration.ofHours(1)));
```

### `Q4` — `What` if `Redis` is down?

Consumers pause; auto-reconnect retries with backoff. Published messages are buffered; if buffer overflows, fail fast.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Cache (Redis)** — [`../atlas-richie-component-cache/README.md`](../atlas-richie-component-cache/README.md)
- **Tracing** — [`../atlas-richie-component-tracing/README.md`](../atlas-richie-component-tracing/README.md)
- External: [Redis Streams](https://redis.io/docs/data-types/streams/) · [Spring Data Redis Stream](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis-streams)

---

**atlas-richie-component-redis-streammq** 🚀
