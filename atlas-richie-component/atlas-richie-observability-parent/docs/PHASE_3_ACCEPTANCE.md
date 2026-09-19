# Phase 3 总验收记录：协议组件迁移

## 1. 结论

Phase 3 已通过平台组件阶段验收，可以开启 Phase 4 存量应用迁移。

本阶段已将 HTTP、gRPC、NATS/JetStream、MCP HTTP Client/Server 的观测能力接入统一
Observability Core，并完成旧 tracing 依赖传递边界和旧 HTTP boundary 的兼容收敛。

本记录不提前替代最终 Foundry E2E。按照用户要求，只有所有平台阶段结束后，才使用当前智能体项目
按临时改造方案执行真实应用链路测试。

## 2. 子阶段验收清单

| 子阶段 | 验收记录 | 状态 |
|---|---|---|
| HTTP Client/SSE | [PHASE_3_HTTP_ACCEPTANCE.md](./PHASE_3_HTTP_ACCEPTANCE.md) | 通过 |
| gRPC Client/Server | [PHASE_3_GRPC_ACCEPTANCE.md](./PHASE_3_GRPC_ACCEPTANCE.md) | 通过 |
| NATS/JetStream | [PHASE_3_NATS_ACCEPTANCE.md](./PHASE_3_NATS_ACCEPTANCE.md) | 通过 |
| MCP transport/Tool | [PHASE_3_MCP_ACCEPTANCE.md](./PHASE_3_MCP_ACCEPTANCE.md) | 通过 |
| 重复 boundary 与兼容依赖 | [PHASE_3_BOUNDARY_ACCEPTANCE.md](./PHASE_3_BOUNDARY_ACCEPTANCE.md) | 通过 |

每个子阶段均先完成实现和聚焦回归，再进入下一子阶段；总验收没有跳过子阶段记录。

## 3. 总体验收证据

### 3.1 汇总回归

```bash
mvn -q -f atlas-richie-component/pom.xml \
  -pl atlas-richie-observability-parent,atlas-richie-http-parent,atlas-richie-grpc,\
atlas-richie-nats,atlas-richie-mcp-parent,atlas-richie-web-parent/atlas-richie-web-core,\
atlas-richie-tracing -am test
```

结果：Maven 退出码 0。该 reactor 覆盖统一 observability parent、HTTP、gRPC、NATS、MCP、
web boundary 和旧 tracing 兼容入口；各子阶段的定向测试和模块回归记录见对应验收文档。

### 3.2 变更静态检查

```bash
git diff --check
```

结果：通过。

## 4. Phase 3 关闭范围

- 协议组件复用统一 `OpenTelemetry`、`ObservabilityState`、W3C Propagator、Context 和
  `DependencyMetricsRecorder`；
- 协议组件不创建新的 SDK Provider、Exporter、Actuator 或 Prometheus Registry；
- HTTP、gRPC、NATS、MCP 的客户端/服务端边界具备统一 Trace、request_id、低基数指标和禁用路径；
- 旧 tracing 直接依赖仍可兼容，但组件库不再传递应用级 Spring Boot Starter；
- 新 observability runtime 存在时，旧 web HTTP trace interceptor 自动退出，避免本地 trace ID
  与活动 OTel SpanContext 分裂。

## 5. Phase 4 前置条件

进入 Phase 4 后仍必须逐服务执行并逐项验收：

1. 应用只保留完整 observability starter 作为监控入口；
2. 验证 Resource、Trace、Logs、JVM/Pool Metrics 和总开关；
3. 验证真实异常、超时、重试、降级、取消和关闭路径；
4. 再移除该应用的旧 `atlas-richie-tracing` 直接依赖；
5. 最后清理该应用对基础依赖全局 Actuator 的运行时依赖。
