# Atlas Richie Tracing组件 (atlas-richie-component-tracing)

> **分布式追踪**组件。封装 OpenTelemetry SDK，提供 trace / span 自动采集、Spring Boot 自动装配、跨服务上下文透传、OTLP 导出器、采样率可配。与 [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/) 深度集成。

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
  - [3. 加注解](#3-加注解)
- [🔧 核心能力](#🔧-核心能力)
  - [1. 自动采集](#1-自动采集)
  - [2. 手动 span](#2-手动-span)
  - [3. 跨服务透传](#3-跨服务透传)
  - [4. 采样策略](#4-采样策略)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：如何选择 sampler？](#q1：如何选择-sampler？)
  - [Q2：如何把 trace ID 写到日志？](#q2：如何把-trace-id-写到日志？)
  - [Q3：自定义 span attributes 的最佳实践？](#q3：自定义-span-attributes-的最佳实践？)
  - [Q4：能否用 Zipkin 而非 OTLP？](#q4：能否用-zipkin-而非-otlp？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-tracing` |
| **类别** | 可观测性——分布式追踪 |
| **强依赖** | OpenTelemetry SDK、Brave、Zipkin Reporter（可选） |
| **兼容** | OpenTelemetry 1.40+、Spring Boot 4.x |

### 本模块的"是"与"不是"

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| OpenTelemetry SDK 自动装配 | Metrics（用 Micrometer + [`atlas-richie-component-logging`](./atlas-richie-component-logging/README.zh.md)） |
| HTTP / RPC / DB / Messaging 自动 instrumentation | Logging（用 `atlas-richie-component-logging`） |
| 跨服务 W3C `traceparent` 透传 | Profiling / Continuous Profiling |
| 多种 exporter（OTLP / Zipkin / Jaeger） | 服务网格 sidecar（用 Istio / Linkerd 代替） |

## ✨ 功能特性

### 核心能力

- ✅ **OpenTelemetry SDK 自动装配**——`Tracer` / `TracerProvider` Bean 即可用。
- ✅ **HTTP 客户端 / 服务端**——OkHttp、HttpClient5、JDK HttpClient、Spring `RestClient`、OpenFeign 自动 instrumentation。
- ✅ **数据库**——JDBC、`MongoTemplate`、`JpaTemplate` 自动埋点。
- ✅ **消息**——Kafka、RabbitMQ、RocketMQ、NATS、Redis Stream。
- ✅ **跨进程 / 跨线程**——W3C `traceparent` 自动注入 / 提取。
- ✅ **OTLP / Zipkin / Jaeger exporter**——开箱即用。
- ✅ **采样策略**——always_on / always_off / parent_based / ratio_based。

### 设计选择

- ✅ **OpenTelemetry 标准**——不绑定 vendor 特定 SDK。
- ✅ **OTLP 默认 exporter**——OpenTelemetry 官方协议。
- ✅ **Spring Boot 原生**——`OpenTelemetryAutoConfiguration` 复用。

## 🏗️ 架构与模块布局

```
atlas-richie-component-tracing
├── config/
│   ├── TracingAutoConfiguration
│   ├── TracingProperties
│   └── OtlpExporterConfiguration
├── tracer/
│   ├── TracerProvider                 ← OTel SDK
│   ├── Tracer                         ← facade
│   └── SpanBuilder
├── instrumentation/
│   ├── HttpClientInstrumentation       ← OkHttp / Apache HC / JDK / RestClient / Feign
│   ├── DatabaseInstrumentation         ← JDBC / Mongo / JPA
│   └── MessagingInstrumentation       ← Kafka / RabbitMQ / RocketMQ / NATS / Redis Stream
├── propagation/
│   ├── W3CTraceContextPropagator
│   └── HeaderContextHolderPropagator  ← bridge to platform HeaderContextHolder
├── sampler/
│   ├── ParentBasedSampler
│   ├── RatioBasedSampler
│   └── CustomSampler
└── exporter/
    ├── OtlpHttpExporter
    ├── OtlpGrpcExporter
    └── ZipkinExporter                  ← legacy
```

## 🚀 快速开始

### 1) 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-tracing</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

### 2) 配置

```yaml
platform:
  component:
    tracing:
      enabled: true
      service-name: ${spring.application.name}
      sampler: parent_based_ratio
      sampling-ratio: 0.1
      exporter: otlp
      otlp:
        endpoint: http://otel-collector:4318
        protocol: http/protobuf
```

### 3) 加注解

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final Tracer tracer;

    public Order placeOrder(OrderRequest req) {
        // 自动埋点
        Span span = tracer.spanBuilder("OrderService.placeOrder")
                .setAttribute("order.id", req.getId())
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // ... business logic ...
            return order;
        } finally {
            span.end();
        }
    }
}
```

## 🔧 核心能力

### 1) 自动采集

```java
// HTTP — 自动
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable String id) { ... }
// Span: GET /orders/{id}, status, duration — 自动

// JDBC — 自动
@Autowired JdbcTemplate jdbc;
jdbc.queryForObject("SELECT * FROM users WHERE id = ?", User.class, id);
// Span: SELECT users — 自动

// Messaging — 自动
@KafkaListener(topics = "orders")
public void onOrder(OrderEvent e) { ... }
// Span: orders.consume — 自动
```

### 2) 手动 span

```java
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final Tracer tracer;

    public Payment charge(PaymentRequest req) {
        Span span = tracer.spanBuilder("PaymentService.charge")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("payment.amount", req.getAmount())
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return paymentGateway.charge(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 3) 跨服务透传

W3C `traceparent` header 自动注入到 outbound HTTP / messaging：

```
// 自动 — 不需要任何代码
http.get("https://api/users/123")
    .header("X-Custom", "value")   // headers 合并
    .execute();
// 请求中自动包含: traceparent: 00-aaaa-bbbb-01
```

### 4) 采样策略

```yaml
# 总是采样（开发）
sampler: always_on

# 从不采样
sampler: always_off

# 基于父级，未采样则不采样
sampler: parent_based_always_on

# 基于父级 + 比例（生产）
sampler: parent_based_ratio
sampling-ratio: 0.1   # 10%
```

## ⚙️ 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | `true` | 总开关 |
| `service-name` | String | `${spring.application.name}` | OTel `service.name` resource |
| `sampler` | enum | `parent_based_always_on` | 采样器 |
| `sampling-ratio` | double | `1.0` | 比例（仅 ratio-based） |
| `exporter` | enum | `otlp` | `otlp` / `zipkin` / `none` |
| `otlp.endpoint` | String | – | OTLP collector URL |
| `otlp.protocol` | enum | `http/protobuf` | `http/protobuf` / `grpc` |
| `resource-attributes` | Map<String,String> | – | OTel resource attrs |

## 🎯 最佳实践

1. **生产用 `parent_based_ratio: 0.1`**——10% 采样足够统计。
2. **为所有跨服务调用点加 span**——入口处 create span，出口处 propagate。
3. **使用语义属性**——`http.method`、`db.statement`、`messaging.destination` 等。
4. **关联日志**——通过 `traceId` / `spanId` 关联到 [`atlas-richie-component-logging`](./atlas-richie-component-logging/README.zh.md)。
5. **永远不要在 span name 里放 PII**——用户 ID 可以，邮箱不行。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **不支持 Profiling** | 只能看 trace，不能看火焰图 | 用 Continuous Profiling 工具 |
| **OTLP gRPC exporter 需要更多依赖** | 启动稍慢 | 默认用 `http/protobuf` |
| **Mongo 旧版 driver 需手动 instrumentation** | 部分查询无 span | 升级到最新 driver |

## ❓ 常见问题

### `Q1`：如何选择 sampler？

- dev：`always_on`
- prod：`parent_based_ratio: 0.1`——10% 足够统计，99% trace 不入库。

### `Q2`：如何把 trace `ID` 写到日志？

与 [`atlas-richie-component-logging`](./atlas-richie-component-logging/README.zh.md) 配合：日志 JSON 字段自动包含 `trace_id` 和 `span_id`。

### `Q3`：自定义 span attributes 的最佳实践？

- 命名用 snake_case，遵循 OTel 语义约定。
- 不要超过 10 个 attribute。
- 大字段（>1KB）应存日志，不存 span。

### `Q4`：能否用 `Zipkin` 而非 `OTLP`？

可以——设置 `exporter: zipkin` + 添加 `zipkin-reporter` 依赖。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **Logging** — [`./atlas-richie-component-logging/README.zh.md`](./atlas-richie-component-logging/README.zh.md)
- **gRPC** — [`../atlas-richie-component-grpc/README.zh.md`](../atlas-richie-component-grpc/README.zh.md)
- **HTTP** — [`../atlas-richie-component-http/README.zh.md`](../atlas-richie-component-http/README.zh.md)
- 外部：[OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/) · [W3C Trace Context](https://www.w3.org/TR/trace-context/)

---

**atlas-richie-component-tracing** 🚀
