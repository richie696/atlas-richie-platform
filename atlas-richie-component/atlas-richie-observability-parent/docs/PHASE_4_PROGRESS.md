# Phase 4 阶段记录：Foundry 应用迁移

## 1. 阶段目标

按照临时方案，将当前 `atlas-foundry-ai` 逐步迁移到统一的
`atlas-richie-observability-spring-boot-starter`。本阶段继续执行“完成一个子阶段、验收一个子阶段、通过后再进入下一个子阶段”的门禁。

迁移顺序：

1. 公共依赖和环境配置收敛；
2. 服务启动诊断与 `OTEL_SDK_DISABLED` 开/关验收；
3. Gateway -> Orchestrator 最小真实请求；
4. HTTP/gRPC/NATS/MCP 真实传播和日志/指标关联；
5. Agent、RAG、LLM、Tool、SSE、Channel、Worker 业务阶段补充；
6. 失败、超时、重试、取消、关闭、脱敏和重复 Span 验收；
7. 全部平台阶段结束后，执行最终 Foundry E2E。

## 2. 当前状态

Phase 4.1“公共依赖和环境配置收敛”已通过，记录见
[Phase 4.1 验收记录](./PHASE_4_DEPENDENCY_ACCEPTANCE.md)。

当前已完成：

- 标准 Spring Boot 服务由公共 `service-dependencies` 统一引入完整 observability starter；
- Gateway、MCP Mock 等独立 parent 服务改为显式引入完整 starter；
- 叶子 POM 删除重复的 Actuator、旧 `atlas-richie-tracing`、直接 OTel Starter 和独立 Prometheus Registry；
- Nacos shared 配置统一采用 `OTEL_*` 标准入口；
- K8s observability ConfigMap 显式提供 `OTEL_SDK_DISABLED`、三 signal OTLP、W3C propagator、sampler 和 Resource 属性；
- Foundry 全仓 `mvn -q -DskipTests compile` 通过。
- Phase 4.2“服务运行时与总开关”已通过，记录见
  [Phase 4.2 验收记录](./PHASE_4_RUNTIME_ACCEPTANCE.md)。
- 已在本地 Alloy/Tempo/Loki/Prometheus 中确认 Foundry Mock 的真实 HTTP 请求、JVM/HTTP
  Metrics、Trace 和带 `request_id/trace_id/span_id` 的 Logs 关联。
- Phase 4.3“Gateway → Orchestrator 最小真实请求”已通过，记录见
  [Phase 4.3 验收记录](./PHASE_4_GATEWAY_ORCHESTRATOR_ACCEPTANCE.md)。
- Gateway 已补充生产级 `web-application-type: reactive`，并修复 OTLP/Prometheus 双 Registry
  造成的启动冲突；真实 Gateway outbound HTTP 指标已在 Prometheus 中按路由查询到。
- Phase 4.4 的 HTTP/gRPC/MCP 协议传播子阶段已通过，记录见
  [Phase 4.4 协议验收记录](./PHASE_4_PROTOCOL_ACCEPTANCE.md)。真实 MCP Gateway、Discovery、Mock
  三进程已经在同一条 Tempo Trace 中出现 HTTP、gRPC client/server、MCP client/server Span，
  并完成 Loki request_id/trace_id 关联和 Prometheus HTTP/MCP/gRPC 指标核对。
- Phase 4.4 的 NATS/JetStream 子阶段已通过，记录见
  [Phase 4.4 NATS 验收记录](./PHASE_4_NATS_ACCEPTANCE.md)。真实 Gateway Producer、Admin Consumer、
  归档 JDBC Span 已在同一条 Tempo Trace 中关联，并完成 Gateway/Admin Actuator 与 Prometheus
  dependency.request publish/receive 指标核对。
- Phase 4.5.1 的 Agent/RAG/LLM/Tool/SSE 核心业务观测子阶段已通过，记录见
  [Phase 4.5.1 验收记录](./PHASE_4_AGENT_RAG_SSE_ACCEPTANCE.md)。AgentScope Hook、Agent/LLM/Tool
  Span、RAG 检索 Span、SSE 生命周期 Span 和低基数 Micrometer 指标均已接入；平台 starter
  兼容旧 Nacos OTel 服务名占位符的定向上下文测试也已通过。
- Phase 4.5.2 的 Channel/Worker 业务观测子阶段已通过，记录见
  [Phase 4.5.2 验收记录](./PHASE_4_CHANNEL_WORKER_ACCEPTANCE.md)。入站 JetStream、Channel 编排、
  出站 JetStream 和 Prompt Index Worker 均已补充业务 Span，并接入低基数指标。
- Phase 4.6 的失败/超时/重试/取消/关闭/脱敏/重复 Span 子阶段已通过，记录见
  [Phase 4.6 验收记录](./PHASE_4_FAILURE_RETRY_ACCEPTANCE.md)。平台侧阶段任务已全部完成，
  最终 Foundry E2E 已通过，详细证据见 [最终 Foundry E2E 验收记录](./FINAL_FOUNDRY_E2E_ACCEPTANCE.md)。

Phase 4 最终 Foundry E2E 已通过，Trace、Logs、Metrics、Actuator、统一开关、关联字段和优雅关闭均已完成验证；当前阶段全部关闭。

## 3. 约束

- Phase 4.2 已通过，现在才允许开始真实 Gateway -> Orchestrator 链路验收；
- Phase 4.4、4.5.1、4.5.2 和 4.6 以及最终 Foundry E2E 均已通过，完整方案验收通过；
- 所有平台阶段和应用迁移子阶段均已通过，临时方案文档已按完成条件清理；
- 最终 E2E 已使用真实 Foundry Gateway/Orchestrator 请求，未用平台组件 smoke 替代。
