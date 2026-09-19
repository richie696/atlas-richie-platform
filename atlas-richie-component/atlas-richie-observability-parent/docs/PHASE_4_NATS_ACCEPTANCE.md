# Phase 4.4 NATS/JetStream 验收记录

## 1. 验收结论

Phase 4.4 的 NATS/JetStream 子阶段通过。

本次验收使用真实 Foundry Gateway、MCP Discovery、MCP Mock 和 Admin 进程，执行了一次
random_uuid 请求。请求从 Gateway 进入，经过 Discovery gRPC、MCP Streamable HTTP，随后由
Gateway 通过 JetStream 发布 mcp.call.record，最后由 Admin 的
McpCallRecordArchiveConsumer 消费并写入两张归档表。

固定请求的结果为成功：

~~~json
{"result":"{\\"type\\":\\"uuid\\",\\"value\\":\\"3be23199-a498-4363-aa69-9e53027d15fd\\"}","success":true}
~~~

对应 Tempo Trace：

~~~text
e72b798f3c97d44a79f1f91279184a72
~~~

这条 Trace 同时包含：

- Gateway HTTP Server：POST /api/mcp/tool-call；
- Gateway 与 Discovery 的 gRPC Client/Server 成对 Span；
- Gateway MCP Client 与 Mock MCP Server Span；
- Gateway NATS Producer：mcp.call.record publish；
- Admin NATS Consumer：mcp.call.record receive；
- Admin 归档写入：INSERT atlas_foundry.mcp_call_record；
- Admin 归档写入：INSERT atlas_foundry.mcp_call_metric。

因此，本子阶段已经证明 NATS 不只是“连接成功”或“发布成功”，而是完成了跨进程发布、消费、
Trace 关联、归档写入以及依赖指标落盘。

## 2. 验收范围

### 2.1 启动边界

Gateway 和 Admin 均使用重建后的 JAR 启动，并显式设置：

~~~text
OTEL_SDK_DISABLED=false
OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_RESOURCE_ATTRIBUTES=service.namespace=foundry,service.environment=local
~~~

Gateway 使用 foundry-mcp-gateway-phase44-nats，Admin 使用
foundry-admin-phase44-nats，两个服务都将三类信号发送到本地 OTLP Collector。

Admin 验收进程额外使用了：

~~~text
--spring.task.scheduling.enabled=false
~~~

该参数只关闭与本次 NATS 消费验收无关的 Admin 定时任务，避免既有定时任务错误路径遮蔽
Web Server 和 JetStream Consumer 验收；NATS 连接、JetStream provision、Consumer 注册、
归档写入均保持真实启用。

### 2.2 NATS/JetStream 配置边界

本地 Nacos 的 platform-nats.yaml 保持：

- NATS server：nats://127.0.0.1:4222；
- JetStream enabled；
- FOUNDRY_EVENTS stream enabled；
- mcp.call.record subject 纳入 FOUNDRY_EVENTS；
- mcp-call-record-archive Consumer enabled。

两个服务启动日志均确认：

~~~text
NATS connection state: DISCONNECTED → CONNECTED
JetStream stream [FOUNDRY_EVENTS] already exists, skipping creation
JetStream consumer [mcp-call-record-archive] ... ensured
~~~

Admin 还确认：

~~~text
JetStream consumer [mcp-call-record-archive] ... started
MCP call record archive consumer registered
~~~

## 3. Trace 验收

查询：

~~~text
GET http://127.0.0.1:3200/api/traces/e72b798f3c97d44a79f1f91279184a72
~~~

Trace 中实际出现以下关键 Span：

| 边界 | Span | 结果 |
|---|---|---|
| Gateway HTTP | POST /api/mcp/tool-call | HTTP 200 |
| Gateway NATS Producer | atlas-richie-nats / mcp.call.record publish | OK |
| Discovery gRPC | ListTools、PrepareInvocation client/server 成对 Span | OK |
| MCP | MCP tools/call client/server | OK |
| Admin NATS Consumer | atlas-richie-nats / mcp.call.record receive | OK |
| Admin JDBC | INSERT atlas_foundry.mcp_call_record | OK |
| Admin JDBC | INSERT atlas_foundry.mcp_call_metric | OK |

Producer 和 Consumer 共享同一条 Trace，Consumer Span 的 parent 关系和 NATS headers 中的
W3C trace context 证明跨进程上下文传播有效。

## 4. 日志验收

Loki 标签查询：

~~~logql
{service_name=~"foundry-.*"} |= "phase44-nats-metrics"
~~~

Gateway 日志实际包含：

