# Phase 3-NATS/JetStream 子阶段验收记录

## 1. 结论

Phase 3 的 NATS/JetStream 统一观测子阶段已通过组件边界验收，可以开启 MCP transport、Tool、Worker、Channel 子阶段。

本记录关闭的是 NATS 组件到统一 Observability Core 的迁移，不代表 Foundry 已经完成真实 NATS Broker、JetStream 重投和完整跨服务观测后端验收。真实应用级验证统一留到所有协议组件完成后，按临时改造方案执行。

## 2. 已落地内容

- `OpenTelemetryNatsTracingSupport` 统一复用注入的 `OpenTelemetry` 和 W3C Propagator，不再在 NATS 组件内读取另一套全局 Provider/Propagator；
- Producer、Consumer、Client、Server 四种消息角色分别创建对应 Span，并在成功/失败路径结束；
- NATS Headers 传播 W3C `traceparent`/`tracestate`，同时用 `x-request-id` 传播独立业务请求 ID；
- 消费管道使用统一 Context 承载 `trace_id`、`span_id`、`request_id`，在 dispatcher 线程执行期间恢复，并在消息处理结束后清理 MDC；
- `DependencyMetricsRecorder` 记录 publish、receive、request、handle 的低基数依赖请求指标，状态和耗时随 Span 生命周期完成；
- Span 属性保留 subject、operation、message id 和 request id，消息正文、Authorization、Cookie、Prompt 等敏感内容不进入 Span 或指标；
- `ObservabilityState` 关闭时不创建有效 Span、不注入 W3C/request_id、不记录依赖指标，NATS 原有业务路径继续工作；
- Core NATS、RPC Endpoint、JetStream publish/consume 复用原有 Bus、Ack、Nak、重试、DLQ 业务语义，不将观测代码改造成新的消息处理实现；
- NATS 模块只依赖 `atlas-richie-observability-core`，不传递 Actuator、Exporter 或完整 Observability Starter；
- 原有 `NatsTracingSupport` 构造方式保持兼容，自定义实现可继续使用；新的消息处理 Context 通过接口默认方法扩展，不强制破坏已有实现。

## 3. 验收证据

### 3.1 定向观测测试

执行：

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-nats -am \\
  -Dtest=OpenTelemetryNatsTracingSupportTest,TracingMessageDecoratorTest \\
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。

覆盖：

1. Producer W3C Header 注入；
2. Consumer W3C Header 提取和父子 Trace 连续；
3. `x-request-id` 的消息 Header 与 Observability Context 恢复；
4. Producer/Consumer Span 和 dependency request 指标生命周期；
5. `ObservabilityState.disabledState()` 的总开关语义；
6. 现有消费管道成功、异常、空 Header 和 finish 清理行为。

### 3.2 NATS 模块完整回归

执行：

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-nats -am test
```

结果：通过，Surefire 汇总为 `tests=75`、`errors=0`、`failures=0`、`skipped=0`。既有 NATS 策略、Pipeline、Bus、配置和消息测试与新观测测试全部通过。

本次证据是组件/模块边界证据；Docker Broker、JetStream 实际 Ack/Nak/重投、DLQ 和真实 OTLP 后端查询仍需在最终 Foundry E2E 中验证，不能由本次 Maven 回归替代。

### 3.3 静态检查

执行：

```bash
git diff --check
```

结果：通过。

## 4. 下一阶段门禁

只有本记录成立后，才开启 MCP transport、Tool、Worker、Channel 子阶段。下一阶段必须单独验证：

1. MCP HTTP/WebSocket transport 的 Context 传播；
2. tool.call、worker、channel 的 Span/日志/指标关联；
3. 参数、Prompt、Token、Authorization 和工具返回值的脱敏边界；
4. 异步 ACK、中间状态、最终结果、超时、取消和重试；
5. 与 HTTP/gRPC/NATS 已有 boundary 的重复 Span 清理。
