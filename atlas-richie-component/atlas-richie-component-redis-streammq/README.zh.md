# Atlas Richie Redis StreamMQ组件 (atlas-richie-component-redis-streammq)

> 基于 **Redis Stream** 的可靠消息队列。构建于 [Spring Data Redis Stream](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis-streams)——提供 consumer group、自动重试、死信、幂等、可观测性（OTLP 指标 / 追踪）以及 Spring Boot 自动装配。是 [`atlas-richie-component-cache`](../atlas-richie-component-cache/README.zh.md) 中旧 Stream MQ 代码的替代品。

---

## 📖 目录

- [📖 概述](#📖-概述)
  - [本模块的"是"与"不是"](#本模块的是与不是)
- [✨ 功能特性](#✨-功能特性)
  - [核心能力](#核心能力)
  - [设计选择](#设计选择)
- [🏗️ 架构与模块布局](#🏗️-架构与模块布局)
- [🚀 快速开始](#🚀-快速开始)
  - [1. 引入依赖](#1-引入依赖)
  - [2. 配置](#2-配置)
  - [3. 发布](#3-发布)
  - [4. 消费](#4-消费)
- [🔧 核心能力](#🔧-核心能力)
  - [1. Consumer Group](#1-consumer-group)
  - [2. 重试 + 死信](#2-重试-+-死信)
  - [3. 幂等性](#3-幂等性)
  - [4. 可观测性](#4-可观测性)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：何时用本组件 vs Kafka？](#q1：何时用本组件-vs-kafka？)
  - [Q2：能否多 consumer 共享一个流？](#q2：能否多-consumer-共享一个流？)
  - [Q3：如何从头重放消息？](#q3：如何从头重放消息？)
  - [Q4：Redis 挂了会怎样？](#q4：redis-挂了会怎样？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-redis-streammq` |
| **类别** | 消息——基于 Redis Stream 的可靠消息队列 |
| **强依赖** | `spring-boot-starter-data-redis`、Redis 5.0+（启用 Streams） |
| **兼容** | 推荐 Redis 6.0+；Redis Cluster 6.0+ |

### 本模块的"是"与"不是"

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| Redis Stream 上的可靠 pub/sub | 高吞吐事件日志（> 100K msg/s 用 Kafka） |
| Consumer group + ack + 重试 | Exactly-once 投递（用幂等 key） |
| 死信队列 | Schema Registry（手写 JSON / Protobuf） |
| OTLP 指标 + 追踪开箱即用 | 长时 saga（用 Temporal / Cadence） |

## ✨ 功能特性

### 核心能力

- ✅ **Consumer Group**——并行消费 + 水平扩展。
- ✅ **自动重试**——指数退避 + 最大重试次数。
- ✅ **死信队列**——失败消息进入 `<stream>.dlq`。
- ✅ **幂等性**——基于 Redis SET 的消息级去重。
- ✅ **Spring Boot 自动装配**——开箱即用；一个 `StreamMQ` Bean。
- ✅ **OTLP 指标**——`stream.messages`、`stream.duration`、`stream.errors`。
- ✅ **OTLP 追踪**——每条消息的 span，带 producer / consumer kind。

### 设计选择

- ✅ **基于 Spring Data Redis Stream**——无自定义协议。
- ✅ **OTLP 原生可观测性**——Micrometer + OpenTelemetry，与平台一致。
- ✅ **可配置错误策略**——`SKIP` / `RETRY` / `DEAD_LETTER` / `FAIL_FAST`。

## 🏗️ 架构与模块布局

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
│   ├── StreamMq                       ← 门面
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

## 🚀 快速开始

### 1) 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-redis-streammq</artifactId>
</dependency>
```

### 2) 配置

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
          count: 50                          # 每次拉取批量大小
          concurrency: 8                      # 并发 consumer 线程数
          auto-ack: false
          error-strategy: DEAD_LETTER
          max-retries: 3
          idempotency:
            enabled: true
            ttl-seconds: 86400                # 24h
          auto-start: true
          max-len: 100000                     # 流长度上限
```

### 3) 发布

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

### 4) 消费

```java
@Component
public class OrderEventListener {

    @StreamConsumer(stream = "order-events")
    public void onMessage(OrderEvent event, StreamContext ctx) {
        log.info("Received: {}", event);
        // 处理
        // 成功自动 ack；异常时重试 / DLQ
    }
}
```

## 🔧 核心能力

### 1) `Consumer` `Group`

```yaml
platform:
  component:
    redis-streammq:
      streams:
        - name: order-events
          consumer-group: order-service    # 所有实例共享此 group
          consumer-name: order-service-${random.uuid}   # 每实例唯一
          concurrency: 16                  # 每实例并行 poll 数量
```

### 2) 重试 + 死信

```yaml
error-strategy: DEAD_LETTER
max-retries: 3
```

3 次重试后，消息进入 `order-events.dlq` 流。运维人员单独消费 DLQ 进行审查。

### 3) 幂等性

```yaml
idempotency:
  enabled: true
  ttl-seconds: 86400                    # 24h 去重窗口
```

窗口内的重复消息被静默丢弃。去重 key 为 `stream-name + message-id`。

### 4) 可观测性

OTLP 指标自动导出：

```
stream.messages.total{stream,group,status}  counter
stream.messages.duration{stream,group}      histogram
stream.messages.errors{stream,group,error}  counter
stream.consumer.lag{stream,group}          gauge
```

OTLP 追踪：

```
span: "order-events.consume"
  kind: CONSUMER
  attributes: { stream, group, attempt, error? }
```

## ⚙️ 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | – | 流名 |
| `consumer-group` | String | – | Consumer group（负载均衡） |
| `consumer-name` | String | auto | 每实例唯一 |
| `target-type` | Class | – | 事件反序列化目标类 |
| `count` | int | `10` | 每次拉取批量大小 |
| `concurrency` | int | `1` | 并发 consumer 线程数 |
| `auto-ack` | boolean | `true` | 成功时自动 ack |
| `error-strategy` | enum | `RETRY` | `SKIP` / `RETRY` / `DEAD_LETTER` / `FAIL_FAST` |
| `max-retries` | int | `3` | 进入 DLQ 前的最大尝试次数 |
| `idempotency.enabled` | boolean | `true` | 启用去重 |
| `idempotency.ttl-seconds` | long | `86400` | 去重窗口 |
| `auto-start` | boolean | `true` | 应用启动时自动启动 consumer |
| `max-len` | long | `unlimited` | 流长度上限（XADD MAXLEN） |

## 🎯 最佳实践

1. **始终设置 `error-strategy: DEAD_LETTER`**——绝不让消息悄悄丢失。
2. **使用业务事件 ID 作为幂等 key**——`orderId-eventType-sequence` 比自动生成好。
3. **为每个流设置 `max-len`**——没有上限 Redis Stream 会无限增长。
4. **`concurrency` 调成 CPU 核数**——过多浪费线程。
5. **监控 `stream.consumer.lag`**——lag 超阈值告警。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **Redis 单线程命令循环** | 单节点吞吐上限约 50K msg/s | 按流名分片；用 Cluster |
| **无 exactly-once 投递** | 可能重复 | 幂等 key + 去重 |
| **Cluster 模式增加复杂度** | 流是 per-node；跨节点需特殊处理 | 每节点一个流，或在消息中带 `NODE_ID` |

## ❓ 常见问题

### `Q1`：何时用本组件 vs `Kafka`？

- **Redis StreamMQ**——基础设施简单（仅 Redis），< 50K msg/s，单团队。
- **Kafka**——多数据中心、超高吞吐、长期保留、事件溯源。

### `Q2`：能否多 consumer 共享一个流？

可以——每个 consumer 有自己的 group。例如 `order-events` 被 `billing-service`、`analytics-service`、`notification-service` 各自消费，每个用独立 group。

### `Q3`：如何从头重放消息？

```java
streamMQ.stream("order-events")
        .replay(Instant.now().minus(Duration.ofHours(1)));
```

### `Q4`：`Redis` 挂了会怎样？

Consumer 暂停；自动重连退避。发布被缓冲；缓冲溢出则快速失败。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **Cache（Redis）** — [`../atlas-richie-component-cache/README.zh.md`](../atlas-richie-component-cache/README.zh.md)
- **追踪** — [`./atlas-richie-component-tracing/README.zh.md`](./atlas-richie-component-tracing/README.zh.md)
- 外部：[Redis Streams](https://redis.io/docs/data-types/streams/) · [Spring Data Redis Stream](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis-streams)

---

**atlas-richie-component-redis-streammq** 🚀
