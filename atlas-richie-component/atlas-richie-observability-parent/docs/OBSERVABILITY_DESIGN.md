# Atlas Richie Observability 完整架构设计

> 状态：组件实现、分阶段验收和 Foundry 最终 E2E 均已完成
>
> 目标：替代 atlas-richie-tracing 加基础依赖全局引入 Actuator 的接入方式，为 Atlas Richie 技术中台提供统一、可裁剪、可验证的完整应用可观测性方案。

## 1. 设计结论

### 1.1 应用接入结论

新应用的标准接入方式是只增加：

    <dependency>
        <groupId>cn.richie696.component</groupId>
        <artifactId>atlas-richie-observability-spring-boot-starter</artifactId>
    </dependency>

再配置：

    spring.application.name
    atlas.observability.enabled
    OTEL_EXPORTER_OTLP_ENDPOINT
    OTEL_EXPORTER_OTLP_PROTOCOL

应用不需要：

- 复制 OTel SDK 初始化代码；
- 手动创建 SDK provider；
- 为每个服务重复配置 exporter；
- 自己拼装 Trace Context；
- 自己实现 JVM、线程池、连接池指标；
- 自己处理 trace_id 与日志 MDC 的关联；
- 自己配置一组互相冲突的依赖版本。

### 1.2 模块合并结论

atlas-richie-observability-core 与 atlas-richie-tracing-core 合并为一个 atlas-richie-observability-core。

原因：

1. Trace、Metrics、Logs 共享同一套 Resource、Context、request_id 和生命周期。
2. 当前中台已经确定采用 OpenTelemetry + Micrometer，不需要为尚不存在的多 Trace 后端预留第二套核心模块。
3. 单独的 tracing core 会让协议组件和应用重复理解两套观测上下文。
4. 合并模块不等于把 Actuator 或 Spring Boot 放进 Core；Core 仍保持轻量和框架无关。

只有未来明确支持多个 Trace 实现、非 OTel 后端或独立 Trace API 时，才重新拆出 tracing-api。

### 1.3 全局依赖迁移结论

当前基础依赖 POM 中的 spring-boot-starter-actuator 属于应用运行时依赖，不应长期放在所有中台组件的全局 dependencies 中。迁移完成后：

- 基础依赖只保留 Actuator 的版本管理；
- 完整 Starter 实际引入 Actuator；
- 基础库只按需要依赖 observability-core 或 micrometer-core；
- 业务应用通过完整 Starter 获得管理端点和运行时指标。

该迁移已完成：未接入完整 Starter 的组件不再因为继承基础父 POM 而获得 Actuator；需要健康检查和管理端点的应用必须显式引入完整 Starter。

## 2. 现状基线

### 2.1 当前依赖结构

当前平台存在以下事实：

1. atlas-richie-dependencies 的 dependencies 中全局引入 spring-boot-starter-actuator。
2. atlas-richie-tracing 目前是无生产 Java 源码的 OTel 依赖聚合包。
3. atlas-richie-component-dependencies 托管了 atlas-richie-tracing 的版本，但没有提供完整的应用观测 Starter。
4. 部分组件，例如 MongoDB，额外声明 tracing 和 Prometheus registry。
5. atlas-richie-web-core 对 Micrometer 使用 optional 依赖，具备“启用 Actuator 才采集 Web 指标”的基础模式。
6. 当前 OTel 版本由平台 BOM 管控：instrumentation 2.31.1 对应 SDK 1.65.0。

### 2.2 现有方式的问题

| 问题 | 影响 |
|---|---|
| Actuator 全局传递 | 组件库被迫拥有应用管理能力，边界不清晰 |
| tracing 只是聚合包 | 没有中台统一开关、Resource、日志、Metrics 和生命周期实现 |
| OTel/Micrometer 分散 | 同一应用可能产生多个 Registry、Exporter 或重复指标 |
| 组件各自观测 | MongoDB、HTTP、gRPC、NATS 的命名、标签和 Context 可能漂移 |
| 总开关语义不完整 | OTEL_SDK_DISABLED 可以关闭 OTel，但不自动决定 Actuator 端点和 Micrometer registry |
| 依赖入口不清晰 | 新应用不知道应该增加 tracing、Actuator、Prometheus 还是 logging |

## 3. 总体架构

