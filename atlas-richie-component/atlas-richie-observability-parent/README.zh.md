# Atlas Richie Observability

Atlas Richie Observability 是 Atlas Richie 技术中台面向 Spring Boot 应用的统一可观测性接入组件。它把 OpenTelemetry Trace、Micrometer/Actuator Metrics、结构化日志、JVM 运行时数据和跨协议上下文传播统一到一套应用接入方式中。

应用侧的目标是：

1. 增加一个 Starter；
2. 配置服务身份、OTLP 出口和观测开关；
3. 直接进入 Alloy + Tempo + Loki + Prometheus + Grafana + Pyroscope 监控体系。

本 README 面向应用开发者和组件使用者，重点说明接入方法、模块关系、配置方式、数据去向和开关行为。完整的包级职责、指标设计、协议细节、迁移方案和验收标准见：

- [完整架构设计](./docs/OBSERVABILITY_DESIGN.md)
- [测试设计](./docs/OBSERVABILITY_TEST_PLAN.md)
- [Phase 2 验收记录](./docs/PHASE_2_ACCEPTANCE.md)
- [Phase 2 阶段检查记录](./docs/PHASE_2_PROGRESS.md)
- [Phase 3 阶段记录](./docs/PHASE_3_PROGRESS.md)
- [English README](./README.md)

## 一、接入前需要理解的几个概念

### 1. Trace、Metrics、Logs 和 Profiles

一次应用请求通常同时产生四类信息：

| 类型 | 用途 | 主要展示位置 |
|---|---|---|
| Trace/Span | 查看请求经过了哪些服务、阶段和下游，以及每一段耗时 | Tempo、Grafana Trace |
| Metrics | 查看请求量、错误率、延迟、JVM、线程池、连接池和队列趋势 | Prometheus、Grafana Dashboard |
| Logs | 查看具体错误、业务阶段和处理结果 | Loki、Grafana Explore |
| Profiles/诊断 | 定位 CPU 热点、线程阻塞和内存问题 | Pyroscope 或按需运维诊断 |

这些数据通过统一的服务身份和关联字段连接起来：

    service.name
    service.version
    deployment.environment.name
    service.instance.id
    trace_id
    span_id
    request_id
    operation
    stage

其中：

- trace_id 是 OpenTelemetry 技术调用链 ID；
- span_id 是当前 Span ID；
- request_id 是业务请求或消息关联 ID；
- dedup_key 只用于幂等和去重，不能替代前三个字段。

trace_id 必须来自当前 OTel Context，不能用自造的 X-Trace-Id 替代 W3C traceparent。详细字段语义和跨协议规则见设计文档。

### 2. 数据流向

应用一般不直接连接 Tempo、Loki 或 Prometheus，而是把观测数据发送到 Alloy：

    Application
        -> OTLP / Prometheus scrape
        -> Alloy
             ├── traces  -> Tempo
             ├── logs    -> Loki
             └── metrics -> Prometheus

Grafana 统一查询这些后端，并提供 Dashboard、Explore、Trace-to-Logs、Trace-to-Metrics 和 Service Map。

## 二、模块关系

组件由一个 Maven parent 和六个职责明确的子模块组成：

    atlas-richie-observability-parent
    ├── atlas-richie-observability-core
    ├── atlas-richie-observability-spring-boot-autoconfigure
    ├── atlas-richie-observability-actuator
    ├── atlas-richie-observability-logging
    ├── atlas-richie-observability-testkit
    └── atlas-richie-observability-spring-boot-starter

### 应用依赖关系

普通 Spring Boot 应用只依赖：

    atlas-richie-observability-spring-boot-starter
        ├── observability-core
        ├── spring-boot-autoConfiguration
        ├── observability-actuator
        └── observability-logging

协议或基础组件只依赖 observability-core，例如：

    atlas-richie-http  -> observability-core
    atlas-richie-grpc  -> observability-core
    atlas-richie-nats  -> observability-core
    atlas-richie-mcp   -> observability-core
    atlas-richie-web   -> observability-core

这样可以让底层组件复用 Context、传播和关联契约，同时避免把 Actuator、Exporter 和管理端点传递到所有基础库。

### 各模块的使用定位

#### atlas-richie-observability-parent

Maven 聚合、版本和文档边界。应用不直接依赖该模块，它不承担运行时初始化。

#### atlas-richie-observability-core

统一的基础契约，包含 Context、W3C 传播、request_id 关联、Resource、属性约束、生命周期和总开关抽象。它合并了原计划中的 observability-core 和 tracing-core，但不绑定 Spring Boot、Actuator 或具体后端。

#### atlas-richie-observability-spring-boot-autoconfigure

Spring Boot 自动配置实现，负责把 Core、OTel SDK、Resource、Sampler、Exporter、生命周期和配置属性装配起来。它是实现模块，不建议应用直接依赖。

