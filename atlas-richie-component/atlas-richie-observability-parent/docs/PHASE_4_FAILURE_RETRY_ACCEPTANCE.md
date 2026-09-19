# Phase 4.6 验收记录：失败 / 超时 / 重试 / 取消 / 关闭 / 脱敏 / 重复 Span

## 1. 结论

Phase 4.6 已通过代码级和定向测试验收。Phase 4 规划中的平台侧接入任务至此全部完成；
下一步只允许执行基于真实 `atlas-foundry-ai` 的最终 E2E，不再新增平台业务接入范围。

最终 E2E 前仍必须清理运行环境中的 Nacos 配置同步问题和既有 TokenBudget 测试夹具问题；
它们不是本阶段观测实现失败，但不能带入最终“全部通过”的结论。

## 2. 失败与超时

- `AgentObservabilityHook.ErrorEvent` 会结束活动的 Agent、LLM、Tool Span，并记录异常类型；
- SSE 超时结束 `agent.sse.stream`，并记录 `agent.failure.total{failure_type="timeout"}`；
- 同步 Channel/Worker 和 Prompt Index Worker 异常均设置 Span ERROR 状态；
- 失败指标只保留异常类型/固定失败类型，不写入异常消息、请求正文或凭据。

## 3. 重试

MCP transport failure 的“无 Tool 重试”路径创建 `agent.retry` Span，并增加：

```text
agent.retry.total{reason="mcp_transport_failure"}
```

重试原因作为低基数枚举写入 Span 和指标；实际第二次 Agent 调用继续使用独立 AgentScope
业务 Span，首次调用不会被覆盖或重复结束。

## 4. 取消与关闭

- 用户取消 SSE 时，`RequestCancellationRegistry` 回调先结束 `agent.sse.stream` 为
  `CANCELLED`，再发送取消事件并关闭 emitter；
- `agent.cancel.total{reason="user_request"}` 记录取消；
- Channel 入站消费者停止时创建 `channel.inbound.shutdown`；
- Prompt Index Worker 停止时创建 `worker.prompt-index.shutdown`；
- 关闭 Span 只记录消费者数量等低基数信息，不记录消息正文。

## 5. 脱敏和重复 Span

新增 `ObservabilityRedactor`，对 `token`、`password`、`secret`、`authorization`、`apiKey`、
`credential` 等字段统一输出 `[REDACTED]`，普通文本统一去换行并限制 128 字符。

重复 Span 防护来自三处：

- SSE `finish` 使用 `AtomicBoolean`，completion、timeout、error、cancel 竞争时只结束一次；
- Agent Hook 对未关闭的旧 call/reasoning/tool Span 先按 `RESTARTED` 结束，再创建新 Span；
- Channel 入站的幂等决定由同一个业务 Span 归类为 `processed` 或 `already_completed`，不会
  为一次重复投递伪造第二次业务处理成功。

## 6. 验收命令和结果

### 6.1 编译

```bash
mvn -q -DskipTests compile \
  -pl foundry-agent-engine/foundry-orchestrator-service,foundry-agent-engine/foundry-frontdoor-runtime-spring \
  -am
```

结果：通过。

### 6.2 定向测试

```bash
mvn -q -pl foundry-agent-engine/foundry-orchestrator-service -am \
  -Dtest=MetricRegistryTest,AgentObservabilityHookTest,ObservabilityRedactorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。覆盖指标开关、失败/重试/取消指标、Agent Hook 生命周期和凭据字段脱敏。

## 7. 验收清单

| 编号 | 验收项 | 状态 | 证据 |
|---|---|---|---|
| FAILURE-OBS-001 | Agent/LLM/Tool 异常结束并记录 ERROR | 通过 | `AgentObservabilityHook` + 定向测试 |
| FAILURE-OBS-002 | SSE timeout/error 形成失败指标 | 通过 | `AgentChatStreamService` + 编译通过 |
| RETRY-OBS-001 | MCP transport retry 有独立 retry Span 和指标 | 通过 | `AgentOrchestrator` 实现检查、编译通过 |
| CANCEL-OBS-001 | 用户取消结束 SSE Span 且记录取消指标 | 通过 | `AgentChatStreamService` 实现检查 |
| SHUTDOWN-OBS-001 | Channel/Prompt Worker shutdown 有 Span | 通过 | 两个 Worker 实现检查、编译通过 |
| REDACT-OBS-001 | 凭据字段不进入观测日志属性 | 通过 | `ObservabilityRedactorTest` |
| DUPLICATE-OBS-001 | SSE/Agent/Channel 不重复结束业务 Span | 通过 | 原子 finish、Hook 状态、幂等 outcome 实现检查 |
| REGRESSION-OBS-006 | 子阶段编译和定向测试通过 | 通过 | 两个 Maven 命令 exit code 0 |

## 8. 最终 E2E 边界

平台阶段验收至此结束。最终 E2E 必须按临时方案文档启动当前 Foundry，使用真实 Gateway、
Orchestrator、NATS/JetStream、MCP、RAG/Agent/SSE 请求，并同时核对：

- Tempo：完整父子 Span 和协议边界；
- Loki：同一 requestId、traceId、spanId 的日志关联和脱敏；
- Prometheus：JVM、HTTP、gRPC、NATS、Agent、RAG、SSE、Channel、Worker 指标；
- Actuator：health、metrics、prometheus 和 dependency 观测结果；
- `OTEL_SDK_DISABLED=true`：三 signal 和业务指标全部关闭；
- `OTEL_SDK_DISABLED=false`：三 signal 和业务指标全部可见。

最终 E2E 失败时必须修复并从失败边界重新执行，直到所有清单项通过；临时方案文档在全部
完成前不得删除。
