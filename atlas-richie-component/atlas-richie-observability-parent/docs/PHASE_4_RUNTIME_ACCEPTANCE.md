# Phase 4.2 验收记录：Foundry 服务运行时与总开关

## 1. 结论

Phase 4.2 已通过。使用当前 Foundry 的 `foundry-mcp-mock-service` 作为最小真实应用，分别以
`OTEL_SDK_DISABLED=true` 和 `OTEL_SDK_DISABLED=false` 启动，完成了启动诊断、健康检查、业务请求、
Actuator/JVM 指标、OTLP 后端查询、Trace-to-Logs/Trace-to-Metrics 关联以及进程停止验收。

本记录只关闭“单服务运行时与总开关”子阶段，不代表 Gateway -> Orchestrator 或所有业务异步链路已经
关闭；后续阶段仍须使用真实 Foundry 服务逐条验收。

## 2. 验收对象与环境

- 应用：`foundry-mcp-mock-service`；
- 本地端口：`18902`（开启态数据验收）、`18904`（最新开启态关闭验收）；
- 观测后端：本地 Alloy OTLP Receiver、Tempo、Loki、Prometheus；
- 应用入口：唯一的 `atlas-richie-observability-spring-boot-starter`；
- 验收请求：`GET /mock/stock/query?storeId=A001`，携带 `X-Request-Id`；
- 本地后端容器由 `/Users/richie696/Development/docker-scripts/observability/` 提供。

## 3. 开启态验收

### 3.1 启动与配置

启动时使用标准 OTel 环境变量：

```bash
OTEL_SDK_DISABLED=false \
OTEL_TRACES_EXPORTER=otlp \
OTEL_METRICS_EXPORTER=otlp \
OTEL_LOGS_EXPORTER=otlp \
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318 \
OTEL_SERVICE_NAME=foundry-mcp-mock-server-final \
SERVER_PORT=18902 \
java -jar foundry-mcp-mock-service/target/foundry-mcp-mock-service-1.0.0-SNAPSHOT.jar
```

启动日志确认：

- `enabled=true`；
- `sdkDisabled=false`；
- `openTelemetryBean=true`；
- `exporters={traces=otlp, metrics=otlp, logs=otlp}`；
- `resource.service.name=foundry-mcp-mock-server-final`；
- Micrometer OTLP Registry 向 `http://127.0.0.1:4318/v1/metrics` 发布；
- 未出现第二个手工启动的 Micrometer publisher。

### 3.2 应用端点与业务请求

- `/actuator/health`：HTTP 200，状态 `UP`；
- `/actuator/metrics`：HTTP 200；
- `/actuator/metrics/jvm.memory.used`：HTTP 200；
- `/actuator/prometheus`：HTTP 200；
- `/mock/stock/query?storeId=A001`：HTTP 200，返回 A001 库存业务数据。

### 3.3 后端数据

使用本地后端 API 查询得到以下证据：

- Prometheus 存在 `job="foundry-mcp-mock-server-final"` 的 JVM 指标；
- Prometheus 存在 `http_server_request_duration_seconds_bucket/count/sum`；
- `http_server_request_duration_seconds_count` 查询到
  `http_route="/mock/stock/query"`、HTTP 200 的请求样本；
- Tempo 查询到根 Trace：`GET /mock/stock/query`；
- Loki 查询到同一请求的结构化日志：
  `HTTP request completed request_id=phase4-request-body trace_id=... span_id=...`；
- 因此本次请求具备 `request_id -> trace_id -> span_id -> Logs/Metrics/Trace` 的关联证据。

## 4. 关闭态验收

使用同一应用和同一观测后端，以 `OTEL_SDK_DISABLED=true` 启动：

- 启动诊断确认 `enabled=false`、`configuredEnabled=true`、`sdkDisabled=true`；
- `exporters={traces=none, metrics=none, logs=none}`；
- `/actuator/health`：HTTP 200，应用仍可提供健康检查；
- `/actuator/metrics`：HTTP 200，但 `names=[]`；
- `/actuator/metrics/jvm.memory.used`：HTTP 404；
- `/actuator/prometheus`：HTTP 404；
- 业务应用仍可以启动并提供请求，但没有 OTel Trace、Metrics、Logs 导出；
- 停止后端端口确认已释放。

这证明一个配置 `OTEL_SDK_DISABLED=true` 可以整体关闭三类 OTel signal，同时不阻断应用业务启动。

## 5. 代码与实现验收

### 5.1 JVM/Micrometer OTLP

`observability-actuator` 增加 Micrometer OTLP Registry 的自动配置，复用标准
`otel.metrics.exporter`、`otel.exporter.otlp.metrics.endpoint`、`otel.resource.attributes` 和
`management.otlp.metrics.export.step` 配置，不要求业务服务自己创建 Registry 或手动启动 publisher。

Prometheus Registry 仍保留为 Actuator 兼容端点，但不再由 Foundry 业务 POM 单独声明；OTLP Registry
负责进入统一观测后端。

### 5.2 日志关联

Logback 自动配置会递归处理 Root Logger 和已注册 Logger 的官方 OTel Appender，显式开启
`trace_id`、`span_id`、`request_id`、`operation`、`stage`、`status`、`duration_ms` 和错误字段的
MDC 捕获。同时，HTTP 完成日志保留显式 `request_id/trace_id/span_id` 字段，避免后端解析 MDC
上下文时丢失关联信息。

### 5.3 关闭路径

统一自动配置在 `ContextClosedEvent` 请求官方 OTel SDK 的 Trace、Metrics、Logs provider flush，
然后继续由官方 Spring Boot Starter 管理 provider/exporter 的最终 shutdown。最新开启态服务已完成
优雅停止并释放端口；本地 exporter 无远端确认时，flush 仍属于尽力而为，不把本地进程关闭误判成后端
数据已持久化的证明。

## 6. 验收清单

| 编号 | 项目 | 状态 | 证据 |
|---|---|---|---|
| APP-CFG-001 | 仅使用统一 starter | 通过 | Foundry 全仓 POM 静态检查、Phase 4.1 |
| APP-START-001 | 开启态启动诊断 | 通过 | `enabled=true`、三 signal 为 `otlp` |
| APP-START-002 | 关闭态启动诊断 | 通过 | `enabled=false`、三 signal 为 `none` |
| APP-TOGGLE-001 | 单配置关闭三 signal | 通过 | 关闭态无 JVM/Prometheus 数据且业务可启动 |
| APP-RES-001 | service.name 进入后端 | 通过 | Prometheus job、Loki service_name、Tempo 查询 |
| APP-TRACE-001 | 真实 HTTP Trace | 通过 | Tempo 根 Trace `GET /mock/stock/query` |
| APP-LOG-001 | 日志带 request/trace/span | 通过 | Loki 结构化完成日志 |
| APP-METRIC-001 | JVM 与 HTTP 指标 | 通过 | Prometheus JVM、HTTP duration count |
| APP-CORR-001 | Trace-to-Logs/Metrics | 通过 | 同一服务与请求路由查询关联 |
| APP-SHUT-001 | 开启/关闭态停止 | 通过 | 进程退出，`18904` 端口释放 |

## 7. 未在本子阶段关闭的范围

- Gateway -> Orchestrator 的真实跨进程请求；
- gRPC、NATS/JetStream、MCP、SSE、Channel、Worker 的应用级真实传播；
- Agent、RAG、LLM、Tool 的业务 Span；
- 失败、超时、重试、取消、脱敏和重复 Span 的最终 Foundry 验收；
- 全部平台阶段结束后的最终完整 E2E。