#### atlas-richie-observability-actuator

把 Actuator 和 Micrometer 运行时指标纳入统一出口。JVM、GC、HTTP 和标准连接池优先复用 Spring Boot/Micrometer 官方 Binder；组件额外提供低基数线程池指标、拒绝任务计数包装器和 `DependencyMetrics` 门面，供 DB、Redis、HTTP、gRPC、NATS、MCP 适配器统一记录请求耗时和连接数，避免各组件自行发明指标命名。

#### atlas-richie-observability-logging

统一 MDC 关联字段、请求完成日志和敏感值过滤，使日志可以按 trace_id、request_id、operation、stage、status、duration_ms 在 Loki 中检索，并从 Tempo Trace 跳转到 Loki。stdout 的具体 JSON Encoder 仍由应用 Logback 配置负责，组件不会强行覆盖已有日志布局。

#### atlas-richie-observability-testkit

测试依赖，不进入生产运行时。用于验证传播、关联、指标、总开关、重复埋点和敏感数据过滤。

#### atlas-richie-observability-spring-boot-starter

普通应用的唯一依赖入口，负责聚合上述运行时模块。新应用不需要再分别声明 OTel Starter、Actuator 或 Prometheus Registry。

## 三、新应用接入

### 1. 添加依赖

    <dependency>
        <groupId>cn.richie696.component</groupId>
        <artifactId>atlas-richie-observability-spring-boot-starter</artifactId>
    </dependency>

版本由 atlas-richie-component-dependencies 统一管理。

### 2. 配置服务身份和 OTLP 出口

建议把环境相关内容放在部署环境变量中：

    OTEL_SERVICE_NAME=example-service
    OTEL_RESOURCE_ATTRIBUTES=service.namespace=foundry,service.version=1.0.0,deployment.environment.name=local,service.instance.id=example-1,service.criticality=normal
    OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4318
    OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
    OTEL_TRACES_EXPORTER=otlp
    OTEL_METRICS_EXPORTER=otlp
    OTEL_LOGS_EXPORTER=otlp
    OTEL_PROPAGATORS=tracecontext,baggage
    OTEL_TRACES_SAMPLER=parentbased_always_on

应用配置至少需要：

    spring:
      application:
        name: example-service

    atlas:
      observability:
        enabled: true

    otel:
      sdk:
        disabled: false
      exporter:
        otlp:
          endpoint: http://alloy:4318
          protocol: http/protobuf
      traces:
        exporter: otlp
        sampler: parentbased_always_on
      metrics:
        exporter: otlp
      logs:
        exporter: otlp

实际部署时应使用平台支持的标准 OTel 配置方式；不要在业务代码中自行创建 SDK Provider、Exporter 或拼装 traceparent。

### 3. 配置 Actuator 管理端点

推荐只开放健康检查、应用信息和 Prometheus 端点：

    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus
      endpoint:
        health:
          probes:
            enabled: true

env、configprops、beans、loggers、heapdump 和 threaddump 等端点不应默认暴露到公网。Heap Dump、Thread Dump 和 JFR 属于按需诊断能力，不是常规高频监控数据。

### 4. 验证接入

接入后至少验证：

1. 服务能在 Tempo 查询到真实 Trace；
2. Loki 能按 trace_id 或 request_id 查询日志；
3. Prometheus 能查询 JVM、线程池、连接池和 HTTP 指标；
4. Grafana Trace 页面可以跳转到对应日志；
5. 不同服务之间的 HTTP、gRPC、NATS 或 MCP 上下文能够连续传播；
6. 日志和 Span 中没有 Token、Cookie、Authorization、Prompt 和完整工具参数。

完整验收项见设计文档的验收标准章节。

## 四、指标和日志最终展示在哪里

应用只负责通过组件产生标准数据，后端和 Grafana 负责存储、查询和展示：

| 应用能力 | 主要数据 | 最终位置 |
|---|---|---|
| HTTP、gRPC、NATS、MCP Trace | Span、状态、耗时、异常 | Tempo / Grafana Trace |
| 结构化应用日志 | JSON 日志、关联 ID、错误阶段 | Loki / Grafana Explore |
| JVM 和 GC | Heap、Metaspace、GC、CPU、FD | Prometheus / JVM Dashboard |
| 线程池和 Worker | active、queue、rejected、duration、lag | Prometheus / Thread Pool Dashboard |
| DB、Redis、HTTP、gRPC、NATS 连接池 | active、idle、pending、timeout、error | Prometheus / Dependency Dashboard |
| Servlet、WebFlux、Netty、SSE | 请求量、状态码、延迟、连接数、写失败 | Prometheus + Tempo + Loki |
| LLM、RAG、Tool、Channel | 业务 Span、阶段指标和错误日志 | Tempo + Prometheus + Loki |
| CPU 和内存 Profile | 热点和采样诊断信息 | Pyroscope |
| 健康状态 | health、liveness、readiness | Kubernetes / Grafana |

