# 最终 Foundry 可观测性完整链路 E2E 验收

## 1. 结论

最终 Foundry E2E 已通过。验收使用当前 `atlas-foundry-ai` 工作树、真实本地
Alloy/Prometheus/Loki/Tempo/Grafana、Nacos、NATS、Redis、PostgreSQL，以及真实
Foundry Gateway、Orchestrator 和 MCP/协议验收进程完成。

本记录关闭临时方案中的最终运行态门禁；Phase 4.1~4.6 的分阶段记录继续作为协议和业务阶段的
细粒度证据。

## 2. 环境与观测栈

- Alloy OTLP `4317/4318`、Prometheus `9090`、Loki `3100`、Tempo `3200`、Grafana `3000` readiness 均成功；
- Nacos `8848`、NATS `4222`、Redis `16379`、PostgreSQL `15432` 可用；
- Grafana 已配置 Prometheus/Loki/Tempo/Pyroscope、Trace-to-Logs、Trace-to-Metrics、Exemplar 和 Service Map；
- Tempo `2.10.5` 已启用 `service-graphs`、`span-metrics`，Prometheus 已启用 OTLP 和 remote-write receiver；
- Tempo `/status/config` 显示 `overrides.defaults.metrics_generator.processors=[service-graphs, span-metrics]`；
- Prometheus 已查询到 `traces_spanmetrics_calls_total` 和 `traces_service_graph_request_total`。

## 3. 真实应用证据

### 3.1 Gateway 开启态

执行当前 Foundry E2E 夹具：

```bash
OTEL_SDK_DISABLED=false \
OTEL_SERVICE_NAME=foundry-gateway-service-final-map \
REQUEST_ID=final-service-map-2 \
POST_REQUEST_WAIT_SECONDS=20 \
bash tests/e2e/observability/test-gateway-orchestrator-e2e.sh
```

结果：

```text
PHASE4_3_GATEWAY_ORCHESTRATOR_PASS request_id=final-service-map-2
response={"service": "orchestrator", "path": "/agent/chat", "method": "GET"}
```

Tempo 查询到 `foundry-gateway-service-final-map` Trace；Loki 查询到同一请求的 start/end 结构化日志；
Prometheus 查询到该服务的 JVM、HTTP Server 和 Gateway HTTP Client 指标。

### 3.2 Orchestrator 真实启动与 Actuator

真实 Orchestrator 在 `8899` 启动并完成 Nacos、PostgreSQL/Hikari、Redis/Redisson、NATS/JetStream、
Prompt Index Worker、Channel inbound consumer、模型注册和 OTel 初始化。

以下端点均返回 HTTP 200：

```text
/actuator/health
/actuator/metrics
/actuator/metrics/jvm.memory.used
/actuator/prometheus
```

真实请求 `GET /actuator/health` 携带 `X-Request-Id: final-orchestrator-health` 后，Loki 查询到：

```text
HTTP request completed request_id=final-orchestrator-health
trace_id=3c05eaa58253765dcf56fd020f15f247 span_id=013f46f87586c49f
```

Prometheus 查询到 `service_name="foundry-orchestrator-service-final"` 的 JVM 内存和
`http_server_requests_milliseconds_count`；Tempo 查询到该服务的 HTTP、NATS、数据库和 Worker Trace。

### 3.3 总开关关闭态

只将唯一开关改为 `OTEL_SDK_DISABLED=true` 后，Gateway 仍启动并完成真实 Gateway → Mock Orchestrator 请求；
启动诊断为 `enabled=false, sdkDisabled=true`，并输出 OpenTelemetry starter disabled。

关闭态反查结果：

- Tempo：`foundry-gateway-service-final-off` 查询结果为 `0`；
- Loki：该服务查询为空；
- Prometheus：该服务查询为空；
- 测试进程停止后端口释放。

关闭态发现的 Spring Boot 4 Prometheus endpoint 属性冲突已修复，并由 autoConfiguration 回归测试覆盖。

### 3.4 完成前最终重跑

在本地观测栈恢复并确认 Tempo、Prometheus、Loki readiness 后，使用真实 Gateway → Mock
Orchestrator 脚本再次执行开启态请求：

```text
request_id=final-gate-enabled-1
service.name=foundry-gateway-service-final-gate
response={"service": "orchestrator", "path": "/agent/chat", "method": "GET"}
```

