# Phase 4.1 验收记录：Foundry 公共依赖与环境配置收敛

## 1. 结论

Phase 4.1 已通过。Foundry 的标准 Spring Boot 服务现在通过唯一的
`atlas-richie-observability-spring-boot-starter` 获得 Trace、Metrics、Logs、Actuator、JVM
和统一总开关；不再通过业务服务 POM 组合旧 tracing、直接 OTel Starter、独立 Actuator 和
Prometheus Registry。

本记录只关闭依赖和配置子阶段，不代表服务已完成运行时启动、真实请求或后端查询验收。

## 2. 改造范围

### 2.1 公共服务依赖

修改 `atlas-foundry-ai/foundry-foundation/service-dependencies/pom.xml`：

- 删除全局直接 `spring-boot-starter-actuator`；
- 删除全局 `atlas-richie-tracing`；
- 删除全局直接 `opentelemetry-spring-boot-starter`；
- 删除全局直接 `micrometer-registry-prometheus`；
- 增加唯一完整入口 `atlas-richie-observability-spring-boot-starter`。

该 starter 内部聚合 Actuator、Micrometer/Prometheus、官方 OTel Spring Boot runtime、
结构化日志和平台统一配置，不要求业务服务重复声明这些依赖。

### 2.2 叶子服务依赖

清理了标准服务 POM 中的重复声明，覆盖 Admin、Audit、Learning、MCP Discovery、MCP Gateway、
Portal、SMS、Frontdoor、Orchestrator 和 Knowledge。

Gateway 与 MCP Mock 不继承 Foundry `service-dependencies`，因此分别显式增加完整 starter，
并删除它们原先的 Actuator、旧 tracing、直接 OTel Starter 和独立 Prometheus 组合。

### 2.3 配置和部署

更新：

- `config/nacos/shared/platform-observability.yaml`：默认完整 signal 使用 OTLP，标准 W3C
  propagator，增加本地环境 Resource 默认值；
- `deploy/k8s/base/infrastructure/observability-config.yaml`：增加
  `OTEL_SDK_DISABLED=false`、OTLP traces/metrics/logs、`tracecontext,baggage`、
  `parentbased_always_on` 和环境 Resource；
- 继续保留 `OTEL_SDK_DISABLED=true` 作为单配置全量关闭 Trace/Metrics/Logs 的降级路径。

## 3. 验收证据

### 3.1 POM 静态检查

```bash
rg -n "<artifactId>(atlas-richie-tracing|opentelemetry-spring-boot-starter|spring-boot-starter-actuator|micrometer-registry-prometheus)</artifactId>" \
  -g 'pom.xml' --glob '!**/.flattened-pom.xml'
```

结果：无匹配。业务服务只通过完整 observability starter 进入应用级观测运行时。

### 3.2 Maven POM 解析

```bash
mvn -q -f pom.xml -DskipTests validate
```

结果：通过。

### 3.3 Foundry 全仓编译

```bash
mvn -q -DskipTests compile
```

结果：通过。首次执行曾因本地 Maven 仓未安装新 starter 子模块而无法解析；补装
observability starter reactor 后，使用同一命令重新执行并通过。该环境问题不构成代码失败。

### 3.4 静态差异检查

```bash
git diff --check
```

结果：通过。

## 4. 未关闭事项

- 各服务实际启动时的 Resource、Sampler、Exporter 和启动诊断日志；
- 开启/关闭总开关的真实服务请求和无 exporter 连接证据；
- Gateway、Orchestrator、MCP、Knowledge、Channel 的真实跨进程传播；
- Loki/Tempo/Prometheus/Grafana 的应用级关联检索。