Grafana 需要预先配置 Prometheus、Loki、Tempo 和 Pyroscope 数据源。Trace-to-Logs、Trace-to-Metrics、Exemplar 和 Service Map 还需要配套的 Grafana、Tempo、Prometheus 后端配置，不能仅凭应用依赖自动产生。

## 五、总开关和信号裁剪

### 全量开启

    ATLAS_OBSERVABILITY_ENABLED=true
    OTEL_SDK_DISABLED=false
    OTEL_TRACES_EXPORTER=otlp
    OTEL_METRICS_EXPORTER=otlp
    OTEL_LOGS_EXPORTER=otlp

此时 Trace、Metrics、Logs 按配置发送到 Alloy，并由 Alloy 路由到 Tempo、Prometheus 和 Loki。

### 全部关闭

    ATLAS_OBSERVABILITY_ENABLED=false

或使用兼容配置：

    OTEL_SDK_DISABLED=true

关闭后应满足：

- 不初始化 OTel SDK/Provider；
- 不建立 Exporter 连接；
- Trace、Metrics、Logs 不导出；
- 协议埋点退化为 No-op；
- 应用启动、请求、异步任务和 shutdown 不受影响；
- health、liveness、readiness 仍然可用。

开启模式下，如果应用没有显式配置 Actuator 暴露范围，组件默认暴露 `health,info,prometheus`，并开启 liveness/readiness probes；应用已有配置优先，不会被覆盖。

### 单独关闭某类信号

总开关开启时可以只关闭某类出口：

    OTEL_TRACES_EXPORTER=otlp
    OTEL_METRICS_EXPORTER=none
    OTEL_LOGS_EXPORTER=none

这表示只接入 Trace，不应被标记为完整监控体系。模块级配置和开关优先级见设计文档。

## 六、协议组件如何配合

应用和协议组件不需要重复实现观测逻辑，只需要遵守统一边界：

- HTTP/WebFlux/Servlet：框架负责 Trace 边界，业务侧补充 request_id、operation 和 stage；
- HTTP Client、WebSocket、gRPC：由平台适配器负责创建 Client Span 和注入传播信息；
- NATS/JetStream：Producer 注入、Consumer 提取，业务成功后再 Ack；
- MCP：Handshake、WebSocket 和 Tool 调用保持上下文连续，并对 Token 和工具参数脱敏；
- 异步线程池、Worker、Reactor：必须同时恢复 OTel Context、MDC 和业务 request_id。

协议适配的包级职责和具体验收规则不在 README 中展开，统一维护在完整架构设计中。

## 七、从旧方案迁移

旧 tracing 和基础依赖中的全局 Actuator 已完成清理，当前统一按以下方式接入：

1. 新应用直接使用完整 Starter；
2. 存量应用逐个增加完整 Starter；
3. 验证 Trace、Logs、JVM 和依赖指标；
4. 清理重复的 OTel Starter、Prometheus Registry 和旧 tracing 依赖；
5. 需要管理端点的应用统一显式依赖完整 Starter；
6. 使用 `otel.sdk.disabled=true` 关闭 SDK，关闭时无 Collector 也不会因 OTel exporter 连接失败导致启动失败。

## 八、当前实现状态

当前已经完成：

- Maven parent 和子模块骨架；
- Core 的 Context、Correlation、W3C Propagator 和统一状态契约；
- Spring Boot 配置属性、总开关映射和 Resource 属性映射；
- Actuator MetricsFilter、公共标签和线程池 Binder；
- Servlet/WebFlux Request ID、MDC 关联、Reactor 异步恢复和官方 OTel Logback Appender 装配；
- Actuator 默认端点策略、JVM/GC/标准运行时指标、线程池 Binder、拒绝计数和 `DependencyMetrics` 低基数门面；
- Testkit、模块级单元测试、自动配置测试、Starter 集成测试和真实本地 OTLP 三信号测试；
- 本地 Alloy、Prometheus、Loki、Tempo、Pyroscope、Grafana smoke，以及 Tempo/Loki/Prometheus 关联查询。

Phase 3 协议组件迁移、Phase 4 Foundry 应用迁移、Service Map、Trace-to-Logs、Trace-to-Metrics、
统一关闭开关和最终真实 E2E 均已完成。详细职责、实现边界、迁移过程和逐阶段证据以
`OBSERVABILITY_DESIGN.md`、各 `PHASE_*_ACCEPTANCE.md` 和
`FINAL_FOUNDRY_E2E_ACCEPTANCE.md` 为准。
