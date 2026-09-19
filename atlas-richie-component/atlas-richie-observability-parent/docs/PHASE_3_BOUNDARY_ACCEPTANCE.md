# Phase 3-重复 Boundary 子阶段验收记录

## 1. 结论

重复 boundary 子阶段已通过。新 observability runtime 负责 HTTP Server 的统一边界；旧 web tracing
仅在没有统一 observability core 时兼容装配。组件库传递依赖不再扩散应用级 OTel Spring Boot Starter。

该结论是平台组件边界验收，不代表 Foundry 真实跨服务请求已经完成；真实链路证据按 Phase 4 和最终 E2E 计划执行。

## 2. 改造内容

### 2.1 旧 tracing 的依赖边界

`atlas-richie-tracing` 仍保留原有坐标和 OTel API 依赖，保证存量应用能够继续编译和运行；
但 `opentelemetry-spring-boot-starter` 改为 Maven `optional`：

- 应用直接声明旧 tracing 时，官方 Starter 仍在其直接依赖闭包中；
- `atlas-richie-mongodb`、`atlas-richie-document-parser` 等组件间接引用旧 tracing 时，
  不再把应用级 SDK、Exporter 和 Spring instrumentation 传递给业务应用；
- 新应用只由 `atlas-richie-observability-spring-boot-starter` 作为完整运行时入口。

### 2.2 旧 HTTP tracing boundary 的兼容保护

`atlas-richie-web-core` 的 `TracingAutoConfiguration` 增加 `@ConditionalOnMissingClass`：
当统一 `ObservabilityState` 存在时，不再装配旧 `OtelTracingInterceptor`。原因是旧拦截器只
维护本地 `ctx.traceId`，不创建 OTel Server Span，和统一运行时并存会造成业务上下文中的 trace
ID 与活动 `SpanContext` 不一致。

没有统一 observability core 的存量应用仍按原条件装配，不改变旧行为。

## 3. 验收证据

### 3.1 旧 tracing 兼容回归

```bash
mvn -q -f atlas-richie-component/pom.xml \
  -pl atlas-richie-tracing -am test
```

结果：通过；旧 tracing 的禁用和启用配置测试均通过。

### 3.2 依赖传递边界

直接依赖旧 tracing 的依赖树仍包含 Starter，但明确标记为 optional：

```bash
mvn -f atlas-richie-component/atlas-richie-tracing/pom.xml \
  dependency:tree \
  -Dincludes=io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter
```

关键结果：`opentelemetry-spring-boot-starter:2.31.1:compile (optional)`。

组件库间接引用旧 tracing 的依赖树不再出现应用级 Starter：

```bash
mvn -q -f atlas-richie-component/atlas-richie-document-parser/pom.xml \
  dependency:tree \
  -Dincludes=io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter
```

结果：命令成功且无匹配依赖输出。

### 3.3 旧 HTTP boundary 兼容测试

```bash
mvn -q -f atlas-richie-component/pom.xml \
  -pl atlas-richie-observability-parent,atlas-richie-web-parent/atlas-richie-web-core -am test
```

结果：通过。`TracingAutoConfigurationBoundaryTest` 验证统一 observability core 存在时没有
`OtelTracingInterceptor` Bean，web-core 既有测试同步通过。

### 3.4 Phase 3 汇总回归

```bash
mvn -q -f atlas-richie-component/pom.xml \
  -pl atlas-richie-observability-parent,atlas-richie-http-parent,atlas-richie-grpc,\
atlas-richie-nats,atlas-richie-mcp-parent,atlas-richie-web-parent/atlas-richie-web-core,\
atlas-richie-tracing -am test
```

结果：退出码 0；HTTP、gRPC、NATS、MCP、observability、web boundary 和旧 tracing 兼容回归
均通过。

### 3.5 静态检查

```bash
git diff --check
```

结果：通过。

## 4. 未由本子阶段关闭的事项

- 真实应用中 HTTP/gRPC/NATS/MCP 多层 instrumentation 的最终 Span 数量和父子关系；
- Foundry 真实 Gateway -> Orchestrator -> MCP -> 外部工具链路的 Trace-to-Logs/Metrics 查询；
- 存量服务移除旧 tracing 直接依赖；
- 基础依赖中全局 Actuator 的最终下沉和删除。
