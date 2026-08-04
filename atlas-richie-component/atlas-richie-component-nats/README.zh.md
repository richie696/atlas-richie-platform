# Atlas Richie NATS 组件

提供 Core NATS 发布订阅与 RPC、JetStream 持久化消息、DLQ、上下文透传、幂等、链路追踪，以及 Key-Value / Object Store 访问。

## 快速开始

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

组件通过 `NatsComponent` 管理连接生命周期，并在启动时声明已配置的 JetStream stream 与 consumer。

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

## API

```java
Subscription subscription = nats.bus().subscribe("orders.>", message -> {
    // 处理 Core NATS 消息
});
nats.bus().unsubscribe(subscription);

// 未传 Duration 的 overload 使用 request.default-timeout。
OrderView view = nats.bus().request("orders.get", request, OrderView.class);

// 保存注册句柄；关闭它会注销 RPC endpoint 并释放 dispatcher。
NatsEndpoint.Registration registration = nats.endpoint().registerHandler(
        "orders.get", GetOrderRequest.class, service::get);

// JetStream：成功自动 ack，抛异常自动 nak。
MessageConsumer consumer = nats.stream().consume("ORDERS", "order-processor", message -> {
    // 处理持久化消息
});

KeyValue flags = nats.keyValue("config");
ObjectStore files = nats.objectStore("uploads");
```

`stream().publish(streamName, subject, message)` 会先验证 subject 是否属于传入的 stream，避免参数形同虚设。

## 关键配置

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `server` | `nats://localhost:4222` | 用逗号分隔的 NATS 地址。 |
| `tracing.enabled` | `true` | 开启 OpenTelemetry span 和 W3C context 透传。OTel API 是本组件的必需依赖。 |
| `header-propagation.enabled` | `true` | 是否透传配置的 header 白名单。 |
| `idempotent.enabled` | `false` | 开启消费端幂等去重。 |
| `request.default-timeout` | `5s` | RPC overload 未指定超时时使用。 |
| `jetstream.enabled` | `false` | 开启 JetStream。 |
| `jetstream.streams[].consumers[].ack-wait` | `30s` | 处理超时窗口；应大于预期任务时长。 |
| `jetstream.streams[].consumers[].max-ack-pending` | `1000` | consumer 全部实例的在途任务上限，可作为 Worker 并发上限。 |
| `jetstream.streams[].consumers[].backoff` | 空 | 未确认任务的服务端重投退避序列；配置后覆盖 `ack-wait`。 |
| `jetstream.streams[].consumers[].nak-delay` | `5s` | handler 抛异常后的延迟重投，避免瞬时故障热循环。 |
| `jetstream.dlq.enabled` | `false` | 开启最大投递次数 advisory 的 DLQ 转发。 |
| `tls.enabled` | `false` | 开启 TLS；未配置自定义 truststore 时使用 JVM 默认受信任 CA。 |

DLQ 配置统一位于 `platform.component.nats.jetstream.dlq`，不存在顶层 `platform.component.nats.dlq` 配置。

## 注意事项

- Core NATS 是 fire-and-forget；关键消息使用 JetStream。
- JetStream 是 at-least-once 语义，建议继续使用幂等去重。
- `stream().consume` 会按 consumer 的 `ack-wait` 三分之一间隔发送 `inProgress()`；长时间 LLM 调用仍在运行时不会被误重投。
- 动态创建的 JetStream consumer 与 RPC registration 应由调用方停止/关闭。
