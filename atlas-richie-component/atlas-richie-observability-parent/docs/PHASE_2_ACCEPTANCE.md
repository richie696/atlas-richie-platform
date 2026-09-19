# Phase 2 验收记录：Actuator、Metrics、Logging 与本地闭环

## 1. 结论

Phase 2 已通过当前架构边界的验收，可以进入 Phase 3。

本阶段关闭的是“完整 Starter 的运行时基础能力”：Actuator 策略、JVM/GC/标准运行时指标、线程池通用 Binder、请求日志关联、WebFlux/Reactor 上下文恢复、OTLP 三信号出口、总开关和本地 Alloy/Tempo/Loki/Prometheus 闭环。

HTTP、gRPC、NATS、MCP、Redis Stream 等协议组件的具体 Client/Consumer/Producer 迁移，以及 Worker、Channel、SSE 的领域专用 Binder，不在本次 Phase 2 的关闭范围内，按设计进入 Phase 3。Phase 3 不得重复创建 SDK Provider、重复 HTTP Server Span 或重复 Prometheus Registry，而应复用本阶段提供的 Core、`DependencyMetrics` 和标准 OTel/Micrometer 入口。

## 2. 验收矩阵

| 验收项 | 结果 | 证据 |
|---|---|---|
| 完整 Starter 可启动 | 通过 | `Phase2EnabledSpringBootTest` 真实创建 Spring Boot WebFlux ApplicationContext |
| Actuator 默认端点 | 通过 | 开启模式暴露 `health/info/prometheus`，并保留 liveness/readiness |
| JVM、GC、Heap、Metaspace | 通过 | 本地 Prometheus 查询到 `jvm_memory_used_bytes`、GC、Class、CPU 等系列 |
| Thread/Executor/Rejected/Queue | 通过 | `ExecutorMetricsBinderTest` 覆盖 active、pool、queue、completed、rejected |
| 依赖指标通用契约 | 通过 | `DependencyMetricsTest` 覆盖低基数请求 Timer 和连接 Gauge |
| Servlet 请求日志 | 通过 | `ObservabilityMdcFilterTest` 覆盖成功、异常、清理和敏感值脱敏 |
| WebFlux/Reactor/MDC | 通过 | `ObservabilityWebFluxFilterTest` 覆盖 `boundedElastic` 线程切换和 MDC 清理；Starter 测试覆盖真实 WebFlux 请求 |
| Netty 请求边界 | 通过 | 本地 Starter 使用 Reactor Netty 接收真实 HTTP 请求，并在 Tempo 查询到 WebFlux Server Span |
| OTLP Trace/Logs/Metrics | 通过 | `Phase2LocalOtlpSmokeTest` 向本地 Alloy 发送真实请求并查询三类数据 |
| Trace-to-Logs | 通过 | Tempo Trace 的 `trace_id` 与 Loki 日志中的 `trace_id`、`span_id` 一致；本地 Grafana 已配置跳转关系 |
| 总开关 | 通过 | `atlas.observability.enabled=false` 和 `otel.sdk.disabled=true` 均关闭 OTel、Metrics、Prometheus 出口，同时保留健康探针 |
| 依赖版本唯一 | 通过 | effective dependency tree 中 OTel API/SDK `1.65.0`、Instrumentation `2.31.1`、Micrometer `1.17.0` 单一收敛 |
| 代码格式和构建 | 通过 | `git diff --check` 通过，Maven reactor 构建通过 |

## 3. 自动化证据

### 3.1 完整模块回归

执行：

```bash
mvn -q -f atlas-richie-component/atlas-richie-observability-parent/pom.xml test
```

结果：

```text
tests=32
errors=0
failures=0
skipped=1
```

唯一跳过项是受 `ATLAS_OBSERVABILITY_LOCAL_E2E=true` 门控的本地后端测试；该测试随后已单独执行并通过。

### 3.2 本地 OTLP 三信号闭环

执行：

