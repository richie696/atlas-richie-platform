# Phase 4.5.1 验收记录：Agent / RAG / LLM / Tool / SSE 核心业务观测

## 1. 结论

Phase 4.5.1 的业务观测改造已完成，定向验收通过。当前子阶段关闭以下边界：

- AgentScope 每个 Agent 实例具备独立 Hook，不再依赖全局无行为的占位 Hook；
- Agent 调用、LLM 推理、Tool 调用形成 `agent.call → agent.llm.call / agent.tool.call`
  业务 Span；
- RAG 每个知识库检索形成 `agent.rag.retrieve` Span，并记录成功/失败和缓存命中指标；
- SSE 连接形成 `agent.sse.stream` 生命周期 Span，覆盖完成、超时、异常、重复请求和拒绝；
- Agent、LLM、Tool、RAG、SSE 的耗时和计数统一进入 Micrometer/OTLP 指标出口；
- `OTEL_SDK_DISABLED` / `atlas.observability.enabled` 关闭时，业务观测不会继续写入指标或
  创建有效的 OTel SDK 出口；
- 工具返回的 `success` 等小写状态在观测边界统一归一化为 `SUCCESS`，避免成功调用被错误标记
  为 ERROR；LLM iteration 从每次 Agent 调用重新计数。

Phase 4.5.1 只关闭上述核心业务阶段；Channel、Worker、失败/超时/重试/取消/关闭/脱敏/
重复 Span 仍是后续独立子阶段，不能据此宣称 Phase 4 总体完成。

## 2. 实现范围

### 2.1 AgentScope Hook

文件：

`foundry-agent-engine/foundry-orchestrator-service/src/main/kotlin/ai/atlasfoundry/orchestrator/engine/hook/AgentObservabilityHook.kt`

`AgentFactory` 为每个 Agent 请求创建 Hook，Hook 负责：

- `PreCallEvent` / `PostCallEvent`：创建和结束 `agent.call`；
- `PreReasoningEvent` / `PostReasoningEvent`：创建和结束 `agent.llm.call`；
- `PreActingEvent` / `PostActingEvent`：创建和结束 `agent.tool.call`；
- `ReasoningChunkEvent`：累计 `agent.sse.chunk.total`；
- `ErrorEvent`：为未结束的活动 Span 记录异常、设置 ERROR 状态并收敛生命周期；
- 日志输出统一包含 `stage`、`operation`、`requestId`、状态和耗时。

Span 属性只放低基数或请求级关联信息：`agent.request_id`、Agent 名称、模型名、工具名、
输入消息数和推理 iteration。`requestId` 不进入 Prometheus 标签，避免高基数爆炸。

### 2.2 RAG

文件：

`foundry-agent-engine/foundry-orchestrator-service/src/main/kotlin/ai/atlasfoundry/orchestrator/application/chat/AgentChatPipeline.kt`

每个知识库调用 `ragCacheService.retrieveWithMetadata` 时创建 `agent.rag.retrieve`，属性包含
请求 ID、知识库 ID 和查询长度；结果进入：

- `agent.rag.retrieve.duration`；
- `agent.rag.retrieve.total`，标签为 `status`、`cache_hit`；
- 成功/异常日志，异常不会吞掉原始业务错误。

### 2.3 SSE

文件：

`foundry-agent-engine/foundry-orchestrator-service/src/main/kotlin/ai/atlasfoundry/orchestrator/application/chat/AgentChatStreamService.kt`

SSE Span 在入口创建并以原子 finish 保护，以下路径都会结束 Span 并记录最终状态：

- 参数校验失败；
- 重复请求或连接拒绝；
- 正常完成；
- emitter completion/error；
- 超时；
- 同步执行异常。

对应指标为 `agent.sse.stream.duration`、`agent.sse.stream.total`，标签只包含状态。

## 3. 平台兼容修复

文件：

`atlas-richie-observability-spring-boot-autoconfigure/src/main/java/cn/richie696/component/observability/autoconfigure/ObservabilityEnvironmentPostProcessor.java`

当前本地 Nacos 实例曾返回旧的嵌套服务名占位符：

```text
${OTEL_SERVICE_NAME:${spring.application.name}}
```

官方 OTel Spring Boot 自动配置无法在自身配置解析器中解析该嵌套 Spring 占位符。starter
现在在远程 ConfigData 和 Bean 创建之间做服务名归一化，并在自动配置的 BeanFactory 阶段再次
兜底，保证标准 `OTEL_SERVICE_NAME`、`spring.application.name` 或固定安全默认值可用。

