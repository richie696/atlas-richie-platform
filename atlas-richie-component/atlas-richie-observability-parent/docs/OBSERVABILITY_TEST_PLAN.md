# Atlas Richie Observability 测试设计

## 1. 测试目标

本测试设计验证 Phase 1 和 Phase 2 实现是否满足以下可观察行为：

- 应用可以通过统一 Starter 装配 OTel、Actuator、日志关联和运行时指标；
- atlas.observability.enabled=false 可以关闭 Trace、Metrics、Logs 的输出能力，同时保留健康检查；
- trace_id、span_id 和 request_id 保持不同语义；
- 异步上下文和日志 MDC 不因线程复用而串线；
- 线程池指标使用低基数的池名称；
- 自动配置不会重复创建 OTel Provider、MeterRegistry 或 HTTP 埋点；
- 敏感字段不会进入关联日志；
- 组件可以在无真实 Alloy 时用 SDK/Registry 测试验证契约，在本地 observability 环境再验证真实传输。
- WebFlux/Reactor 线程切换不会造成 MDC/request_id 串线或泄漏；
- 完整 Starter 可以把 Trace、Logs、Metrics 发送到本地 Alloy，并在 Tempo、Loki、Prometheus 形成查询闭环。

## 2. 测试边界

| 层级 | 验证内容 | 证据边界 |
|---|---|---|
| Core unit | Context、关联 ID、总开关、传播契约 | 证明纯契约和状态行为，不证明网络传输 |
| Auto-configuration | Spring Boot Bean、配置绑定、关闭行为 | 证明应用上下文装配，不证明后端已收到数据 |
| Metrics integration | JVM、线程池、注册表过滤和公共标签 | 证明 MeterRegistry 产生的数据，不证明 Prometheus 查询 |
| Logging integration | Servlet 请求关联、MDC、响应 Request ID、脱敏 | 证明应用日志上下文，不证明 Loki 存储 |
| Build/dependency | Starter 聚合、依赖唯一性、自动配置资源 | 证明发布包可构建和可装配 |
| Local observability | OTLP traces/logs/metrics 到 Alloy，再到 Tempo/Loki/Prometheus | 证明真实本地后端闭环 |

## 3. Phase 1/2 最小测试矩阵

| 编号 | 风险 | 测试 |
|---|---|---|
| OBS-CORE-001 | 关闭开关仍产生观测数据 | ObservabilityStateTest 验证 disabled 状态和信号判断 |
| OBS-CORE-002 | 业务 Request ID 覆盖 Trace ID | CorrelationIdsTest 验证三类 ID 独立 |
| OBS-CORE-003 | 线程复用导致 request_id 串线 | ObservabilityContextTest 验证 Scope 关闭后恢复上一个 Context |
| OBS-AUTO-001 | Starter 没有加载自动配置 | ObservabilityAutoConfigurationTest 验证配置属性和状态 Bean |
| OBS-AUTO-002 | 单开关不能关闭 OTel/Actuator | 使用 disabled 环境启动上下文，验证 OTel disabled 和 Metrics deny 配置 |
| OBS-AUTO-003 | 关闭后仍创建真实 OTel SDK | 通过官方 OTel Spring Boot 自动配置验证 disabled 模式返回官方 No-op OpenTelemetry |
| OBS-AUTO-004 | 启动诊断泄漏出口凭证 | 验证诊断只输出 Resource/Exporter/Sampler 名称，不输出 endpoint 凭证 |
| OBS-METRIC-001 | 线程池无指标或标签高基数 | ExecutorMetricsBinderTest 验证 active、queue、pool_name |
| OBS-METRIC-002 | 依赖指标把请求 ID 作为标签 | DependencyMetricsTest 验证 dependency_type、target_service、operation、status 低基数契约 |
| OBS-METRIC-003 | 线程池拒绝任务不可查询 | ExecutorMetricsBinderTest 验证显式包装 `ObservabilityRejectedExecutionHandler` 后的 rejected 指标 |
| OBS-LOG-001 | Servlet 请求没有日志关联 | ObservabilityMdcFilterTest 验证 Request ID、响应 Header、清理 MDC |
| OBS-LOG-002 | 非法/过长 Request ID 进入日志 | 验证过滤后生成安全 ID |
| OBS-LOG-003 | 完成日志缺少定位字段或泄漏敏感值 | ObservabilityMdcFilterTest 验证 operation、status、duration、error.type、error.stage 和 MDC 脱敏 |
| OBS-ACTUATOR-001 | 总开关误暴露敏感管理端点 | ObservabilityAutoConfigurationTest 验证默认仅暴露 health、info、prometheus，并保留应用显式配置优先级 |
| OBS-BUILD-001 | Starter 引入重复 OTel 入口 | 依赖树检查 OTel API/SDK/Starter 只有平台统一版本 |
| OBS-WEBFLUX-001 | Reactor 线程切换丢失 request_id/MDC | ObservabilityWebFluxFilterTest 验证 boundedElastic 线程切换和清理 |
| OBS-STARTER-001 | 完整 Starter 无法启动或端点策略错误 | Phase2EnabledSpringBootTest 验证 WebFlux、health、prometheus 和 JVM 指标 |
| OBS-STARTER-002 | 总开关关闭后仍输出观测数据 | Phase2DisabledSpringBootTest 验证健康探针保留、Prometheus 404 和请求不带 Request ID |
| OBS-E2E-001 | 本地后端未收到三类信号 | Phase2LocalOtlpSmokeTest 验证 OTLP 请求及 Tempo/Loki/Prometheus 查询 |

Phase 1/2 额外验收：应用 Context 关闭时由组件请求官方 SDK 的 Trace、Metrics、Logs provider
force flush；最终 provider shutdown 仍由官方 OTel Spring Boot Starter 的 Bean 生命周期负责，组件不重复关闭 SDK。

## 4. Phase 2 仍不声称已经证明的内容

- 单元测试不能证明 Alloy、Tempo、Loki 或 Prometheus 可访问；
- ApplicationContext 启动不能证明真实应用的 gRPC、NATS、MCP 跨服务传播；
- Actuator 指标存在不能证明 Grafana Dashboard 已配置；
- JSON/MDC 字段存在不能证明日志已经被 Loki 正确解析；
- OTel SDK disabled 不能替代真实 local smoke；
- 通用 `DependencyMetrics` 契约不能替代各协议组件的具体客户端适配验收；
- WebFlux/Netty 请求边界通过不代表 SSE、Worker、Channel 等领域专用 Binder 已完成。
- Pyroscope profile 不属于第一阶段 OTLP Metrics/Logs/Traces 的自动验证范围。

## 5. 本地闭环验收

在组件测试通过后，使用 /Users/richie696/Development/docker-scripts/observability/local：

1. 启动 Alloy、Prometheus、Loki、Tempo、Pyroscope、Grafana；
2. 启动一个依赖完整 Starter 的最小 Spring Boot 应用；
3. 发起真实 HTTP 请求并触发一条失败请求；
4. 在 Grafana/Tempo 查询 Trace；
5. 用同一个 trace_id 在 Loki 查询日志；
6. 在 Prometheus 查询 JVM、线程池和 HTTP 指标；
7. 使用 atlas.observability.enabled=false 重启应用；
8. 验证健康检查仍可用，同时后端不再收到新的 Trace、Logs 和 Metrics。