```bash
ATLAS_OBSERVABILITY_LOCAL_E2E=true \
mvn -q -f atlas-richie-component/atlas-richie-observability-parent/pom.xml \
  -pl atlas-richie-observability-spring-boot-starter -am \
  -Dtest=Phase2LocalOtlpSmokeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

测试请求返回了同一条请求关联信息：

```text
PHASE2_LOCAL_OTLP_BODY={"span_id":"b23ae1926f8baaa9","trace_id":"65c778be92905647eb7ab89334bd1c82","request_id":"phase2-local-e2e"}
```

随后在本地后端完成了以下查询：

- Tempo：查询 Trace `65c778be92905647eb7ab89334bd1c82`，得到 `GET /phase2/trace` Server Span，服务名为 `atlas-observability-phase2-e2e`；
- Loki：使用 `{service_name="atlas-observability-phase2-e2e"}` 查询到请求完成日志，日志包含相同的 `trace_id` 和 `span_id`；
- Prometheus：使用 `jvm_memory_used_bytes{job="atlas-observability-phase2-e2e"}` 查询到 JVM 内存、GC、Class 和 CPU 相关指标。

Prometheus 查询使用 `job` 是因为当前 Alloy OTLP resource 到 Prometheus 的映射将 `service.name` 映射为 `job`，而不是 `service_name`。这是查询约定，不是应用丢失服务身份；Tempo 和 Loki 仍使用 `service.name`/`service_name` 进行服务过滤。

### 3.3 本地后端健康

执行本地 observability 环境的 `validate.sh`，Prometheus、Loki、Tempo、Pyroscope、Grafana、Alloy readiness、指标/日志/Trace smoke 和 Grafana 数据源检查均通过。

## 4. Phase 2 形成的运行时契约

### 应用接入

应用只需要引入：

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-observability-spring-boot-starter</artifactId>
</dependency>
```

并提供 `spring.application.name`、标准 `otel.*`/`OTEL_*` 配置即可。应用不应自行创建 OTel SDK Provider、Exporter 或 Prometheus Registry。

### 指标归属

- Spring Boot/Micrometer 官方 Binder 负责 JVM、GC、Class、CPU、FD、HTTP 和可识别的标准连接池；
- `ExecutorMetricsBinder` 负责显式注册的线程池；
- `DependencyMetrics` 只提供协议组件复用的低基数门面，不绑定 Redis、gRPC、NATS 或具体数据库客户端；
- 具体协议组件在 Phase 3 注入 `dependency_type`、`target_service`、`operation`、`status`，不得将 request ID、trace ID、用户 ID、完整 URL 或消息内容作为标签。

### 日志归属

- 组件负责 request ID、Trace Context、operation、stage、status、duration 和错误字段的关联及清理；
- 官方 OTel Logback Appender 负责 OTLP Logs 出口；
- stdout JSON Encoder 仍由应用的 Logback 配置选择，组件不覆盖应用已有日志布局；
- 日志和 Span 通过 `trace_id` 关联，Grafana/Tempo/Loki 的跳转由后端配置完成。

## 5. 进入 Phase 3 的前置约束

Phase 3 只做协议组件迁移，不回头改动本阶段已经验收的基础契约：

1. 每个协议组件只依赖 `atlas-richie-observability-core`，不传递 Actuator、Exporter 或完整 Starter；
2. 复用统一 W3C Propagator 和 OTel Context，保持 `trace_id`、`span_id`、`request_id` 三者语义分离；
3. 复用 `DependencyMetrics`，统一请求耗时、状态、连接和重试指标命名；
4. 保留现有业务错误、超时、重试、Ack、Dedup 和降级语义，不以观测埋点吞掉业务异常；
5. 通过真实 HTTP/gRPC/NATS/MCP 请求验证跨服务链路后，才能移除对应组件的旧 tracing 入口；
6. 本阶段不删除基础依赖中的全局 Actuator，也不删除旧 `atlas-richie-tracing`，这些属于后续应用迁移和兼容收敛阶段。

