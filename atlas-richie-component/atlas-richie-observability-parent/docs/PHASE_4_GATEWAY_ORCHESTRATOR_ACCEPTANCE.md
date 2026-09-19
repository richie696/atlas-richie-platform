# Phase 4.3 验收记录：Gateway → Orchestrator 最小真实请求

## 1. 结论

Phase 4.3 已通过。当前 Foundry Gateway 以真实应用进程启动，使用本地测试路由将
`/api/agent/chat` 转发到 Mock Orchestrator，完成了 Gateway Server Span、Gateway outbound
HTTP Client Metrics、request_id/trace_id 日志关联和本地后端查询。

本子阶段验证的是 Gateway 应用边界和跨 HTTP 路由的观测接线；Mock Orchestrator 只承担可控的
下游响应，不代表真实 Agent 编排、MCP、LLM 或 SSE 业务已经验收。真实 Orchestrator 业务链路
留到后续子阶段和最终 Foundry E2E。

## 2. 测试夹具

测试入口：

```bash
OTEL_SDK_DISABLED=false \
OTEL_TRACES_EXPORTER=otlp \
OTEL_METRICS_EXPORTER=otlp \
OTEL_LOGS_EXPORTER=otlp \
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318 \
OTEL_SERVICE_NAME=foundry-gateway-service-phase43 \
POST_REQUEST_WAIT_SECONDS=20 \
bash tests/e2e/observability/test-gateway-orchestrator-e2e.sh
```

测试夹具做了以下边界隔离：

- 本地 Mock Orchestrator 监听 `8899`，只返回 service/path/method；
- Gateway 使用 `9510`；
- 测试进程关闭 Nacos 配置/发现，避免把共享环境的服务实例作为结果来源；
- 通过 `gateway-phase43.yml` 使用 Spring Cloud Gateway 5 的正式配置键
  `spring.cloud.gateway.server.webflux.routes`；
- 只在测试进程关闭 Gateway 内部 JWT 校验，生产 Nacos 配置和生产鉴权代码没有改为匿名；
- Redis 使用本地观测环境的 `127.0.0.1:16379`，确保 Gateway 的真实缓存初始化路径也被覆盖；
- 请求携带 `X-Request-Id: phase43-gateway-orchestrator`；
- 请求完成后等待 OTLP 批量 exporter 发布，再停止进程并查询后端。

## 3. 真实请求验收

测试输出：

```text
PHASE4_3_GATEWAY_ORCHESTRATOR_PASS request_id=phase43-gateway-orchestrator
response={"service": "orchestrator", "path": "/agent/chat", "method": "GET"}
```

这证明：

1. Gateway 在生产配置级别以 WebFlux 启动；
2. `/api/agent/chat` 命中路由；
3. `StripPrefix=1` 生效，下游实际收到 `/agent/chat`；
4. Gateway 到下游的 HTTP Client 观测边界被建立；
5. 业务响应为 HTTP 200。

## 4. 后端证据

### 4.1 Tempo

以 `service.name=foundry-gateway-service-phase43` 查询 Tempo，得到 Gateway Trace，例如：

```text
trace_id=575486ea9eb28093cb8e6ada983d8e33 root=GET
```

同一服务同时得到两个健康检查 Trace，说明服务启动和业务请求均进入 Trace 后端。

### 4.2 Loki

以 `{service_name="foundry-gateway-service-phase43"} |= "phase43-gateway-orchestrator"` 查询，
得到业务请求日志：

```text
stage=gateway.request.end operation=route_request
requestId=phase43-gateway-orchestrator
traceId=1ca07f2cb04485e5744217b2ed6b72b0
method=GET path=/api/agent/chat status=200 signal=onComplete durationMs=50
```

同一请求也有对应的 `stage=gateway.request.start` 日志，因此 Gateway 侧具备开始、完成、状态、
耗时和 Trace 关联。

### 4.3 Prometheus