### 3.1 运行时数据流

    atlas-richie-observability
      ├── unified Resource / Context
      ├── correlation and request_id
      ├── lifecycle
      ├── redaction
      └── unified switch

    Trace/Span       Metrics/Actuator       Logs/MDC
    OTel Context     Micrometer             JSON stdout/OTLP
          \                 |                  /
                   Alloy OTLP / Prometheus
                            |
                 Tempo / Loki / Grafana

### 3.2 应用边界

    应用服务
      └── atlas-richie-observability-spring-boot-starter
            ├── observability-core
            ├── spring-boot-autoConfiguration
            ├── observability-actuator
            └── observability-logging

    协议组件
      ├── atlas-richie-http   -> 使用 core 的 Context/Propagation
      ├── atlas-richie-grpc   -> 使用 core 的 Context/Propagation
      ├── atlas-richie-nats   -> 使用 core 的 Context/Propagation
      ├── atlas-richie-mcp    -> 使用 core 的 Context/Propagation
      └── atlas-richie-web    -> 使用 core 的 request correlation 和 Metrics 契约

协议组件负责把上下文放入各自载体，不负责创建第二套 SDK 或 exporter。

## 4. 模块和包职责

### 4.1 atlas-richie-observability-core

建议包结构：

    cn.richie696.component.observability.core
    ├── context
    ├── correlation
    ├── propagation
    ├── resource
    ├── switch
    ├── lifecycle
    ├── attribute
    ├── metric
    └── noop

#### context

职责：

- 保存当前 OTel Context/Span；
- 提供 capture、restore、clear；
- 支持异步任务和线程池传递；
- 防止线程复用造成上下文串线。

不负责：

- 创建 HTTP、gRPC、NATS 客户端；
- 业务 Span 命名；
- 具体 exporter。

#### correlation

职责：

- 独立维护 request_id；
- 读取可信入站 request_id；
- 没有 request_id 时生成新 ID；
- 映射日志中的 trace_id、span_id、request_id；
- 明确 dedup_key 只作为幂等键。

规则：

    trace_id    = 当前 OTel Context 的 Trace ID
    span_id     = 当前 OTel Context 的 Span ID
    request_id  = 业务请求关联 ID
    dedup_key   = 幂等/去重键

四者不互相替代。

#### propagation

职责：

- W3C traceparent；
- W3C baggage；
- inject/extract 抽象；
- carrier 适配契约。

协议组件分别适配：

| 协议 | Carrier |
|---|---|
| HTTP | Header |
| gRPC | Metadata |
| NATS | Message Header |
| MCP HTTP/WebSocket | Handshake/Message Header |
| Worker | Message Metadata + 持久化关联字段 |

跨边界固定顺序：

    extract -> make current -> create child span -> inject

#### resource

职责：

- 合并服务 Resource；
- 定义优先级；
- 生成启动诊断；
- 防止不同服务使用不同字段名。

标准字段：

    service.name
    service.namespace
    service.version
    deployment.environment.name
    service.instance.id
    service.criticality
    k8s.namespace.name
    k8s.pod.name

#### switch

职责：

- 读取 ATLAS_OBSERVABILITY_ENABLED；
- 兼容 OTEL_SDK_DISABLED；
- 生成 effective enabled 状态；
- 向 Trace、Metrics、Logs、Actuator 和协议适配器广播统一状态。

有效状态：

    effective_enabled =
        ATLAS_OBSERVABILITY_ENABLED
        && !OTEL_SDK_DISABLED

关闭时必须：

- 不初始化 OTel SDK/provider；
- exporter 不连接后端；
- protocol instrumentation no-op；
- Micrometer registry no-op 或不导出；
- Actuator Metrics/Prometheus 端点不暴露；
- Health/Liveness/Readiness 保留。

#### lifecycle

职责：

- 初始化顺序；
- exporter shutdown；
- force flush；
- Worker/CLI 关闭；
- 超时和失败诊断。

#### attribute

职责：

- OTel 语义属性；
- 低基数字段；
- 长度限制；
- 敏感字段过滤；
- Prompt、Token、Authorization、Cookie 默认不进入 Trace/Logs。

#### metric

职责：

- Metric 命名约定；
- tag/label 约定；
- Timer、Counter、Gauge 的公共工厂；
- 低基数约束。

不得使用以下字段作为 Metrics label：

    trace_id
    request_id
    user_id
    完整 URL 参数
    完整 prompt
    tool arguments

### 4.2 atlas-richie-observability-spring-boot-autoconfigure