~~~text
E2E_STAGE stage=gateway.service.call.start requestId=phase44-nats-metrics ...
E2E_STAGE stage=gateway.service.call.end requestId=phase44-nats-metrics ...
HTTP request completed request_id=phase44-nats-metrics trace_id=e72b798f3c97d44a79f1f91279184a72 ...
~~~

Mock 日志也使用同一 request_id 和 trace_id。Admin 消费端的归档处理以 NATS Consumer
Span 和 JDBC Span 作为结构化链路证据，Consumer 注册和启动日志确认消费者已经真实运行。

本阶段的日志结论是：入口服务和下游 MCP 服务可以按 request_id 反查，跨进程 NATS 消费和
数据库归档则通过同一 Trace 进行定位；后续业务阶段继续补齐各业务处理器的显式
stage/operation/status/duration 日志。

## 5. Actuator 与 Prometheus 指标验收

### 5.1 Gateway Actuator

~~~text
GET http://127.0.0.1:18902/actuator/metrics/dependency.request
~~~

返回 dependency.request，并包含：

~~~text
dependency_type=nats
target_service=mcp.call.record
operation=publish
status=OK
~~~

### 5.2 Admin Actuator

~~~text
GET http://127.0.0.1:8898/actuator/metrics/dependency.request
~~~

返回 dependency.request，并包含：

~~~text
dependency_type=nats
target_service=mcp.call.record
operation=receive
status=OK
~~~

### 5.3 Prometheus

Micrometer Timer 经过 OTLP/Alloy 后的实际 Prometheus 指标名为：

~~~promql
dependency_request_milliseconds_count{dependency_type="nats"}
~~~

查询返回两条结果：

~~~text
service_name=foundry-mcp-gateway-phase44-nats
dependency_type=nats
operation=publish
target_service=mcp.call.record
status=OK

service_name=foundry-admin-phase44-nats
dependency_type=nats
operation=receive
target_service=mcp.call.record
status=OK
~~~

本次验收还修复了一个真实缺陷：统一 Actuator 自动配置此前使用
@ConditionalOnBean(MeterRegistry.class)，在 OTLP MeterRegistry 创建前提前判断，导致
DependencyMetrics 没有实例化。现在改为注入 ObjectProvider<MeterRegistry> 并在记录时延迟
解析 Registry，保证 dependency.request 能同时覆盖 OTLP Registry 的真实运行时创建顺序。

## 6. 组件回归验证

NATS 模块定向测试：

~~~bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-nats -am test
~~~

结果：exit code 0。

Gateway/Admin 使用修复后的 observability starter 完成重新打包，并由本次真实请求验证运行时
行为。Actuator 模块定向测试也通过，且修复后的 starter 已安装到本地 Maven 仓库供 Foundry
运行时加载。

## 7. 验收清单

| 编号 | 验收项 | 状态 | 证据 |
|---|---|---|---|
| NATS-START-001 | Gateway/Admin 连接 NATS | 通过 | 两服务日志均为 CONNECTED |
| NATS-START-002 | JetStream stream/consumer provision | 通过 | FOUNDRY_EVENTS 与 mcp-call-record-archive 均 ensured |
| NATS-REAL-001 | Gateway 真实 MCP 请求成功 | 通过 | success=true，UUID 返回成功 |
| NATS-PROP-001 | NATS Producer Span | 通过 | mcp.call.record publish |
| NATS-PROP-002 | NATS Consumer Span 与 Producer 同 Trace | 通过 | Trace e72b798f3c97d44a79f1f91279184a72 |
| NATS-PROP-003 | W3C/request_id 关联 | 通过 | Trace 共用，Gateway/Mock Loki 共用 request_id |
| NATS-METRIC-001 | Gateway publish dependency.request | 通过 | Gateway Actuator + Prometheus |
| NATS-METRIC-002 | Admin receive dependency.request | 通过 | Admin Actuator + Prometheus |
| NATS-METRIC-003 | Prometheus 落盘 | 通过 | dependency_request_milliseconds_count 返回 publish/receive |
| NATS-DB-001 | Consumer 归档数据库写入 | 通过 | 两条 JDBC INSERT Span |
| NATS-REGRESSION-001 | NATS/Actuator 定向回归 | 通过 | Maven 定向测试 exit code 0 |

## 8. 下一阶段边界

NATS/JetStream 子阶段已通过。下一阶段只能进入 Agent、RAG、LLM、Tool、SSE、Channel、Worker
等业务阶段；在这些业务阶段全部完成并分别验收前，不执行最终 Foundry E2E，也不删除临时方案
文档。

