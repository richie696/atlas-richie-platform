# Atlas Richie Tracing 组件 (atlas-richie-component-tracing)

> **依赖托管模块**——统一管理 OpenTelemetry SDK、Spring Boot Starter 及导出器版本，提供开箱即用的分布式追踪依赖集。

本模块**不含自定义 Java 代码**，是一个依赖聚合包（dependency aggregator）。它统一引入了 OpenTelemetry 生态的核心依赖并锁定版本，业务方只需引入这一个模块，即可获得完整的 OTel SDK + Spring Boot 自动装配 + 多种导出器支持。

---

## 📖 目录

- [📖 概述](#📖-概述)
  - [设计目的](#设计目的)
  - [本模块的"是"与"不是"](#本模块的是与不是)
- [📦 托管依赖总览](#📦-托管依赖总览)
  - [核心依赖](#核心依赖)
  - [导出器依赖](#导出器依赖)
  - [Spring Boot 集成](#spring-boot-集成)
  - [可选依赖](#可选依赖)
- [🔧 使用场景选择](#🔧-使用场景选择)
  - [场景 A：Java Agent——零代码全量埋点](#场景-ajava-agent零代码全量埋点)
  - [场景 B：Spring Boot Starter——纯代码集成（推荐）](#场景-bspring-boot-starter纯代码集成推荐)
  - [场景 C：手动 API——自定义精细控制](#场景-c手动-apiannotations自定义精细控制)
  - [场景 D：仅需 Trace ID 透传——用 `web-core`](#场景-d仅需-trace-id-透传用-web-core)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
- [📚 相关文档](#📚-相关文档)

---

## 📖 概述

| 项        | 值                                                     |
|----------|-------------------------------------------------------|
| **坐标**   | `com.richie.component:atlas-richie-component-tracing` |
| **类别**   | 依赖托管——分布式追踪                                           |
| **适用范围** | Spring Boot 3.x / 4.x                                 |
| **托管版本** | OpenTelemetry SDK 1.40+ / Instrumentation BOM 2.x     |

### 设计目的

**为什么要做这个模块？** 在微服务架构中引入 OpenTelemetry 涉及 8-12 个依赖（API、SDK、Spring Boot 集成、exporter、注解……），版本对齐极易出错——`opentelemetry-api` v1.40 与 `opentelemetry-sdk-trace` v1.38 混用会引入不兼容的 API 变更。

本模块作为一个**受控依赖集**，让业务方只加一个依赖就能获得：

- ✅ **版本锁定**——所有 OTel 依赖版本由 `atlas-richie-component-dependencies` BOM 统一管控，不存在版本碎片
- ✅ **协议封闭**——引入标准 OTLP exporter + Zipkin exporter（legacy），业务方不需要纠结引入哪个 exporter 版本
- ✅ **注解就绪**——`@WithSpan` / `@SpanAttribute` 直接可用，不用额外加 `opentelemetry-instrumentation-annotations`
- ✅ **自动装配就绪**——`opentelemetry-spring-boot-starter` 在 classpath 上即生效，`TracerProvider` / `OtlpHttpSpanExporter` 自动注册
- ✅ **可选 Metrics**——`micrometer-registry-otlp` 标记为 optional，业务方按需启用 OTLP Metrics

### 本模块的"是"与"不是"

| ✅ 提供                                | ❌ 不提供                                      |
|-------------------------------------|--------------------------------------------|
| OpenTelemetry 核心依赖版本管理              | 自定义 Java 自动配置或 Bean                        |
| Spring Boot 自动装配（由 OTel Starter 提供） | 自定义 Sampler / SpanProcessor / Exporter     |
| OTLP / Zipkin / Logging 三种导出器       | 应用层 instrumentation（需要 Java Agent 或手动 API） |
| `@WithSpan` / `@SpanAttribute` 注解   | 控制台 UI / 规则推送中心                            |
| Micrometer OTLP Metrics（optional）   | 日志聚合（用 `atlas-richie-component-logging`）   |
| 服务名称、采样、exporter 端点等配置属性            | Profiling / Continuous Profiling           |

---

## 📦 托管依赖总览

本模块引入的全部依赖见 `pom.xml`，以下按功能分组说明各依赖的作用和典型使用场景。

### 核心依赖

| 依赖                                          | 作用                                                             | 何时用到                                                  |
|---------------------------------------------|----------------------------------------------------------------|-------------------------------------------------------|
| `opentelemetry-api`                         | OTel 接口定义——`Tracer`、`Span`、`OpenTelemetry`                     | 任何使用 OTel API 的地方（手动 span、instrumentation）            |
| `opentelemetry-sdk`                         | 完整 SDK（trace + metrics + logs）                                 | 生产运行必须                                                |
| `opentelemetry-sdk-trace`                   | Trace 专属 SDK（`SdkTracerProvider`、`SpanProcessor`）              | SDK 自动装配时使用                                           |
| `opentelemetry-sdk-extension-autoconfigure` | 从环境变量 / `otel.*` 配置初始化 SDK                                     | `OTEL_SERVICE_NAME`、`OTEL_TRACES_EXPORTER` 等环境变量生效的基础 |
| `opentelemetry-semconv`                     | 语义约定常量——`SemanticAttributes.*`（如 `HTTP_METHOD`、`DB_STATEMENT`） | 手动 span 时设置标准化 attribute 名称                           |
| `opentelemetry-instrumentation-annotations` | `@WithSpan`、`@SpanAttribute` 注解                                | 需要注解式埋点时（配合 Java Agent 或 Spring AOP）                  |

### 导出器依赖

| 依赖                               | 协议                         | 场景                                                      |
|----------------------------------|----------------------------|---------------------------------------------------------|
| `opentelemetry-exporter-otlp`    | OTLP（gRPC + HTTP/protobuf） | **首选**——发送到 OTel Collector / Jaeger / Tempo / Uptrace 等 |
| `opentelemetry-exporter-zipkin`  | Zipkin JSON（HTTP）          | **Legacy**——后端仅支持 Zipkin 时使用（OTel 官方标记 deprecated）      |
| `opentelemetry-exporter-logging` | 控制台输出                      | **开发调试**——span 打印到日志，不发送到任何后端                           |

### Spring Boot 集成

| 依赖                                  | 作用               | 说明                                                                      |
|-------------------------------------|------------------|-------------------------------------------------------------------------|
| `opentelemetry-spring-boot-starter` | Spring Boot 自动装配 | 提供 `OpenTelemetry` Bean、自动注册 `SdkTracerProvider`、`OtlpHttpSpanExporter` |

**该 Starter 自动完成的工作**（来自 `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`）：

- 自动创建 `OpenTelemetry` / `SdkTracerProvider` Bean
- 从 `otel.*` / `application.yml` 读取配置（服务名、采样率、exporter 端点）
- 自动注册 `OtlpHttpSpanExporter`（或 gRPC）
- 支持 `spring-web` / `spring-webmvc` / `spring-webflux` 的 AOP 埋点
- 支持可编程的 SDK 自定义（`AutoConfigurationCustomizerProvider`）

### 可选依赖

| 依赖                         | 作用                      | 启用方式                 |
|----------------------------|-------------------------|----------------------|
| `micrometer-registry-otlp` | 将 Micrometer 指标导出为 OTLP | 在业务方 `pom.xml` 中显式引入 |

---

## 🔧 使用场景选择

OpenTelemetry 在 Spring Boot 中有 3 种接入路线，**选型取决于你需要自动埋点到什么粒度**：

```
                    ┌──────────────────────────────────────┐
                    │  Java Agent                          │
                    │  零代码 · 150+ 库 · 全自动           │
                    │  ❌ 不支持 Native Image              │
                    └──────────────────────────────────────┘

                    ┌──────────────────────────────────────┐
                    │  Spring Boot Starter                 │
                    │  纯代码依赖 · AOP 埋点 · 可配置     │
                    │  ✅ 支持 Native Image                │
                    └──────────────────────────────────────┘

                    ┌──────────────────────────────────────┐
                    │  手动 API / @WithSpan                │
                    │  精细控制 · 无自动 instrumentation   │
                    └──────────────────────────────────────┘

                    ┌──────────────────────────────────────┐
                    │  web-core 的 Trace ID 透传           │
                    │  仅 traceId 传递 · 最轻量级           │
                    └──────────────────────────────────────┘
```

### 场景 A：Java Agent——零代码全量埋点

**适合**：已有 Spring Boot 应用，不想改一行代码，希望自动获取 HTTP / JDBC / 消息 / gRPC 等全部调用链。

```
# 启动时附加 javaagent（需要额外下载）
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=my-app \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -jar my-app.jar
```

**优点**：字节码注入 150+ 库（Spring MVC、WebFlux、JDBC、JPA、Kafka、gRPC、HTTP 客户端……），完全不侵入代码。

**缺点**：
- ❌ 不支持 GraalVM Native Image
- ❌ Agent 启动有额外开销（通常 50-200ms）
- ❌ 无法通过 `application.yml` 配置 OTel（用环境变量 / 系统属性）
- ❌ 多 Agent 共存可能有冲突

**本模块提供什么**：即使使用 Java Agent，本模块仍提供 API + SDK + 注解，用于在代码中补充自定义 span（`@WithSpan` / `tracer.spanBuilder()`）。

### 场景 B：Spring Boot Starter——纯代码集成（推荐）

**适合**：新版 Spring Boot（3.x+）应用，需要类型安全的配置、GraalVM Native 支持或 Agent 方式受限的场景。

**步骤**：

1. **引入依赖**（只需本模块 + BOM）

```xml
<!-- 已包含 opentelemetry-spring-boot-starter 及其所有传递依赖 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-tracing</artifactId>
</dependency>
```

2. **配置 `application.yml`**

```yaml
# OTel 环境变量方式（推荐——与部署环境解耦）
otel:
  service.name: my-app
  traces.exporter: otlp
  exporter:
    otlp:
      endpoint: http://otel-collector:4317
      protocol: grpc

# 或者 Spring Boot 管理属性方式
management:
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://otel-collector:4318/v1/traces
  tracing:
    sampling:
      probability: 0.1
```

3. **完成——启动即可看到 trace**

Spring Boot Starter 自动装配的埋点覆盖：
- **HTTP 服务端**——`@RestController` / `@Controller` 自动产生 span
- **HTTP 客户端**——`RestTemplate`、`RestClient`、`WebClient`
- **JDBC**——数据源操作自动产生 span
- **日志**——自动注入 `trace_id` / `span_id`

**本模块的价值**：业务方不需要管理 8+ OTel 依赖的版本——只引入本模块一个，所有版本由 `dependencies` BOM 统一锁定。

### 场景 C：手动 API / Annotations——自定义精细控制

**适合**：只想对关键业务方法埋点，不需要全自动 instrumentation；或在 Java Agent 基础上补充自定义 span。

**方式 1：注解**

```java
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;

@Service
public class OrderService {

    @WithSpan("OrderService.placeOrder")
    public Order placeOrder(@SpanAttribute("order.id") String orderId,
                            @SpanAttribute("order.amount") BigDecimal amount) {
        // 自动创建 span，方法返回时自动结束
        return doPlace(orderId, amount);
    }
}
```

**方式 2：手动 span**

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Tracer tracer;

    public Payment charge(PaymentRequest req) {
        Span span = tracer.spanBuilder("PaymentService.charge")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("payment.amount", req.getAmount())
                .startSpan();
        try (var scope = span.makeCurrent()) {
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

**本模块提供的依赖**：
- `opentelemetry-api`——`Tracer`、`Span`、`StatusCode` 等 API 类型
- `opentelemetry-instrumentation-annotations`——`@WithSpan`、`@SpanAttribute`
- `opentelemetry-semconv`——`SemanticAttributes.*` 常量，确保 attribute 名与 OTel 标准一致

### 场景 D：仅需 Trace ID 透传——用 `web-core`

**适合**：不需要全量 trace（不接 OTel Collector），只需要通过响应头和 MDC 传递 `traceId`，让运维和日志系统能追踪请求链路。

请使用 [`atlas-richie-component-web-core`](../atlas-richie-component-web/README.zh.md) 的 **§3 Trace ID 透传**：

```yaml
platform.component.web.tracing.enabled=true
```

这个模式**不需要** OTel Collector，不启动 exporter，**不产生 span**。它只做一件事：在 servlet 拦截器链最前置生成 `traceId`，写入 `X-Trace-Id` 响应头 + SLF4J MDC。与 `atlas-richie-component-logging` 配合后，日志中自动出现 `trace_id` 用于检索。

**什么时候选这个**：
- 不需要看调用链拓扑（火焰图、span 详情）
- 只需要用 traceId 索引日志
- 请求链路简单（1-2 跳），不需要跨服务交叉检索

**关系说明**：`web-core` 的 trace 透传和 `tracing` 模块**可以共存**。`web-core` 负责 servlet 容器层的 traceId 注入（ORDER=50 最前置），`tracing` 模块提供完整的 OTel SDK + 导出能力。二者同时启用时，`web-core` 生成的 traceId 会自然被 OTel SDK 继承为 span 上下文——互补，不冲突。

---

## ⚙️ 配置参考

本模块本身不定义配置属性（0 自定义代码）。所有配置来自 `opentelemetry-spring-boot-starter` 和 Spring Boot 原生 OTel 支持。

### OTel 环境变量（推荐——部署环境解耦）

| 属性                            | 示例值                                       | 说明          |
|-------------------------------|-------------------------------------------|-------------|
| `OTEL_SERVICE_NAME`           | `my-app`                                  | 服务名，必填      |
| `OTEL_TRACES_EXPORTER`        | `otlp` / `zipkin` / `none`                | trace 导出器   |
| `OTEL_METRICS_EXPORTER`       | `otlp` / `none`                           | metrics 导出器 |
| `OTEL_LOGS_EXPORTER`          | `otlp` / `none`                           | logs 导出器    |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4317`              | OTLP 后端地址   |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` / `http/protobuf`                  | OTLP 传输协议   |
| `OTEL_EXPORTER_OTLP_HEADERS`  | `api-key=xxx`                             | OTLP 请求头    |
| `OTEL_TRACES_SAMPLER`         | `parent_based_always_on` / `traceidratio` | 采样器         |
| `OTEL_TRACES_SAMPLER_ARG`     | `0.1`                                     | 采样比例        |

### Spring Boot `application.yml` 方式

```yaml
spring:
  application:
    name: my-app

management:
  tracing:
    sampling:
      probability: 0.1              # 采样概率
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
    logging:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/logs
  otlp:
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
```

### 采样策略速查

| 场景                 | 推荐配置                                         | 说明                       |
|--------------------|----------------------------------------------|--------------------------|
| 开发 / 调试            | `always_on` 或 `probability: 1.0`             | 100% 采样，不漏任何 trace       |
| 生产（低流量，<100 req/s） | `parent_based_always_on`                     | 继承父级采样决策，根 span 全采       |
| 生产（高流量）            | `traceidratio: 0.1` 或 `probability: 0.1`     | 10% 随机采样，足够统计            |
| 生产（关键路径全采）         | `parent_based_ratio: 1.0` + 用 `@WithSpan` 标记 | 关键方法全采，非关键继承父级           |
| 完全关闭               | `none`                                       | 禁用 trace 导出（SDK 仍加载但不发送） |

---

## 🎯 最佳实践

1. **参考 [场景 B](#场景-bspring-boot-starter纯代码集成推荐) 为首选路线**——Spring Boot Starter + `application.yml` 配置，兼顾可维护性和灵活性
2. **Java Agent 方案优先用于存量应用改造**——不想改代码时用 Agent；新项目用 Starter
3. **生产环境设 10% 采样**——`probability: 0.1` 足够观察统计特征，大幅降低存储成本
4. **跨服务调用点加 `@WithSpan`**——标识关键边界（如 `@WithSpan("PaymentService.charge")`），让调用链在关键节点有语义名称
5. **使用 OTel 语义属性**——用 `SemanticAttributes.HTTP_METHOD` 而非 `"http_method"`，确保与 OTel 后端兼容
6. **日志关联 trace**——配合 `atlas-richie-component-logging`，日志 JSON 自动包含 `trace_id`、`span_id`
7. **不要在 span name / attribute 里放 PII**——邮箱、手机号等敏感信息不应出现在 span 中
8. **与 `web-core` trace 透传共存时**——`web-core` 先注入 traceId，OTel SDK 自此继承；span 中会自动包含 `http.method`、`http.target` 等属性
9. **Zipkin exporter 已 deprecated**——新项目用 OTLP exporter（`otlp`），兼容 Jaeger / Tempo / Uptrace / Grafana 等所有主流后端

---

## ⚠️ 已知限制

| 限制                                   | 影响                                                    | 说明                                        |
|--------------------------------------|-------------------------------------------------------|-------------------------------------------|
| **本模块不包含 Java Agent**                | 无字节码级别的全自动 instrumentation                            | 需要 `opentelemetry-javaagent.jar` 需额外下载    |
| **Starter 的 instrumentation 有限**     | 仅 Spring Web / JDBC 等基础埋点                             | 深度埋点（Kafka / gRPC / Redis）需 Agent 或手动 API |
| **OTLP gRPC exporter**               | 需更多依赖，启动稍慢                                            | 默认用 `http/protobuf` 可避免                   |
| **Zipkin exporter deprecated**       | 未来 OTel 版本可能移除                                        | 新项目用 OTLP                                 |
| **Micrometer 集成 use Spring Boot 4+** | `management.opentelemetry.*` 属性在 Spring Boot 3.x 可能不全 | 检查具体版本的官方文档                               |

---

## ❓ 常见问题

### Q1：到底选 Java Agent 还是 Spring Boot Starter？

| 因素             | Agent        | Starter                                       |
|----------------|--------------|-----------------------------------------------|
| 代码改动           | 零            | 引入依赖 + 配置                                     |
| 埋点覆盖           | 150+ 库全自动    | Spring Web / JDBC / 日志                        |
| GraalVM Native | ❌            | ✅                                             |
| 配置方式           | 环境变量 / 系统属性  | `application.yml`                             |
| 启动开销           | 50-200ms     | 无额外开销                                         |
| 精细控制           | 弱（要改需编程 API） | 强（可自定义 `AutoConfigurationCustomizerProvider`） |

**结论**：Agent 是"数据全自动，改配置只能靠环境变量"；Starter 是"基础自动，精细可控，配置类型安全"。**大多数新项目选 Starter，存量项目先试 Agent**。

### Q2：引入本模块后，OTel SDK 是否自动生效？

是。`opentelemetry-spring-boot-starter` 在 classpath 上即激活自动装配（条件：Spring Boot 3.x+，检测到 `OpenTelemetry` Bean 未手动定义）。同时需设置 `OTEL_SERVICE_NAME` 或 `otel.service.name`，否则 trace 无服务名。

### Q3：需要更多 instrumentation（Kafka / Redis / gRPC）怎么办？

方案一：Java Agent（自动覆盖 Kafka、Redis、gRPC 等）；
方案二：手动引入对应 instrumentation 库，如 `opentelemetry-instrumentation-kafka-clients`；
方案三：用 `@WithSpan` 手动标记关键方法。

### Q4：怎么把 trace ID 写入日志？

配合 `atlas-richie-component-logging` 组件：日志 JSON 布局自动注入 `trace_id` / `span_id`。查看该组件 README 了解详情。

### Q5：怎么用 Jaeger 而不是 OTel Collector？

设置 `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317`——Jaeger 原生接受 OTLP gRPC。不需要额外的 Zipkin 桥接。

### Q6：可以只 trace 部分请求吗？

可以，用 `Sampler` 控制：
- 环境变量：`OTEL_TRACES_SAMPLER=traceidratio` + `OTEL_TRACES_SAMPLER_ARG=0.1`
- Spring Boot 属性：`management.tracing.sampling.probability=0.1`
- 编程：实现 `AutoConfigurationCustomizerProvider` 注入自定义 `Sampler`

---

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **Web-Core Trace 透传** — [`../atlas-richie-component-web/README.zh.md`](../atlas-richie-component-web/README.zh.md) §3
- **Logging** — [`../atlas-richie-component-logging/README.zh.md`](../atlas-richie-component-logging/README.zh.md)
- **OpenTelemetry 官方**：
  - [Spring Boot Starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
  - [Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
  - [Java API / SDK](https://opentelemetry.io/docs/languages/java/)
  - [Spring Boot 4 OTel 支持](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

---

**atlas-richie-component-tracing** —— 依赖托管，版本一致，开箱即 trace。
