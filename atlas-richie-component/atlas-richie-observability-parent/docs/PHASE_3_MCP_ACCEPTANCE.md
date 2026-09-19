# Phase 3-MCP、Worker、Channel 子阶段验收记录

## 1. 结论

当前 MCP transport、Tool 调度、HTTP Client/Server 观测子阶段已通过组件边界验收，可以进入 Phase 3 的重复 boundary 清理与总验收。Worker/Channel 的应用领域实现仍需在 Foundry 应用迁移阶段按实际调用路径补充，不把本记录扩大解释为业务 Worker/Channel 已完成。

本记录关闭的是 MCP HTTP 传输与 Tool 调度边界到统一 Observability Core 的迁移，不代表 Foundry 的 Gateway -> Orchestrator -> MCP -> 外部工具真实链路已完成。最终真实请求、日志、Trace、Metrics 和总开关验收统一在所有平台阶段完成后执行。

## 2. 已落地内容

- MCP HTTP Client 每次 JSON-RPC 请求创建唯一 `CLIENT` Span，记录 `mcp.method`、`mcp.request.id`、稳定的 `mcp.tool.name`、传输类型和目标 host；
- MCP HTTP Server Endpoint 每次请求创建唯一 `SERVER` Span，提取 W3C Context，并用 `x-request-id` 恢复独立业务 request_id；
- Client/Server 均使用统一 W3C Propagator 注入/提取 `traceparent`/`tracestate`，不手写自定义 Trace ID Header；
- `McpHttpOperations` 的 `CompletableFuture.supplyAsync` 恢复调用方 OTel Context，避免异步线程丢失父 Span 和 request_id；
- `tools/call` 以稳定工具名写入 Span 属性，不把工具参数、Prompt、Token、Authorization、Cookie 或完整返回内容写入监控指标/Span 属性；
- `DependencyMetricsRecorder` 记录 MCP Client 出站和 Server 入站请求的低基数指标，状态、方法和耗时随边界 Span 完成；
- `ObservabilityState` 关闭时不创建 Span、不传播 W3C/request_id、不记录依赖指标，协议处理和业务错误返回语义保持原状；
- MCP transport 只依赖 `atlas-richie-observability-core`，没有把 Actuator、Exporter 或完整 Starter 传递到协议内核；
- 原有 MCP 构造器保留兼容重载，Spring Boot 自动配置优先复用统一 `OpenTelemetry`、`DependencyMetricsRecorder` 和 `ObservabilityState`。

## 3. 验收证据

### 3.1 MCP 定向观测测试

执行：

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-mcp-parent/atlas-richie-mcp-transport-http -am \\
  -Dtest=McpObservabilityTest \\
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过，覆盖：

1. Client W3C Header 注入和 request_id 传播；
2. Server W3C Context 提取和父子 Trace 连续；
3. MCP Client/Server method、request id、tool name Span 属性；
4. Client/Server dependency request 指标；
5. MCP Server JSON-RPC 正常响应路径。

### 3.2 MCP parent 回归

第一次完整 MCP parent 回归通过，Surefire 汇总为 `tests=542`、`errors=0`、`failures=0`、`skipped=0`。随后在补充异步 Context 恢复和 MCP 请求属性后，使用 component 根 reactor 重新执行了包含 observability parent、MCP transport、MCP client starter、MCP server starter 及其依赖的回归，命令退出码为 0。

正式构建命令：

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-observability-parent,\\
atlas-richie-mcp-parent/atlas-richie-mcp-transport-http,\\
atlas-richie-mcp-parent/atlas-richie-mcp-client-spring-boot-starter,\\
atlas-richie-mcp-parent/atlas-richie-mcp-server-spring-boot-starter \\
  -am test
```

这里必须使用 component 根 reactor，因为 MCP parent 单独构建时无法看到工作树中尚未发布的 `atlas-richie-observability-core:1.0.0-SNAPSHOT`。

### 3.3 静态检查

执行：

```bash
git diff --check
```

结果：通过。

## 4. 尚未关闭的最终边界

下列项目不由本组件边界验收冒充完成，统一留到 Phase 3 总验收和 Phase 4 Foundry E2E：

- MCP SSE/订阅长连接的 Span 结束时机与连接关闭指标；
- Tool/Worker/Channel 真实异步 ACK、中间状态、最终结果、超时、取消和重试；
- HTTP、gRPC、NATS、MCP 多层 instrumentation 的重复 Server/Client Span 清理；
- Gateway、Orchestrator、MCP、外部工具/Mock、PostgreSQL、Redis、NATS、gRPC 的真实跨服务 Trace-to-Logs/ Metrics 查询。

这些是后续真实链路证据，不改变本次 MCP 组件边界验收通过的结论。