以 `job="foundry-gateway-service-phase43"` 查询得到：

- `jvm_memory_used_bytes`：6 条 JVM 堆/非堆样本；
- `http_client_requests_milliseconds_count`：值为 `1`；
- HTTP Client 指标标签包含：
  `spring_cloud_gateway_route_id="phase43-orchestrator"`、
  `spring_cloud_gateway_route_uri="http://127.0.0.1:8899"`、
  `http_status_code="200"`、`error="none"`；
- Gateway HTTP Server 指标同时写入 OTLP Metrics，指标族为
  `http_server_requests_milliseconds_*`。

这证明 Metrics 不只是启动时的 JVM 静态数据，也包含本次真实路由的 outbound 请求统计。

## 5. 本子阶段发现并修复的问题

### 5.1 OTLP Registry 与 Prometheus Registry 冲突

开启统一 starter 后，Gateway 同时存在 Prometheus Registry 和 OTLP Registry，Spring Boot
Observation 自动配置因存在两个 `MeterRegistry` 无法启动。`ObservabilityOtlpMetricsAutoConfiguration`
现将 OTLP Registry 标为 `@Primary`：

- 业务 Observation 以 OTLP 为主路径；
- Prometheus Registry 继续保留 Actuator 兼容端点；
- 关闭 OTel SDK 时 OTLP Registry 不创建，不改变关闭态行为。

### 5.2 Gateway WebFlux/Servlet 自动判定冲突

Gateway 依赖链同时包含 WebFlux 和 springdoc WebMVC 类，Boot 首次启动判定为 Servlet，但没有
Servlet WebServerFactory，启动失败。已在 Gateway `bootstrap.yml` 显式声明：

```yaml
spring:
  main:
    web-application-type: reactive
```

这属于当前应用真实启动配置修复，测试脚本不再通过临时环境变量注入 reactive 类型。

### 5.3 Spring Cloud Gateway 5 路由配置键

本地测试使用 `spring.cloud.gateway.server.webflux.routes`，避免使用旧的
`spring.cloud.gateway.routes` 造成本地测试路由未加载。生产 Nacos 配置仍由 Gateway 的迁移兼容
机制读取，后续配置治理应统一改为新键并单独验收。

## 6. 验收清单

| 编号 | 项目 | 状态 | 证据 |
|---|---|---|---|
| GW-START-001 | Gateway 无临时 reactive 环境变量可启动 | 通过 | bootstrap.yml 显式 reactive，脚本通过 |
| GW-START-002 | 统一 starter 三 signal 开启 | 通过 | 启动诊断 `enabled=true`，三 exporter=otlp |
| GW-ROUTE-001 | `/api/agent/chat` 命中 Orchestrator 路由 | 通过 | 返回 service=orchestrator |
| GW-ROUTE-002 | StripPrefix 后下游路径正确 | 通过 | 下游收到 `/agent/chat` |
| GW-TRACE-001 | Gateway Trace 进入 Tempo | 通过 | Tempo service.name 查询 |
| GW-LOG-001 | request_id 与 trace_id 进入 Loki | 通过 | start/end 日志查询 |
| GW-METRIC-001 | Gateway outbound HTTP 指标进入 Prometheus | 通过 | route_id、URI、200、error=none |
| GW-METRIC-002 | JVM 指标进入 Prometheus | 通过 | 6 条 jvm_memory_used_bytes |
| GW-REGISTRY-001 | OTLP/Prometheus Registry 不阻断启动 | 通过 | `@Primary` 修复后真实启动通过 |

## 7. 下一阶段边界

Phase 4.4 继续验证真实应用协议传播：HTTP Client/SSE、gRPC、NATS/JetStream、MCP transport，
以及 Gateway/Orchestrator/MCP 之间的 Trace、request_id、Logs、Metrics 关联。未通过 Phase 4.4
前，不进入 Agent/RAG/LLM/Tool 业务 Span 阶段。