验证覆盖真实 `OpenTelemetryAutoConfiguration` 上下文，而非只检查字符串替换。

## 4. 验收命令和结果

### 4.1 平台 starter 兼容测试

```bash
mvn -q -f atlas-richie-component/pom.xml \
  -pl atlas-richie-observability-parent/atlas-richie-observability-spring-boot-autoconfigure \
  -am -Dtest=ObservabilityAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。覆盖开关关闭、资源属性、Actuator 默认暴露和官方 OTel 上下文启动。

### 4.2 Agent/RAG/SSE 定向测试

```bash
mvn -q -pl foundry-agent-engine/foundry-orchestrator-service -am \
  -Dtest=MetricRegistryTest,AgentObservabilityHookTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。

### 4.3 Orchestrator 编译回归

```bash
mvn -q -DskipTests compile \
  -pl foundry-agent-engine/foundry-orchestrator-service -am
```

结果：通过。

### 4.4 现有全量模块回归的边界记录

曾执行：

```bash
OTEL_SERVICE_NAME=foundry-orchestrator-service \
mvn -q -pl foundry-agent-engine/foundry-orchestrator-service -am clean test
```

观测改造相关测试均通过，但全量模块仍有两类既有环境/基线问题：

1. 当前运行中的 Nacos 服务端只返回 `orchestrator-async` 和 `sync-worker` 两个线程池，未同步
   仓库中已存在的 `prompt-index` 配置，导致 `ChannelRagRuntimeE2ETest` 上下文无法注入
   `@Qualifier("prompt-index")` Executor。该问题不是本次观测代码创建的 Bean 冲突；正式 E2E
   前必须把 `config/nacos/foundry-orchestrator-service.yaml` 同步到当前 Nacos。
2. `TokenBudgetServiceTest` 的无 Spring 构造路径依赖未初始化的 `GlobalCache`，产生 8 个
   `Token quota cache unavailable`；该测试与本子阶段观测实现无依赖关系，应在缓存测试夹具
   子任务中单独修复。

因此本记录的通过依据是 Phase 4.5.1 的定向验收、平台兼容验收和编译回归；上述两项不作为
本子阶段的实现失败，但在最终 Foundry E2E 前必须清零，最终 E2E 不得以此状态宣称通过。

## 5. 验收清单

| 编号 | 验收项 | 状态 | 证据 |
|---|---|---|---|
| AGENT-OBS-001 | AgentScope Hook 按 Agent 实例创建 | 通过 | `AgentFactory` + `AgentObservabilityHookTest` |
| AGENT-OBS-002 | Agent/LLM/Tool 业务 Span 成对结束 | 通过 | Hook 定向测试、编译通过 |
| AGENT-OBS-003 | LLM iteration 和工具成功状态正确归一化 | 通过 | Hook 实现与定向测试 |
| RAG-OBS-001 | 每个知识库检索创建 RAG Span | 通过 | `AgentChatPipeline` 实现检查 |
| RAG-OBS-002 | RAG 成功/失败/缓存命中进入指标 | 通过 | `MetricRegistryTest`、编译通过 |
| SSE-OBS-001 | SSE 正常、异常、超时、拒绝均结束 Span | 通过 | `AgentChatStreamService` 实现检查、编译通过 |
| SSE-OBS-002 | SSE 指标不使用 requestId 高基数标签 | 通过 | `MetricRegistry` 实现检查 |
| PLATFORM-OBS-001 | 旧 Nacos OTel 服务名占位符可兼容启动 | 通过 | `ObservabilityAutoConfigurationTest` 官方上下文测试 |
| REGRESSION-OBS-001 | 观测定向测试通过 | 通过 | Maven exit code 0 |
| REGRESSION-OBS-002 | Orchestrator 编译通过 | 通过 | Maven exit code 0 |
| REGRESSION-OBS-003 | 全量模块无基线错误 | 待清理 | Nacos `prompt-index` 与 TokenBudget 测试夹具问题 |

## 6. 下一阶段边界

Phase 4.5.1 通过后，下一子阶段只处理 Channel、Worker 的业务 Span、消息关联和后台任务
指标；仍不得提前进行最终 Foundry E2E。全量基线问题和最终部署配置同步在最终 E2E 前统一
清零并重新验收。