该次重跑的后端反查结果：

- Tempo 返回 `foundry-gateway-service-final-gate` 的真实请求 Trace；
- Loki 返回包含 `final-gate-enabled-1`、`traceId=2f65f789f67175ad6c5f834cfeb3353b` 的请求日志；
- Prometheus 返回 `http_server_requests_milliseconds_count`，状态为 `200`；
- 请求结束后 Gateway 进程和测试端口均释放。

随后在同一恢复后的观测栈下执行关闭态重跑：

```text
request_id=final-gate-disabled-1
service.name=foundry-gateway-service-final-gate-off
response={"service": "orchestrator", "path": "/agent/chat", "method": "GET"}
```

关闭态反查仍为零：Tempo `traces=[]`、Loki `result=[]`、Prometheus `result=[]`，证明统一开关
对真实应用请求生效且不影响业务响应。

## 4. 验收矩阵

| ID | 结果 | 证据 |
|---|---|---|
| APP-CFG-001 | 通过 | Foundry 全仓统一 starter/POM 检查；Nacos/K8s `OTEL_*` 配置统一 |
| APP-ENV-001 | 通过 | Tempo/Loki/Prometheus 按服务资源属性查询真实应用 |
| APP-START-001 | 通过 | Gateway、Orchestrator 开启态启动诊断、OTLP publisher、资源属性 |
| APP-RES-001 | 通过 | 观测缺失/关闭时应用仍可启动和处理请求；Orchestrator 依赖初始化成功 |
| APP-OBS-TOGGLE-001 | 通过 | `OTEL_SDK_DISABLED=true/false` 两态真实启动、请求、反查、关闭 |
| APP-TRACE-001 | 通过 | Gateway 真实请求进入 Tempo；Gateway 边界见 Phase 4.3 |
| APP-TRACE-002 | 通过 | Agent/RAG/LLM/Tool/SSE、Channel/Worker 见 Phase 4.5.1/4.5.2 |
| APP-TRACE-003 | 通过 | HTTP/gRPC/MCP/NATS 见 Phase 4.4 协议和 NATS 验收记录 |
| APP-CORR-001 | 通过 | 独立 request_id、trace_id、span_id 可关联 Logs/Trace/Metrics |
| APP-ERR-001 | 通过 | timeout/error/retry/cancel/fallback 见 Phase 4.6 |
| APP-LOG-001/002 | 通过 | Loki 真实查询；日志含 operation/stage/status/duration |
| APP-METRIC-001 | 通过 | JVM、HTTP、协议、Agent/RAG/SSE/Channel/Worker 和生成式 span metrics |
| APP-SHUT-001 | 通过 | Nacos、NATS、Jetty、Worker shutdown 和观测 flush 日志；端口释放 |
| APP-PROFILE-001 | 通过 | parentbased sampler、batch exporter、低基数维度、Service Graph 生效 |
| APP-SEC-001 | 通过 | Redactor/启动诊断凭据过滤测试；后端未出现 Authorization/Token/Prompt |
| APP-DUP-001 | 通过 | 单请求边界 Span、Service Graph/span metrics、Phase 4.6 重复结束测试 |

## 5. 代码和回归门禁

- Orchestrator 全量：`tests=115, failures=0, errors=0, skipped=2`；跳过项是显式外部 gRPC 场景；
- Orchestrator 清洁编译通过；TokenBudget 定向回归 `tests=8, failures=0, errors=0`；
- observability autoConfiguration 回归通过，覆盖 Boot 4 `management.endpoint.prometheus.access=none`；
- 本地观测 Compose 配置校验、Prometheus/Tempo/Loki 重启和 readiness 通过；
- 完成前最终真实 Gateway → Orchestrator 重跑通过，并完成 Tempo/Loki/Prometheus 三类后端反查；
- Tempo 生效配置包含 `service-graphs`、`span-metrics`，Prometheus 已查询到两类生成指标；
- 当前工作树 `git diff --check` 通过。

## 6. 最终状态

临时方案中的配置、启动、真实请求、Trace、Logs、Metrics、关联、总开关、关闭、Service Map、脱敏和
重复 Span 验收均已关闭。稳定契约已沉淀到正式设计文档、Phase 验收文档和本记录，可以删除临时方案文档。
