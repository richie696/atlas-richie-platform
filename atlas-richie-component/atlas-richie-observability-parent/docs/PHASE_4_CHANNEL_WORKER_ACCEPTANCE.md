# Phase 4.5.2 验收记录：Channel / Worker 业务观测

## 1. 结论

Phase 4.5.2 已通过定向验收，关闭 Channel 异步入站、编排 Worker、出站发布和 Prompt
Index Worker 的业务观测接入。本阶段仍不关闭失败/重试矩阵、取消、关闭、脱敏和重复 Span
专项；这些任务继续保持独立门禁。

## 2. 实现范围

### 2.1 Channel 入站消费

`ChannelInboundNatsConsumer` 为每条 JetStream 入站消息创建 `channel.inbound.consume`
Consumer Span，并记录：

- subject、worker index、delivery count；
- requestId、channelId；
- `processed`、`already_completed`、`retry`、`discarded` 等结果；
- ACK、重试、终止和异常的最终状态。

业务 Span 置于中台 NATS 自动 Span 之下，并通过 `span.makeCurrent()` 将同一上下文传递给
Channel Worker 和后续 Agent 调用。requestId 只进入 Span 和日志，不进入指标标签。

### 2.2 Channel 编排 Worker

`ChannelQueueWorker` 为实际编排过程创建 `channel.worker.process` Internal Span，记录：

- requestId、channelId、streamId；
- 并发闸门和处理耗时；
- 正常完成、Fallback 完成和异常结束。

Worker 在成功、异常和线程池/信号量等待异常路径均结束 Span，且只有成功路径标记 OK。

### 2.3 Channel 出站发布

`NatsChannelOutboundGateway` 为 JetStream 结果发布创建 `channel.outbound.publish` Producer
Span，记录 reply subject、streamId、发布结果和异常；实际 `nats.stream().publish` 在该 Span
上下文中执行，因此 Channel Service 的消费端可以继续沿同一 trace 关联。

### 2.4 Prompt Index Worker

共享 `PromptIndexWorker` 增加两个后台 Worker Span：

- `worker.prompt-index.recover`：数据库扫描和 NATS wake-up 订阅恢复任务；
- `worker.prompt-index.job`：单个 Prompt Index Job 的异步执行。

Span 覆盖实际 Executor 线程中的执行区间，而不是只覆盖调度方法返回前的提交动作；Job ID
只作为 Span 属性，不进入指标标签。

## 3. 指标契约

`MetricRegistry` 新增以下低基数指标族：

| 指标 | 标签 | 用途 |
|---|---|---|
| `channel.inbound.duration` / `.total` | `status`, `outcome` | 入站解码、幂等、ACK/重试结果 |
| `channel.worker.duration` / `.total` | `status`, `outcome` | Agent 编排和回复构造 |
| `channel.outbound.duration` / `.total` | `status`, `outcome` | 出站 JetStream 发布 |
| `worker.prompt.index.duration` / `.total` | `status`, `outcome` | Prompt Index 扫描和任务执行 |

标签值由固定状态集合和安全归一化函数生成；requestId、streamId、subject、jobId 不进入
Prometheus 标签，防止高基数污染。

## 4. 验收命令和结果

### 4.1 编译回归

```bash
mvn -q -DskipTests compile \
  -pl foundry-agent-engine/foundry-orchestrator-service,foundry-agent-engine/foundry-frontdoor-runtime-spring \
  -am
```

结果：通过。

### 4.2 定向回归

```bash
mvn -q -pl foundry-agent-engine/foundry-orchestrator-service -am \
  -Dtest=MetricRegistryTest,AgentObservabilityHookTest,ChannelFailureMessageResolverTest,ChannelOutboundDeliveryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。

定向测试覆盖统一指标门面、观测开关行为、Agent Hook 兼容性，以及 Channel 出站模型和失败
消息契约；新增 Channel Worker 的构造接线由完整模块编译验证。

## 5. 验收清单

| 编号 | 验收项 | 状态 | 证据 |
|---|---|---|---|
| CHANNEL-OBS-001 | 入站 JetStream 消费具备业务 Consumer Span | 通过 | `ChannelInboundNatsConsumer` 实现检查、编译通过 |
| CHANNEL-OBS-002 | 入站 ACK/重试/终止结果可在 Span 和日志区分 | 通过 | outcome 状态归一化实现 |
| CHANNEL-OBS-003 | Channel 编排 Worker 具备生命周期 Span | 通过 | `ChannelQueueWorker` 实现检查、编译通过 |
| CHANNEL-OBS-004 | 出站 JetStream 发布具备 Producer Span | 通过 | `NatsChannelOutboundGateway` 实现检查、编译通过 |
| WORKER-OBS-001 | Prompt Index 扫描和单 Job 在实际异步线程中具备 Span | 通过 | `PromptIndexWorker` 实现检查、模块编译通过 |
| METRIC-OBS-001 | Channel/Worker 指标进入统一 Registry | 通过 | `MetricRegistryTest` |
| METRIC-OBS-002 | 指标不使用 requestId/jobId 等高基数标签 | 通过 | 指标契约和实现检查 |
| REGRESSION-OBS-004 | Channel/Worker 定向回归通过 | 通过 | Maven exit code 0 |
| REGRESSION-OBS-005 | Orchestrator 与 Frontdoor Runtime 编译通过 | 通过 | Maven exit code 0 |

## 6. 下一阶段边界

下一阶段只处理失败、超时、重试、取消、关闭、脱敏和重复 Span 行为矩阵；完成后才可以进入
最终 Foundry 全链路 E2E。最终 E2E 仍必须使用真实 NATS/HTTP/gRPC/MCP/Agent/RAG/SSE 请求，
并查询 Tempo、Loki、Prometheus 和 Actuator 证据。
