# Phase 2 阶段检查记录：Actuator、Metrics 和 Logging

## 记录性质

本文件保留 Phase 2 的过程记录。最终结论、证据和进入 Phase 3 的约束见 [Phase 2 验收记录](./PHASE_2_ACCEPTANCE.md)。

## 本次已落地

### Actuator 端点策略

- 开启模式且应用没有显式配置时，默认暴露 `health,info,prometheus`；
- 默认开启 liveness/readiness probes；
- 应用显式配置优先，不被默认策略覆盖；
- `atlas.observability.enabled=false` 强制关闭 OTel、Micrometer 全量指标和 Prometheus 出口；
- `otel.sdk.disabled=true` 作为兼容总开关时，执行同样的指标和 Prometheus 关闭策略；
- health/liveness/readiness 保留，满足容器存活和就绪探针需要。

### JVM、线程池和依赖指标

- JVM、GC、类加载、文件描述符、HTTP 和 Spring Boot 能识别的标准连接池继续复用官方 Actuator/Micrometer Binder；
- `ExecutorMetricsBinder` 为应用声明的 `ThreadPoolExecutor`/`ThreadPoolTaskExecutor` 提供 active、pool size、max、queue、completed；
- `ObservabilityRejectedExecutionHandler` 在不改变原拒绝策略的前提下提供 rejected 计数；
- `DependencyMetrics` 提供 DB、Redis、HTTP、gRPC、NATS、MCP 适配器共用的低基数请求耗时和连接数门面；
- 指标不允许使用 request_id、trace_id、用户 ID、完整 URL 或消息内容作为标签。

### 日志关联和安全

- Servlet 请求完成日志包含 operation、status、duration_ms；
- 异常请求包含 error.type、error.stage；
- MDC 值统一进行换行过滤、长度限制和 Authorization/Cookie/Token/Prompt/Secret 等敏感值脱敏；
- 组件装配官方 OTel Logback Appender；
- stdout JSON Encoder 保持应用可控，不由 Starter 强行覆盖现有 Logback 布局。

## 对照验收清单

| 设计验收项 | 当前结果 | 说明 |
|---|---|---|
| JVM/GC/Heap/Metaspace | 通过 | 真实 Starter 应用已在本地 Prometheus 查询到 JVM、GC、Class、CPU 指标 |
| Thread/Executor/Rejected/Queue | 代码通过 | `ExecutorMetricsBinderTest`、拒绝策略计数测试 |
| DB/Redis/HTTP/gRPC/NATS 依赖指标 | 通过（基础契约） | `DependencyMetrics` 低基数契约完成；具体客户端适配按设计留给 Phase 3 |
| Servlet 请求日志 | 代码通过 | 成功、异常、operation/status/duration/error 和脱敏测试 |
| WebFlux/Reactor/MDC | 通过 | 真实异步线程切换测试通过，MDC 无泄漏 |
| Netty 请求边界 | 通过 | 本地 WebFlux 应用由 Reactor Netty 接收真实请求并查询到 Server Span |
| SSE/专用协议 Binder | Phase 3 | 领域专用 SSE、Worker、Channel 和客户端适配不在 Phase 2 基础组件关闭范围 |
| JSON stdout/OTLP Logs | 通过（组件边界） | OTel Logback Appender 和本地 Loki 查询通过；具体 stdout Encoder 仍由应用配置负责 |
| Trace-to-Logs | 通过 | Tempo、Loki 查询到相同 trace_id/span_id，Grafana 跳转配置已验证 |
| 全部关闭 | 通过 | Spring Boot 开启/关闭上下文均验证，关闭后健康探针可用且 Prometheus 返回 404 |

## 自动化证据

执行：

```bash
mvn -q -f atlas-richie-component/atlas-richie-observability-parent/pom.xml test
```

当前结果：

```text
tests=32
errors=0
skipped=1
failures=0
```

同时执行 `git diff --check`，无格式错误。

## Phase 2 关闭和 Phase 3 边界

1. Phase 2 的基础组件和本地三信号闭环已经通过，详见 `PHASE_2_ACCEPTANCE.md`；
2. Phase 3 负责 HTTP、gRPC、NATS、MCP、Redis Stream 等协议组件迁移；
3. Phase 3 负责具体客户端连接池、SSE、Worker、Channel 的领域指标接入；
4. 在 Phase 3 完成真实跨服务请求验证前，不删除旧 tracing 或基础依赖中的全局 Actuator。
