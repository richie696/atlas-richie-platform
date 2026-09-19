# Phase 4.4 验收记录：HTTP / gRPC / MCP 真实协议传播

## 1. 结论

Phase 4.4 的 HTTP、gRPC、MCP 协议传播子阶段已通过。当前验收覆盖真实运行的
Foundry MCP Gateway、MCP Discovery 和 MCP Mock 三个进程；NATS/JetStream、Agent/RAG/LLM/Tool
业务 Span、失败与重试矩阵仍属于后续子阶段，不能据此宣称 Phase 4 总体完成。

## 2. 运行边界

- Gateway HTTP：`127.0.0.1:18902`。
- Discovery HTTP/gRPC：`127.0.0.1:8081/9551`。
- MCP Mock HTTP：`127.0.0.1:8901`。
- OTLP HTTP：`http://127.0.0.1:4318`。
- Tempo：`127.0.0.1:3200`；Loki：`127.0.0.1:3100`；Prometheus：`127.0.0.1:9090`。
- 三个进程使用低内存参数：Mock `-Xmx512m`，Gateway/Discovery `-Xmx768m`。

Mock 不读取 Nacos 共享配置，因此必须显式设置全部标准信号配置：

```bash
OTEL_SDK_DISABLED=false
OTEL_TRACES_EXPORTER=otlp
OTEL_METRICS_EXPORTER=otlp
OTEL_LOGS_EXPORTER=otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318
```

这次启动诊断最终确认：`enabled=true`、`sdkDisabled=false`、三类 exporter 均为 `otlp`。

## 3. 真实协议请求

平台定向测试：

```bash
mvn -q -f atlas-richie-component/pom.xml -pl atlas-richie-mcp-parent/atlas-richie-mcp-transport-http,atlas-richie-mcp-parent/atlas-richie-mcp-server-spring-boot-starter,atlas-richie-mcp-parent/atlas-richie-mcp-client-spring-boot-starter -am test
mvn -q -f atlas-richie-component/pom.xml -pl atlas-richie-grpc -am test
```

真实 MCP E2E：

```text
MCP E2E PASS: tools=15 server=mock-store-static call=true
```

固定调用：

```text
request_id=phase44-mcp-grpc-propagation-3
response={"result":"{\"type\":\"uuid\",\"value\":\"120d5ff2-406d-4b76-a718-5d21a5b789d9\"}","success":true}
```

## 4. Tempo 证据

Loki 根据固定 request_id 查到 trace：

```text
trace_id=c3260d983beee304a077c8fe9441d772
```

Tempo `GET /api/traces/c3260d983beee304a077c8fe9441d772` 返回同一条 trace，包含以下服务和边界：

### 4.1 Gateway

- `POST /api/mcp/tool-call`，HTTP Server，状态 200；
- `ai.atlasfoundry.mcp.discovery.v1.McpDiscoveryService/ListTools`，gRPC Client，状态 OK；
- `ai.atlasfoundry.mcp.discovery.v1.McpDiscoveryService/PrepareInvocation`，gRPC Client，状态 OK；
- `MCP tools/call`，`atlas-richie-mcp-client`，MCP Client，`mcp.transport=http`，状态 OK。

### 4.2 Discovery

- `ListTools`，gRPC Server，状态 OK；
- `PrepareInvocation`，gRPC Server，状态 OK；
- gRPC server span 的 `request_id` 为
  `phase44-mcp-grpc-propagation-3`。

### 4.3 Mock

- `POST /mcp`，HTTP Server，状态 200；
- `MCP tools/call`，`atlas-richie-mcp-server`，MCP Server，工具名 `random_uuid`，状态 OK；
- Mock span 与 Gateway/Discovery 使用同一 trace_id，证明 W3C HTTP propagation 和 MCP 自定义
  propagation 均生效。

## 5. Loki 证据

查询：

```logql
{service_name=~"foundry-mcp-(gateway|discovery|mock)-phase44"}
  |= "phase44-mcp-grpc-propagation-3"
```

得到：

- Gateway：`stage=gateway.service.call.start/end`、`stage=gateway.connector.start/end`，并输出
  `HTTP request completed request_id=... trace_id=c3260d983beee304a077c8fe9441d772`；
- Discovery：`stage=discovery.prepare.start/end traceId=phase44-mcp-grpc-propagation-3`；
- Mock：`HTTP request completed request_id=phase44-mcp-grpc-propagation-3 trace_id=c3260d983beee304a077c8fe9441d772`。

日志具备 request_id、trace_id、stage、状态/结果和耗时字段，且三服务可以按同一 request_id
反查。

## 6. Prometheus 证据

已查询到以下真实指标：

- Gateway `http_server_requests_milliseconds_count`：`POST /api/mcp/tool-call`，HTTP 200；
- Gateway `mcp_streamable_calls_total`：`serverName=smp-static-001`，`result=success`；
- Gateway `grpc_client_milliseconds_count`：`ListTools`、`PrepareInvocation`，`grpc_status_code=OK`；
- Discovery `grpc_server_milliseconds_count`：`ListTools`、`PrepareInvocation`，`grpc_status_code=OK`；
- Mock `http_server_requests_milliseconds_count`：`POST /mcp`，HTTP 200。

这证明当前协议链路不只是 Trace 可见，服务端、客户端和业务协议的 RED 指标也已进入
Prometheus 查询面。

## 7. 验收清单

| 编号 | 验收项 | 状态 | 证据 |
|---|---|---|---|
| PROTOCOL-START-001 | Gateway、Discovery、Mock readiness 通过 | 通过 | 三个 `/actuator/health` 均返回 `UP` |
| PROTOCOL-START-002 | 单开关可开启完整信号 | 通过 | Mock 启动诊断 `sdkDisabled=false`，traces/metrics/logs=otlp |
| PROTOCOL-MCP-001 | MCP tools/list 与 tools/call 真实成功 | 通过 | `tools=15`、`call=true` |
| PROTOCOL-HTTP-001 | Gateway HTTP Server 和 Mock HTTP Server 同链路 | 通过 | Tempo 同一 trace，状态均为 200 |
| PROTOCOL-GRPC-001 | Gateway gRPC Client 与 Discovery gRPC Server 同链路 | 通过 | Tempo 成对 span，Prometheus client/server 指标均为 OK |
| PROTOCOL-MCP-002 | MCP client/server 自定义 Span 同链路 | 通过 | `atlas-richie-mcp-client/server` 均存在，工具 `random_uuid` 成功 |
| PROTOCOL-CORR-001 | request_id、trace_id 贯穿三服务日志 | 通过 | Loki 三服务同 request_id/trace_id |
| PROTOCOL-METRIC-001 | HTTP/MCP/gRPC 指标进入 Prometheus | 通过 | HTTP、MCP、gRPC 指标查询成功 |
| PROTOCOL-REGRESSION-001 | MCP 和 gRPC 定向测试通过 | 通过 | 两组 Maven 定向测试 exit code 0 |

## 8. 下一阶段边界

本记录只关闭 HTTP、gRPC、MCP 子阶段。下一阶段必须单独验收 NATS/JetStream 真实发布、消费、
传播、日志和指标；通过后才能继续 Agent/RAG/LLM/Tool/SSE/Channel/Worker 业务阶段。
