# Phase 3-gRPC 子阶段验收记录

## 1. 结论

Phase 3 的 gRPC Client/Server 统一观测子阶段已通过组件边界验收，可以开启 NATS/JetStream 子阶段。

本记录关闭的是 gRPC 拦截器到统一 Observability Core 的迁移，不代表 Foundry 应用已经完成 gRPC 跨服务真实环境验收。应用级 Gateway、Orchestrator、下游 gRPC 服务和本地观测后端的联调，仍在所有协议组件完成后，按临时改造方案统一执行。

## 2. 已落地内容

- `GrpcClientTracingInterceptor` 为每次调用创建唯一 `CLIENT` Span，并在 gRPC `Metadata` 中注入标准 W3C `traceparent`/`tracestate`；
- `GrpcServerTracingInterceptor` 从 `Metadata` 提取 W3C Context，创建唯一 `SERVER` Span，并在完成或取消时结束生命周期；
- Client、Server 均恢复 OTel Context 和 `trace_id`、`span_id`、`request_id` MDC；
- `x-request-id` 作为独立业务关联 ID 传播，不再将它伪装成 Trace ID，也不再写入旧的 `x-trace-id` 响应头；
- 通过 `DependencyMetricsRecorder` 记录低基数的 gRPC dependency request 指标，维度为协议、目标、方法和状态；
- `ObservabilityState` 关闭时不创建观测 Span、不注入 Metadata、不记录依赖指标，保留原 gRPC 调用行为；
- gRPC 模块只依赖 `atlas-richie-observability-core`，不传递 Actuator、Exporter 或完整 Observability Starter；
- `GrpcAutoConfiguration` 优先复用 Spring 容器中的统一 `OpenTelemetry`、`DependencyMetricsRecorder` 和 `ObservabilityState`，没有创建第二套 SDK Provider。

## 3. 验收证据

### 3.1 定向传播与生命周期测试

执行：

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-grpc -am \\
  -Dtest=GrpcTracingInterceptorTest \\
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过，`GrpcTracingInterceptorTest` 共 2 项，`errors=0`、`failures=0`。

覆盖：

1. Client W3C Metadata 注入和独立 `x-request-id`；
2. Client `CLIENT` Span 在 gRPC call close 后只结束一次；
3. Server W3C Metadata 提取和父子 Trace ID 连续；
4. Server `SERVER` Span 在完成后结束；
5. Client/Server dependency request 记录；
6. 旧 `x-trace-id` 响应头不再作为新链路协议输出。

### 3.2 gRPC 模块完整回归

执行：

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-grpc -am test
```

结果：通过。gRPC discovery、服务注册配置、既有拦截器和新 tracing 测试均通过。

### 3.3 静态检查

执行：

```bash
git diff --check
```

结果：通过。

## 4. 验收边界与后续补充

本子阶段已关闭 gRPC 组件的统一 Context、Metadata carrier、基础 Client/Server 生命周期和禁用开关边界。真正的 streaming、超时、取消、重试以及 Spring OTel instrumentation 重复 Span，需要在后续真实 gRPC 调用链和重复 boundary 清理阶段继续补充验收；这些项目不能用本次单元测试结果冒充应用级 E2E 证据。

下一阶段 NATS/JetStream 必须单独形成实现、定向测试、模块回归和验收记录，至少覆盖 publish、request、consumer、Ack、重试、DLQ、消息关联 ID 与 W3C Header 传播。未形成 NATS 验收记录前，不得进入 MCP、Worker/Channel 子阶段。