建议包结构：

    cn.richie696.component.observability.autoconfigure
    ├── ObservabilityAutoConfiguration
    ├── ObservabilityProperties
    ├── ObservabilityCondition
    ├── ObservabilityResourceConfiguration
    ├── ObservabilitySdkConfiguration
    ├── ObservabilityExporterConfiguration
    ├── ObservabilityLifecycleConfiguration
    ├── ObservabilityDiagnosticsConfiguration
    └── ObservabilityAutoConfiguration.imports

职责：

- 注册统一配置属性；
- 创建或复用 OTel SDK/provider；
- 配置 Resource、Sampler、Exporter；
- 注册总开关；
- 防止重复 SDK 初始化；
- 输出非敏感启动诊断；
- 在关闭时执行 flush/shutdown。

不负责：

- 具体业务 Span；
- 数据库业务规则；
- Grafana Dashboard；
- 具体服务的业务日志内容。

### 4.3 atlas-richie-observability-actuator

建议包结构：

    cn.richie696.component.observability.actuator
    ├── ObservabilityActuatorAutoConfiguration
    ├── ObservabilityMeterRegistryConfiguration
    ├── ObservabilityCommonTagsConfiguration
    ├── jvm
    ├── executor
    ├── connection
    ├── servlet
    ├── reactive
    ├── sse
    ├── worker
    ├── channel
    └── endpoint

采集范围：

#### JVM

- Heap used、committed、max；
- Non-Heap；
- Metaspace；
- Code Cache；
- Eden、Survivor、Old Generation；
- GC 次数和耗时；
- GC pause P95/P99；
- 类加载；
- 进程 CPU；
- 文件描述符；
- JVM uptime。

#### 线程和线程池

- live、daemon、peak threads；
- BLOCKED、WAITING、RUNNABLE；
- deadlock count；
- active、core、max；
- queue size；
- completed、rejected；
- task duration；
- queue wait duration。

#### 连接池

- Hikari active、idle、total、pending；
- Redis active、idle、pending；
- HTTP leased、available、pending；
- gRPC channel、inflight；
- NATS connection、reconnect、pending；
- MCP connection、stream。

#### Web、Servlet、Netty、SSE

- request count、error、latency；
- active connection；
- busy thread；
- event loop pending task；
- bytes in/out；
- SSE active connections；
- SSE first byte；
- SSE disconnect、timeout、write failure；
- SSE connection duration。

#### Worker、Channel

- queue backlog；
- consumer lag；
- retry/dead-letter；
- task success/error；
- task duration；
- queue wait；
- active worker。

Actuator 端点建议：

    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus
      endpoint:
        health:
          probes:
            enabled: true

默认不对外暴露：

    env
    configprops
    heapdump
    threaddump
    loggers
    beans

Thread Dump、Heap Dump、JFR 是按需诊断能力，不作为高频日志或 Metrics 持续上报。

### 4.4 atlas-richie-observability-logging

建议包结构：

    cn.richie696.component.observability.logging
    ├── ObservabilityLoggingAutoConfiguration
    ├── ObservabilityMdcBridge
    ├── StructuredLogConfiguration
    ├── RedactionConfiguration
    ├── LogCorrelationFields
    └── LogbackIntegration

最低 JSON 字段：

    timestamp
    severity
    service.name
    service.version
    deployment.environment.name
    trace_id
    span_id
    request_id
    operation
    stage
    status
    duration_ms
    message

没有当前 Span 时不得用 request_id 或随机字符串伪造 trace_id/span_id。

### 4.5 atlas-richie-observability-testkit

建议包结构：

    cn.richie696.component.observability.testkit
    ├── TraceAssertions
    ├── PropagationAssertions
    ├── CorrelationAssertions
    ├── MetricAssertions
    ├── ToggleAssertions
    ├── DuplicateInstrumentationAssertions
    └── ObservabilityTestApplication

覆盖：

- 开启/关闭总开关；
- Resource 合并；
- HTTP/gRPC/NATS/MCP inject/extract；
- trace_id/span_id/request_id 不混淆；
- 线程池和连接池 Metrics；
- Actuator 端点暴露；
- 无重复 Span；
- 无重复 Metrics；
- shutdown flush；
- 敏感字段脱敏。

### 4.6 atlas-richie-observability-spring-boot-starter

该模块不承载核心业务实现，只聚合完整应用运行时依赖：

    observability-core
    spring-boot-autoConfiguration
    observability-actuator
    observability-logging

应用开发者只依赖此模块；平台和组件开发者不要默认依赖此模块。

## 5. 配置契约

