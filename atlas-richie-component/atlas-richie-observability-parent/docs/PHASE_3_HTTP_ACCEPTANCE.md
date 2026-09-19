# Phase 3-HTTP 子阶段验收记录

## 1. 结论

Phase 3 的 HTTP Client/SSE 子阶段已通过组件边界验收，可以开启下一个 gRPC 子阶段。

本记录只关闭 HTTP 组件自身的协议迁移，不代表 Foundry 应用级完整链路已经完成。应用级 Gateway -> Orchestrator -> 下游真实请求、跨服务 Grafana 关联和总方案验收，统一留到所有组件阶段完成后，按 Foundry 临时改造方案执行。

## 2. 已验收内容

- JDK HttpClient、OkHttp、Apache HttpClient 5、Spring RestClient 四种 Provider 统一使用 `ObservabilityHttpClient`；
- 出站请求创建唯一 `CLIENT` Span；
- 当前 OTel Context 注入 W3C `traceparent`/`tracestate`；
- 同步、回调异步、`CompletableFuture` 均执行观测生命周期；
- SSE Span 覆盖连接建立、服务端关闭、失败和主动关闭；
- 依赖指标使用 `dependency_type`、目标 host、operation、status 低基数维度；
- 观测关闭或没有统一 OTel runtime 时保留原 Provider 行为；
- HTTP core 只依赖 observability-core，不引入 Actuator、Exporter 或完整 Starter。

## 3. 验收证据

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-http-parent -am \\
  -Dtest=ObservabilityHttpClientTest \\
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。覆盖 Client Span、W3C header 注入、目标 host/operation 指标、Future 完成路径和禁用分支。

```bash
mvn -q -f atlas-richie-component/pom.xml \\
  -pl atlas-richie-http-parent -am test
```

结果：通过。HTTP core、JDK、OkHttp、HttpClient 5、RestClient 及现有 SSE 回归测试均通过。

```bash
git diff --check
```

结果：通过。

## 4. 下一阶段门禁

只有本记录成立后，才开启 gRPC 子阶段。gRPC 子阶段必须单独形成实现、测试和验收记录，至少覆盖：

1. Metadata 中的 W3C inject/extract；
2. Client/Server/Streaming 生命周期；
3. 超时、异常、取消、重试和关闭；
4. 与 Spring OTel instrumentation 的重复 Span 检查；
5. `DependencyMetricsRecorder` 的低基数指标；
6. 旧 `Grpc*TracingInterceptor` 与新统一契约的兼容边界。

