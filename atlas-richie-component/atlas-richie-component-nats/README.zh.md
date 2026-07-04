# Atlas Richie NATS组件 (atlas-richie-component-nats)

> 生产级 **NATS** 客户端组件。连接管理、消息总线、RPC 端点、分布式追踪、上下文透传、幂等去重、**JetStream** 持久化流、KV / Object Store。

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
  - [3. 发布消息](#3-发布消息)
  - [4. 订阅](#4-订阅)
- [🔧 核心能力](#🔧-核心能力)
  - [1. 核心 pub/sub](#1-核心-pub/sub)
  - [2. 请求 / 响应（RPC）](#2-请求-/-响应（rpc）)
  - [3. JetStream 持久化流](#3-jetstream-持久化流)
  - [4. Key-Value & Object Store](#4-key-value-&-object-store)
  - [5. 幂等性](#5-幂等性)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：NATS vs Kafka 何时选择？](#q1：nats-vs-kafka-何时选择？)
  - [Q2：能否在同一应用同时使用 NATS 核心和 JetStream？](#q2：能否在同一应用同时使用-nats-核心和-jetstream？)
  - [Q3：如何在服务间追踪 NATS 调用？](#q3：如何在服务间追踪-nats-调用？)
  - [Q4：broker 挂了会怎样？](#q4：broker-挂了会怎样？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-nats` |
| **类别** | 消息——NATS pub/sub + JetStream |
| **强依赖** | `io.nats:jnats`（JetStream 客户端） |
| **兼容** | NATS Server 2.10+，启用 JetStream |

### 本模块的"是"与"不是"

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| 连接管理 + 自动重连 | NATS Server（需自建 nats-server） |
| 请求 / 响应 RPC 模式 | 集群编排（用 nats-box / helm） |
| JetStream 持久化流 + KV / Object Store | 长时 saga（用 Temporal） |
| 幂等性（JetStream 消息去重） | MQTT 5.0（用 [`atlas-richie-component-mqtt`](./atlas-richie-component-mqtt/README.zh.md)） |
| Tracing 集成（W3C traceparent） | Schema Registry（需手写 Protobuf） |

## ✨ 功能特性

### 核心能力

- ✅ **连接管理**——自动重连、退避、关停时 drain。
- ✅ **核心 NATS pub/sub**——fire-and-forget、queue groups、subject wildcards。
- ✅ **请求 / 响应**——内置 RPC，支持超时、header、inbox。
- ✅ **JetStream**——持久化流、consumer、ack 策略、replay、dedup。
- ✅ **Key-Value Store**——分布式配置 / 状态。
- ✅ **Object Store**——基于 JetStream 的大对象存储。
- ✅ **幂等性**——消息级去重窗口。
- ✅ **上下文透传**——`traceparent`、`tenant_id`、`user_id` 通过 header 透传。

### 设计选择

- ✅ **JetStream 优先**——默认持久化；核心 NATS 仅用于临时消息。
- ✅ **主题分层**——`tenant.{tenantId}.service.{svc}.event.{type}`。
- ✅ **退避 + jitter**——指数退避上限 30s。

## 🏗️ 架构与模块布局

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
│   ├── NatsPublisher                  ← 门面
│   ├── JetStreamPublisher
│   └── MessageHeadersAdapter
├── subscribe/
│   ├── NatsSubscriber                 ← @NatsListener 注解
│   ├── JetStreamConsumer
│   └── DispatcherPerConsumer
├── request/
│   ├── NatsRpcClient                  ← 请求/响应
│   └── RpcTimeoutManager
├── store/
│   ├── KeyValueStore
│   └── ObjectStore
└── tracing/
    ├── NatsTracingFilter
    └── TraceparentPropagator
```

## 🚀 快速开始

### 1) 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-nats</artifactId>
</dependency>
```

### 2) 配置

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

### 3) 发布消息

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

### 4) 订阅

```java
@Component
public class OrderEventListener {
    @NatsListener(subject = "orders.*.created", queue = "billing-service")
    public void onOrderCreated(NatsMessage msg) {
        OrderCreatedEvent event = msg.bodyAs(OrderCreatedEvent.class);
        // 处理
        msg.ack();
    }
}
```

## 🔧 核心能力

### 1) 核心 pub/sub

```java
// 发布（fire-and-forget）
publisher.publish("subject", bytes);

// 订阅
@NatsListener(subject = "orders.>")     // > 匹配多层
```

### 2) 请求 / 响应（`RPC`）

```java
// 服务端
@NatsRpcHandler(subject = "users.get")
public User getUser(GetUserRequest req) { return userService.findById(req.getId()); }

// 客户端
User user = rpcClient.request("users.get", request, User.class, Duration.ofSeconds(2));
```

### 3) `JetStream` 持久化流

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
// 持久化发布
jetStreamPublisher.publish("orders.123.created", bytes);

// 持久化消费
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

### 5) 幂等性

```java
// 服务端：去重窗口
jetStreamPublisher.publish(
    "orders.123.created",
    bytes,
    PublishOptions.builder()
        .messageId("order-123-event-456")  // 去重 key
        .build()
);
```

## ⚙️ 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `servers` | List<String> | – | NATS 服务端 URL |
| `connection.name` | String | – | 客户端名（`nats conns` 中可见） |
| `connection.reconnect-wait-seconds` | int | `2` | 初始重连等待 |
| `connection.reconnect-wait-max-seconds` | int | `30` | 最大重连等待 |
| `connection.ping-interval-seconds` | int | `60` | PING 间隔 |
| `jetstream.enabled` | boolean | `true` | 启用 JetStream |
| `jetstream.domain` | String | – | JS domain（前缀） |

## 🎯 最佳实践

1. **始终使用主题分层**——`tenant.{id}.service.{name}.event.{type}`。
2. **重要消息优先 JetStream**——核心 NATS 仅用于临时 pub/sub。
3. **设置去重 key 实现 exactly-once**——使用业务事件 ID。
4. **根据 broker SLA 调 PING 间隔**——默认 60s；更短实现更快 failover。
5. **使用 queue groups 负载均衡**——`@NatsListener(subject = "x", queue = "y")`。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **无内置 schema 校验** | 错误消息类型在 consumer 失败 | Protobuf / Avro + CI |
| **跨区域复制有限** | 跨区域 JetStream 复杂 | 用 leaf node + sync replica |
| **JetStream dedup 窗口是 per-stream** | 可配置但有限 | 设置 `max-age` ≥ dedup 窗口 |

## ❓ 常见问题

### `Q1`：`NATS` vs `Kafka` 何时选择？

NATS：低延迟 pub/sub、RPC、IoT。Kafka：日志式事件流、高吞吐、复杂事件溯源。

### `Q2`：能否在同一应用同时使用 `NATS` 核心和 `JetStream`？

可以——它们共享同一连接。按需配置两者。

### `Q3`：如何在服务间追踪 `NATS` 调用？

本组件自动透传 `traceparent` header。与 [`atlas-richie-component-tracing`](./atlas-richie-component-tracing/README.zh.md) 配合。

### `Q4`：broker 挂了会怎样？

自动重连带退避。挂起的发布被缓冲；JetStream 发布在重连时持久化。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **追踪** — [`./atlas-richie-component-tracing/README.zh.md`](./atlas-richie-component-tracing/README.zh.md)
- **微服务** — [`./atlas-richie-component-microservice/README.zh.md`](./atlas-richie-component-microservice/README.zh.md)
- 外部：[NATS 文档](https://docs.nats.io/) · [JetStream](https://docs.nats.io/nats-concepts/jetstream)

---

**atlas-richie-component-nats** 🚀