### 5.1 标准环境变量

    ATLAS_OBSERVABILITY_ENABLED: true
    OTEL_SDK_DISABLED: false
    OTEL_SERVICE_NAME: example-service
    OTEL_RESOURCE_ATTRIBUTES: service.namespace=foundry,service.version=1.0.0,deployment.environment.name=local,service.instance.id=HOSTNAME,service.criticality=normal
    OTEL_EXPORTER_OTLP_ENDPOINT: http://alloy:4318
    OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf
    OTEL_TRACES_EXPORTER: otlp
    OTEL_METRICS_EXPORTER: otlp
    OTEL_LOGS_EXPORTER: otlp
    OTEL_PROPAGATORS: tracecontext,baggage
    OTEL_TRACES_SAMPLER: parentbased_always_on

### 5.2 配置优先级

    部署环境变量
      -> Nacos shared 配置
      -> application.yml
      -> spring.application.name/build metadata 默认值

总开关的关闭优先级最高：

    ATLAS_OBSERVABILITY_ENABLED=false
    或 OTEL_SDK_DISABLED=true
    => effective disabled

### 5.3 单信号开关

完整模式：

    OTEL_TRACES_EXPORTER: otlp
    OTEL_METRICS_EXPORTER: otlp
    OTEL_LOGS_EXPORTER: otlp

资源受限时可以选择：

    OTEL_TRACES_EXPORTER: otlp
    OTEL_METRICS_EXPORTER: none
    OTEL_LOGS_EXPORTER: none

但这只能声明“Trace 接入”，不能声明“完整观测接入”。

### 5.4 Actuator 与总开关

关闭完整观测时：

- health/liveness/readiness 保留；
- /actuator/metrics 关闭或内部不可访问；
- /actuator/prometheus 关闭；
- Micrometer 不向后端导出；
- 应用正常启动和处理请求。

## 6. 指标和信号清单

### 6.1 Trace

核心 Span：

    gateway.request
    agent.request
    routing
    frontdoor.call
    llm.call
    rag.retrieve
    tool.call
    sse.stream
    channel.consume
    worker.process

Span 属性：

- service/resource；
- operation/stage；
- duration/status/error；
- provider/model/tool_name；
- retrieval_count；
- token_usage；
- retry/timeout/fallback；
- queue wait；
- SSE first byte。

禁止放入：完整 Prompt、Token、密钥、Cookie、Authorization、完整工具输入输出和高基数用户数据。

### 6.2 Logs

日志必须具备：

- severity；
- timestamp；
- service/resource；
- trace_id/span_id/request_id；
- operation/stage；
- status/duration；
- error.type/error.stage；
- 脱敏后的业务摘要。

### 6.3 Metrics

分为：

1. RED：rate、error、duration；
2. JVM/GC/Memory；
3. Thread/Executor；
4. DB/Redis/HTTP/gRPC/NATS/MCP connection pool；
5. Servlet/WebFlux/Netty/SSE；
6. Queue/Worker/Channel；
7. Agent/LLM/RAG/Tool；
8. Container/Process；
9. Circuit breaker/rate limit/retry/fallback。

Metrics 标签只使用低基数值：

    service.name
    environment
    instance
    operation
    stage
    pool_name
    pool_type
    target_service
    status
    error_type

### 6.4 Profiles 和诊断

以下不作为普通 OTel Metrics：

- Thread Dump；
- Heap Dump；
- JFR；
- CPU Profile；
- Native Memory Tracking。

它们通过运维诊断流程按需采集，并遵守敏感信息访问控制。

## 7. 协议接入规则

### HTTP

入站：extract W3C、建立 server span、解析 request_id。

出站：使用公共 carrier inject，不允许业务手写 traceparent 或自造 X-Trace-Id。

### gRPC

- Server interceptor 负责 extract 和 server span；
- Client interceptor 负责 child span 和 Metadata inject；
- 自动埋点与平台 interceptor 只能有一个边界 owner；
- 业务手工 Span 只能补充业务阶段。

### NATS

- producer inject；
- consumer extract；
- consumer 建立 consumer/worker span；
- request_id、message_id、dedup_key、trace_id 分开；
- backlog、consumer lag、retry、dead-letter 进入 Metrics/Logs。

### MCP/WebSocket

- HTTP handshake inject/extract；
- WebSocket forwarding 不能只传 X-* 兼容头；
- tool.call Span 使用稳定 tool_name；
- 工具参数和返回值只记录脱敏摘要或长度。

## 8. 启动生命周期

