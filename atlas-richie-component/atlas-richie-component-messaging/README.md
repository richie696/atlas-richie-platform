# Atlas Richie Messaging Component (atlas-richie-component-messaging)

> Parent module for **unified message queue** access. Wraps Spring Cloud Stream Function and provides one `MessageService` facade across **12 providers**: Kafka, RabbitMQ, RocketMQ, Kinesis, Pub/Sub, Event Hubs, Service Bus, SQS, SNS, Pulsar, Solace, NATS.

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
  - [3. Send a message](#3-send-a-message)
  - [4. Consume a message](#4-consume-a-message)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Send: ordinary, delayed, scheduled](#1-send-ordinary,-delayed,-scheduled)
  - [2. Consume via Function](#2-consume-via-function)
  - [3. Idempotency / retry](#3-idempotency-/-retry)
  - [4. Multi-binder](#4-multi-binder)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Which provider should I choose?](#q1-which-provider-should-i-choose?)
  - [Q2: How do I consume with custom header propagation?](#q2-how-do-i-consume-with-custom-header-propagation?)
  - [Q3: Can I have multiple consumers for the same topic?](#q3-can-i-have-multiple-consumers-for-the-same-topic?)
  - [Q4: Where are dead-lettered messages?](#q4-where-are-dead-lettered-messages?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-messaging` (parent POM) |
| **Category** | Messaging — async event / message bus |
| **Hard dependencies** | Spring Cloud Stream, `atlas-richie-context` |
| **Default provider** | `kafka` |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| One `MessageService` facade across 12 providers | Exactly-once delivery (depends on broker) |
| Spring Cloud Stream Function integration | Kafka Connect / RabbitMQ Streams (raw) |
| Idempotency, retry, delay, scheduled | Schema registry (use Confluent / Apicurio separately) |
| Multi-binder routing (send to multiple brokers) | Complex event sourcing (use Atlas) |

## ✨ Features

### `Core` capabilities

- ✅ **12 providers** — Kafka, RabbitMQ, RocketMQ, Kinesis, Pub/Sub, Event Hubs, Service Bus, SQS, SNS, Pulsar, Solace, NATS.
- ✅ **One API** — `MessageService.send`, `sendDelay`, `sendScheduled`.
- ✅ **Function-based consumer** — `Supplier`, `Function`, `Consumer` auto-registered.
- ✅ **Idempotency** — memory or Redis deduplication.
- ✅ **Retry with backoff** — built-in via Spring Cloud Stream.
- ✅ **Multi-binder** — send to multiple brokers in one call.

### `Design` choices

- ✅ **Spring Cloud Stream under the hood** — leverages Spring's binder abstraction.
- ✅ **Static + dynamic topic aliases** — both via `TopicAlias` enum and string.
- ✅ **`MessageEvent` envelope** — wraps payload + headers + retry metadata.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-messaging                    ← parent POM
├── atlas-richie-component-messaging-core           ← MessageService / MessageEvent / BaseConsumer
├── atlas-richie-component-messaging-kafka
├── atlas-richie-component-messaging-rabbitmq
├── atlas-richie-component-messaging-rocketmq
├── atlas-richie-component-messaging-kinesis
├── atlas-richie-component-messaging-gcp-pubsub
├── atlas-richie-component-messaging-eventhubs
├── atlas-richie-component-messaging-servicebus
├── atlas-richie-component-messaging-sqs
├── atlas-richie-component-messaging-sns
├── atlas-richie-component-messaging-pulsar
└── atlas-richie-component-messaging-solace
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-messaging-core</artifactId>
</dependency>
<!-- Pick exactly one provider -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-messaging-kafka</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: localhost:9092
      bindings:
        normalProcess-in-0:
          destination: normal-topic
          group: normal-group
        delayProcess-in-0:
          destination: delay-topic
          group: delay-group

platform:
  component:
    messaging:
      datasource: redis              # memory | redis (idempotency)
      max-retries: 3
```

### 3) `Send` a message

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final MessageService messageService;

    public void publish(OrderCreatedEvent event) {
        messageService.sendMessage("order-created", event);
    }
}
```

### 4) `Consume` a message

```java
@Component
public class OrderConsumer {
    private final BaseConsumer baseConsumer;

    @PostConstruct
    public void register() {
        baseConsumer.registerConsumer("order-created", this::handle);
    }

    private Boolean handle(MessageEvent event) {
        OrderCreatedEvent order = event.getBody(OrderCreatedEvent.class);
        return process(order);  // true = success, false = retry
    }
}
```

## 🔧 Core Capabilities

### 1) `Send` — ordinary, delayed, scheduled

```java
messageService.sendMessage("order-created", orderEvent);                  // now
messageService.sendDelayMessage("notification", event, 60_000L);            // +60s
messageService.sendScheduledMessage("reminder", event, Instant.now().plus(Duration.ofDays(1)));
```

### 2) `Consume` via `Function`

```java
@Bean
public Consumer<OrderCreatedEvent> orderCreated() {
    return event -> { /* process */ };
}
```

### 3) `Idempotency` / retry

Idempotency is checked via message ID. Duplicates are skipped.

```yaml
platform:
  component:
    messaging:
      datasource: redis   # memory | redis
      max-retries: 5      # then drop / DLQ
```

### 4) `Multi`-binder

```java
messageService.sendMessage("audit-event", "kafka-binder", event);
messageService.sendMessage("audit-event", "rabbitmq-binder", event);
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `datasource` | enum | `memory` | `memory` / `redis` (idempotency backend) |
| `max-retries` | int | `3` | Max retry attempts before drop / DLQ |
| `delay.message-store` | String | redis | Delay message backend |
| `bindings.<binding>.destination` | String | – | Topic / queue name |
| `bindings.<binding>.group` | String | – | Consumer group |

## 🎯 Best Practices

1. **Use enums for topic names** — avoid string typos.
2. **Set `max-retries` per message criticality** — 3 for transient, 5+ for critical.
3. **Always return `false` on transient failures** — let the retry kick in.
4. **Use Redis for idempotency** in production — `memory` only for single-instance dev.
5. **Decouple consumer error handling from business code** — use AOP or wrapper.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Exactly-once not guaranteed** | May process duplicate | Use idempotency + `datasource: redis` |
| **Delay message granularity depends on broker** | RabbitMQ: per-queue TTL; Kafka: not native | Use `scheduled` for cross-broker guarantees |
| **Multi-binder routing complexity** | Hard to debug | Keep routing config in one place |

## ❓ FAQ

### `Q1` — `Which` provider should `I` choose?

- **Kafka** — high throughput, log / event streaming.
- **RabbitMQ** — enterprise, complex routing.
- **RocketMQ** — Aliyun ecosystem, transactions.
- **AWS SQS / SNS** — AWS only.
- **Pub/Sub / Event Hubs / Service Bus** — GCP / Azure.

### `Q2` — `How` do `I` consume with custom header propagation?

```java
@Bean
public Consumer<Message<OrderCreatedEvent>> consumer() {
    return msg -> {
        String tenantId = msg.getHeaders().get("x-tenant-id");
        // process
    };
}
```

### `Q3` — `Can` `I` have multiple consumers for the same topic?

Yes — different `group` values create independent consumer groups.

### `Q4` — `Where` are dead-lettered messages?

Broker-dependent. Configure per-binder; this component doesn't own DLQ.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **State machine (uses messaging)** — [`../atlas-richie-component-statemachine/README.md`](../atlas-richie-component-statemachine/README.md)
- External: [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)

---

**atlas-richie-component-messaging** 🚀
