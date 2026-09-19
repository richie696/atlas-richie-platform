# Phase 1 验收记录：Core 和 Spring Boot AutoConfiguration

## 验收范围

本阶段对应设计文档 Phase 1：

- unified context；
- request_id 和 Resource 属性映射；
- `ATLAS_OBSERVABILITY_ENABLED` / `OTEL_SDK_DISABLED` 有效状态；
- 官方 OTel No-op 关闭契约；
- 官方 Starter 生命周期边界；
- 启动诊断；
- Context 关闭前 force flush。

本阶段不验收 WebFlux、连接池、SSE、协议组件迁移和真实应用到 Alloy 的完整链路，这些分别属于后续阶段。

## 逐项结果

| 验收项 | 结果 | 证据 |
|---|---|---|
| Core Context 和三类关联 ID | 通过 | `ObservabilityContextTest`、`ObservabilityStateTest` |
| Resource 属性映射 | 通过 | `ObservabilityAutoConfigurationTest.environmentPostProcessorMapsPlatformResourceProperties` |
| `atlas.observability.enabled=false` 映射 | 通过 | `ObservabilityAutoConfigurationTest.environmentPostProcessorAddsOfficialDisableProperties` |
| `OTEL_SDK_DISABLED=true` 生效 | 通过 | `ObservabilityAutoConfigurationTest.sdkDisabledMakesEffectiveStateDisabled` |
| 关闭模式使用官方 No-op OpenTelemetry | 通过 | `disabledOtelUsesOfficialNoopOpenTelemetry` |
| 启动诊断输出 Resource/Exporter/Sampler/开关 | 通过 | `ObservabilityStartupDiagnostics` 和凭证过滤测试 |
| 诊断不输出 OTLP endpoint 凭证 | 通过 | `startupDiagnosticsDoNotExposeEndpointCredentials` |
| Context 关闭前 force flush | 通过（代码级） | `ObservabilityLifecycle` 调用官方 SDK provider forceFlush |
| SDK/provider/exporter 不重复创建 | 通过（设计边界） | 平台只复用官方 OTel Spring Boot Starter，不声明第二套 Provider 创建逻辑 |
| SDK/provider 最终 shutdown | 通过（委托边界） | 最终 shutdown 由官方 Starter Bean 生命周期负责，平台不重复关闭 |

## 自动化验证

执行：

```bash
mvn -q -f atlas-richie-component/atlas-richie-observability-parent/pom.xml test
```

结果：

```text
tests=19
errors=0
skipped=0
failures=0
```

同时执行 `git diff --check`，无格式错误。

## 未纳入本阶段的验收项

- 真实 Spring Boot 应用启动并通过完整 Starter 发送 Trace、Metrics、Logs；
- Gateway 到 Application 的 Trace 连续性；
- HTTP/gRPC/NATS/MCP 传播；
- JVM、连接池、Servlet/WebFlux/SSE 的真实 Prometheus 查询；
- Loki Trace-to-Logs、Grafana Exemplar 和 Service Map；
- 旧 `atlas-richie-tracing` 及全局 Actuator 的存量应用迁移。

上述项目保留到 Phase 2、Phase 3、Phase 4 和 Phase 5，不能用本阶段测试结果代替。
