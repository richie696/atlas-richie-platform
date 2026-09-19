# Phase 3 阶段记录：协议组件迁移

## 1. 阶段目标

Phase 3 将现有协议组件迁移到 Phase 2 已验收的统一观测契约中。协议组件只负责自己的 carrier、客户端生命周期和业务阶段，不创建新的 OTel SDK Provider，不重复实现 Spring Boot Server instrumentation，也不把 Actuator/Exporter 传递给基础库。

当前顺序为：

1. HTTP Client 和 SSE；
2. gRPC Client/Server；
3. NATS/JetStream Producer/Consumer/RPC；
4. MCP transport、Tool、Worker、Channel；
5. 清理重复 boundary instrumentation，并完成 Phase 3 组件总验收。

## 2. HTTP 当前落地

HTTP 子阶段的验收记录见 [Phase 3-HTTP 验收记录](./PHASE_3_HTTP_ACCEPTANCE.md)，gRPC 子阶段的验收记录见 [Phase 3-gRPC 验收记录](./PHASE_3_GRPC_ACCEPTANCE.md)，NATS/JetStream 子阶段的验收记录见 [Phase 3-NATS 验收记录](./PHASE_3_NATS_ACCEPTANCE.md)，MCP 子阶段的验收记录见 [Phase 3-MCP 验收记录](./PHASE_3_MCP_ACCEPTANCE.md)。每份记录通过后，才允许进入下一子阶段；四份记录均通过后，才允许进入 Phase 3 总验收。

### 2.1 统一依赖边界

HTTP core 只增加 `atlas-richie-observability-core`，使用 `DependencyMetricsRecorder` 这一无 Actuator、无 Micrometer、无 exporter 的接口。具体实现由 `atlas-richie-observability-actuator` 在完整应用 Starter 场景提供。

因此 HTTP Provider 不会因为增加观测能力而把完整 Starter、Actuator 或 Prometheus Registry 传递给使用方。

### 2.2 统一观测装饰器

`ObservabilityHttpClient` 作为 Facade/Decorator 包装 JDK、OkHttp、Apache HttpClient 5 和 Spring RestClient 四种 Provider，负责：

- 每次出站请求创建一个 `CLIENT` Span；
- 从当前 OTel Context 注入 W3C `traceparent`/`tracestate`；
- 使用低基数的 `dependency_type=http`、目标 host、HTTP method 和状态记录 `DependencyMetricsRecorder`；
- 覆盖同步、回调异步和 `CompletableFuture` 执行路径；
- 在 Future/回调线程恢复 Span Context，不把 MDC 或 request body 放入指标标签；
- 对 SSE 连接把 Span 生命周期延长到 close、服务端关闭或失败，并记录活动连接数；
- 观测关闭或没有统一 OTel runtime 时直接返回原 Provider，保持旧行为。

### 2.3 验证

已通过：

```bash
mvn -q -f atlas-richie-component/pom.xml \
  -pl atlas-richie-http-parent -am \
  -Dtest=ObservabilityHttpClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -q -f atlas-richie-component/pom.xml \
  -pl atlas-richie-http-parent -am test
```

验证内容包括 Client Span、W3C header 注入、目标 host 和 operation 低基数指标、Future 完成路径、关闭分支，以及 HTTP 四个 Provider 的既有单元测试和 SSE 相关回归。

## 3. gRPC 当前落地

gRPC 子阶段的验收记录见 [Phase 3-gRPC 验收记录](./PHASE_3_GRPC_ACCEPTANCE.md)。该记录已关闭组件边界验收，允许开启 NATS/JetStream；真实 streaming、超时、取消、重试和重复 boundary 的应用级证据仍归入后续真实链路验收，不提前宣称完成。

## 4. NATS/JetStream 当前落地

NATS/JetStream 子阶段的验收记录见 [Phase 3-NATS 验收记录](./PHASE_3_NATS_ACCEPTANCE.md)。该记录已关闭组件边界验收，允许开启 MCP、Worker/Channel；真实 Broker、JetStream 重投/DLQ 和 OTLP 后端证据仍归入最终应用级 E2E。

## 5. MCP、Worker、Channel 当前落地

MCP 子阶段的验收记录见 [Phase 3-MCP 验收记录](./PHASE_3_MCP_ACCEPTANCE.md)。MCP HTTP Client/Server 和 Tool 调度已关闭组件边界验收，允许进入重复 boundary 清理与 Phase 3 总验收；Worker/Channel 真实应用链路仍待 Foundry 迁移阶段补证。

## 6. Phase 3 总验收结果

重复 boundary 子阶段验收记录见 [Phase 3-Boundary 验收记录](./PHASE_3_BOUNDARY_ACCEPTANCE.md)。该记录确认：

- 新 observability starter 由官方 OTel Spring Boot instrumentation 负责 HTTP Server boundary；
- 新运行时存在时，旧 `web-core` 的 `OtelTracingInterceptor` 不再自动装配；
- 旧 `atlas-richie-tracing` 直接依赖仍保持兼容，但其 Spring Boot Starter 不再从组件库传递到下游；
- HTTP、gRPC、NATS、MCP 和统一 observability/web boundary 的汇总回归通过。

Phase 3 总验收记录见 [Phase 3 总验收记录](./PHASE_3_ACCEPTANCE.md)，当前已通过组件阶段验收。

仍待后续 Phase 4 应用迁移和最终 Foundry E2E 补证的内容：

- HTTP、gRPC、NATS、MCP 的真实跨服务请求到 Alloy/Tempo/Loki/Prometheus 查询；
- MCP SSE、Worker、Channel 的真实异步 ACK、中间状态、最终结果、超时、取消和重试；
- Gateway、Orchestrator、MCP、外部工具/Mock、PostgreSQL、Redis、NATS、gRPC 的完整链路；
- 存量应用移除旧 tracing 直接依赖，以及基础依赖全局 Actuator 的最终下沉。

## 7. 约束

Phase 3 完成的是平台组件边界，不等同于存量应用迁移完成。旧 `atlas-richie-tracing` 和基础依赖中的全局 Actuator 仍保留兼容入口；
Phase 4 必须按应用逐个完成真实传播、异常、超时、重试/降级和关闭路径验收后，才能删除对应应用的旧入口。