### 开启模式

    读取配置
      -> 合并 Resource
      -> 计算 effective enabled
      -> 创建 SDK/provider
      -> 创建 MeterRegistry/Log bridge
      -> 注册 protocol instrumentation
      -> 输出非敏感启动诊断
      -> 接收请求

### 关闭模式

    读取关闭开关
      -> 不创建 exporter/provider
      -> 注册 No-op Context/Tracer/Meter
      -> 不打开 Actuator Metrics/Prometheus
      -> 保留健康检查
      -> 正常处理请求

### 关闭流程

    停止接收新任务
      -> 完成/取消在途任务
      -> force flush
      -> shutdown exporters
      -> 记录失败和丢弃数量
      -> 在超时内结束进程

## 9. 依赖治理

### 9.1 版本管理

OTel API、SDK、Instrumentation BOM、Spring Boot Starter、Micrometer 版本由平台 BOM 统一锁定。

当前基线：

    OpenTelemetry instrumentation: 2.31.1
    OpenTelemetry SDK: 1.65.0

任何模块不得自行覆盖这些版本。升级必须验证：

- effective POM；
- dependency tree；
- Spring Boot 启动；
- HTTP/gRPC/NATS 传播；
- Actuator Metrics；
- shutdown flush。

### 9.2 依赖方向

允许：

    protocol component -> observability-core
    spring-boot-autoconfigure -> observability-core
    actuator -> observability-core
    logging -> observability-core
    starter -> all runtime modules

禁止：

    observability-core -> Actuator
    observability-core -> Spring MVC
    observability-core -> specific database client
    protocol component -> full application starter
    component library -> application management endpoint

### 9.3 Prometheus 与 OTLP 出口

同一个指标不能同时被 Prometheus scrape 和 OTLP 重复上报。

需要在部署环境选择一种主路径：

    Path A: Actuator /prometheus -> Prometheus scrape
    Path B: Micrometer/OTel Metrics -> OTLP -> Alloy -> Prometheus

完整新方案优先统一到 OTLP；兼容期允许 Path A，但必须明确唯一出口。

## 10. 迁移方案

### Phase 0：组件骨架

- 建立本 parent 和模块；
- 建立 README/设计契约；
- 加入 Reactor；
- 已由完整 observability starter 替代旧 tracing 和全局 Actuator 接入方式。

### Phase 1：实现 Core 和 AutoConfiguration

- 实现 unified context；
- 实现 request_id；
- 实现 Resource；
- 实现总开关；
- 实现 no-op；
- 实现 SDK/provider/exporter 生命周期；
- 实现启动诊断。

### Phase 2：实现 Actuator 和 Logging

- JVM/GC/Thread/Pool binders；
- Servlet/WebFlux/Netty/SSE；
- Hikari/Redis/HTTP/gRPC/NATS；
- JSON stdout；
- Trace-to-Logs；
- Actuator endpoint 策略。

本阶段在基础组件层完成通用 Binder、适配边界、日志关联、WebFlux/Reactor 上下文恢复和本地三信号闭环。具体协议客户端、SSE、Worker、Channel 和存量连接池的业务接入属于下一阶段的协议迁移，不在基础 Starter 内强行绑定具体客户端。

### Phase 3：协议组件迁移

- HTTP；
- gRPC；
- NATS；
- MCP；
- Worker/Channel；
- 去除重复 boundary instrumentation。

### Phase 4：应用迁移

每个服务：

1. 增加完整 Starter；
2. 设置 spring.application.name；
3. 验证 Resource；
4. 验证真实 Trace；
5. 验证 JSON Logs；
6. 验证 JVM/Pool Metrics；
7. 验证总开关；
8. 再移除本服务对旧 tracing 的直接依赖；
9. 最后从基础依赖移除全局 Actuator。

### Phase 5：旧组件收敛

- 删除 atlas-richie-tracing 旧模块及其版本托管；
- 清理组件库对完整 Starter 的误用；
- 清理重复 Prometheus registry；
- 基础依赖不再全局引入 Actuator；
- 更新平台 README 和能力目录。

## 11. 验收清单

以下清单已按 Phase 1～4.6 验收记录和最终 Foundry E2E 记录完成核对；勾选项均有对应的代码、测试或真实运行时证据。

### 依赖和启动

- [x] 新应用只增加完整 Starter 即可启动。
- [x] effective POM 中 OTel API/SDK/Instrumentation 版本唯一。
- [x] 不产生重复 OTel provider。
- [x] 启动日志打印非敏感 Resource、Exporter、Sampler 和开关状态。

### Trace

- [x] Gateway -> Application Trace 连续。
- [x] HTTP/gRPC/NATS/MCP 传播连续。
- [x] trace_id/span_id/request_id 分离。
- [x] 无重复 server/client boundary Span。
- [x] 超时、重试、异常、降级可定位。

### Metrics

- [x] JVM/Heap/GC/Metaspace 可查询。
- [x] Thread/Executor/Rejected/Queue 可查询。
- [x] DB/Redis/HTTP/gRPC/NATS 连接池可查询。
- [x] Servlet/WebFlux/Netty/SSE 可查询。
- [x] Agent/LLM/RAG/Tool/Worker/Channel 可查询。
- [x] 容器 CPU、RSS、throttling、OOM、restart 可查询。
- [x] 没有 request_id/trace_id 等高基数 label。

### Logs

- [x] Loki 能按 trace_id 查询跨服务日志。
- [x] 日志包含 operation/stage/status/duration。
- [x] 错误包含 error.type/error.stage。
- [x] Prompt、Token、Authorization、Cookie、密钥不会进入监控后端。

### 总开关

- [x] ATLAS_OBSERVABILITY_ENABLED=false 关闭全部观测输出。
- [x] OTEL_SDK_DISABLED=true 兼容关闭 OTel。
- [x] 关闭后无 exporter 连接。
- [x] 关闭后应用启动、请求、异步任务、关闭均正常。
- [x] health/liveness/readiness 仍然可用。

## 12. 设计模式和误用防护

### Facade

spring-boot-starter 作为应用接入外观，隐藏多个观测模块的依赖和初始化顺序。

防护：Starter 不承载业务逻辑，不向应用暴露所有底层配置对象。

### Adapter

Actuator/Micrometer、OTel Metrics、Prometheus/OTLP 之间使用适配层。

防护：不把某一个 exporter 的类型穿透到应用业务层。

### Strategy

Metrics 出口、Sampler、Log export 可以是配置选择的策略。

防护：策略只负责技术选择，不能把业务路由规则写入观测组件。

### No-op 替换

观测关闭时使用 No-op Context/Tracer/Meter/Exporter 行为。

防护：No-op 必须保持业务调用契约，不能吞掉业务异常或改变业务返回结果。

## 13. 当前实现边界

Phase 1～4.6 以及最终 Foundry E2E 已经落地：

- Maven parent 和六个子模块；
- Core 的 Context、CorrelationIds、W3C Propagator 和不可变 ObservabilityState；
- Spring Boot 配置属性、EnvironmentPostProcessor、OTel SDK 禁用映射和 Resource 属性映射；
- Actuator/Micrometer 总开关过滤、Resource 公共标签和显式线程池 Binder；
- Servlet Request ID、MDC 关联、响应 X-Request-Id 和安全 Request ID 过滤；
- 官方 OTel Logback Appender 的生命周期装配；
- 官方 OTel No-op 关闭契约、启动诊断和 Context 关闭前的 Trace/Metrics/Logs force flush；
- Testkit、Core/AutoConfiguration/Actuator/Logging/Starter 测试；
- WebFlux/Reactor 异步上下文、MDC 恢复和清理；
- 线程池低基数指标、拒绝计数和依赖指标通用门面；
- 本地 Alloy、Prometheus、Loki、Tempo、Pyroscope、Grafana 的后端 smoke 验证；
- 真实 Starter 应用的 OTLP Trace、Logs、Metrics 发送，以及 Tempo/Loki/Prometheus 关联查询。

Phase 1 已完成的边界：

- SDK/provider/exporter 的创建和最终 shutdown 继续由官方 OTel Spring Boot Starter 管理；
- 平台自动配置负责 effective enabled 计算、非敏感启动诊断和关闭前 force flush；
- 诊断只输出 Resource 标准身份字段、Exporter/Sampler 名称和开关状态，不输出 OTLP endpoint；
- 关闭模式由官方 Starter 提供 `OpenTelemetry.noop()`，平台适配器使用统一 `ObservabilityState`。

Phase 2、Phase 3、Phase 4.1～4.6 的具体证据分别见对应验收文档；最终 Foundry
Trace-to-Logs/Metrics/Actuator、统一开关、Service Graph、脱敏和优雅关闭证据见
`docs/FINAL_FOUNDRY_E2E_ACCEPTANCE.md`。生产环境的容量、保留期、告警规则、TLS 证书轮换和
高可用演练属于部署环境运行手册，不作为本次组件代码验收的未完成项。
