# OpenTelemetry 快速开始

## ⚡ 重要说明

从 **5.0.1 版本**开始，采用**分层 optional 策略**：

**核心设计：**
- ✅ OpenTelemetry **核心依赖**（API/SDK）保留，cache 组件内部使用
- ✅ OpenTelemetry **自动配置**（spring-boot-starter）设为 optional，避免自动连接
- ✅ 需要链路追踪时，只需引入 starter 即可激活

**优势：**
- ✅ 默认不会自动连接 OTLP endpoint，不会有连接错误
- ✅ cache 组件的链路追踪功能保持完整
- ✅ 按需激活，简单明了

详见：[OpenTelemetry 依赖架构说明](./OpenTelemetry-依赖架构说明.md)

---

## 默认行为（无需配置）

引入 `richie-component-cache` 时，**不会激活** OpenTelemetry 自动配置：

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

✅ 结果：
- 引入了 OpenTelemetry API/SDK（cache 组件内部使用）
- 但不会自动配置和连接
- 没有连接错误
- 应用正常启动

### ✅ 启用链路追踪

#### 方式一：使用 JavaAgent（最推荐）

**无需修改代码和依赖**，只需启动时添加参数：

```bash
java -javaagent:/path/to/opentelemetry-javaagent.jar \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.resource.attributes=service.name=my-service \
     -jar your-app.jar
```

#### 方式二：应用内集成

**添加 starter 依赖**：
```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

**配置链路追踪**：
```yaml
# 启用 Redis Stream 链路追踪
platform:
  cache:
    redis:
      stream:
        tracing:
          enabled: true
          service-name: my-service
          exporters:
            - otlp
          otlp:
            endpoint: http://localhost:4317
```

#### 方式三：同时启用 Traces 和 Metrics

**添加依赖**：
```xml
<!-- Traces -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>

<!-- Metrics -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-otlp</artifactId>
</dependency>
```

**配置**：
```yaml
# Traces 配置
platform:
  cache:
    redis:
      stream:
        tracing:
          enabled: true

# Metrics 配置
management:
  otlp:
    metrics:
      enabled: true
      endpoint: http://localhost:4318/v1/metrics
```

## 配置说明

### 核心配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `management.otlp.metrics.enabled` | boolean | `false` | 是否启用 OTLP 导出（主开关） |
| `management.otlp.metrics.endpoint` | String | - | OTLP Collector 地址 |
| `management.otlp.metrics.service-name` | String | `redis-stream-service` | 服务名称 |
| `management.otlp.metrics.timeout-seconds` | int | `30` | 超时时间（秒） |

### 配置示例

#### 本地开发（默认）

```yaml
# 无需配置
```

#### 测试环境

```yaml
management:
  otlp:
    metrics:
      enabled: true
      endpoint: http://otel-collector.test.svc:4318/v1/metrics
      service-name: my-service-test
```

#### 生产环境

```yaml
management:
  otlp:
    metrics:
      enabled: true
      endpoint: http://otel-collector.prod.svc:4318/v1/metrics
      service-name: my-service-prod
      timeout-seconds: 10
```

## 启动 OpenTelemetry Collector（可选）

如果你想在本地测试 OTLP 导出，可以快速启动一个 Collector：

### Docker 方式

```bash
# 创建配置文件 otel-collector-config.yaml
cat > otel-collector-config.yaml <<EOF
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318
      grpc:
        endpoint: 0.0.0.0:4317

exporters:
  logging:
    loglevel: debug

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [logging]
    metrics:
      receivers: [otlp]
      exporters: [logging]
    logs:
      receivers: [otlp]
      exporters: [logging]
EOF

# 启动 Collector
docker run -d \
  --name otel-collector \
  -p 4318:4318 \
  -p 4317:4317 \
  -v $(pwd)/otel-collector-config.yaml:/etc/otel-collector-config.yaml \
  otel/opentelemetry-collector:latest \
  --config=/etc/otel-collector-config.yaml
```

### 验证 Collector

```bash
# 检查 HTTP 端口
curl http://localhost:4318/v1/metrics

# 查看日志
docker logs -f otel-collector
```

## 工作原理

1. **OpenTelemetrySdkAutoConfiguration**
   - 在 Spring Boot 启动早期执行（`@PostConstruct`）
   - 根据 `management.otlp.metrics.enabled` 开关注入环境属性
   - 使用 `@AutoConfigureBefore` 确保优先于 OpenTelemetry 自动配置

2. **环境属性注入**
   ```java
   MapPropertySource propertySource = new MapPropertySource(
       "openTelemetrySdkDefaults", otelProperties);
   environment.getPropertySources().addFirst(propertySource);
   ```

3. **配置优先级**
   ```
   用户配置 > 组件默认配置 > OpenTelemetry 默认配置
   ```

## 对比其他方案

### 方案一：手动配置 YAML（旧方式）

```yaml
# 需要配置多个属性，容易遗漏
otel:
  sdk:
    disabled: true
  traces:
    exporter: none
  metrics:
    exporter: none
  logs:
    exporter: none
management:
  otlp:
    metrics:
      export:
        enabled: false
```

❌ 缺点：配置繁琐、容易遗漏、维护成本高

### 方案二：移除依赖

```xml
<!-- 需要时再加，不需要时移除 -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

❌ 缺点：频繁修改依赖、影响构建、不灵活

### 方案三：组件内置配置（新方式）✅

```yaml
# 无需配置，或仅需一个开关
management:
  otlp:
    metrics:
      enabled: false  # 默认值，可省略
```

✅ 优点：
- 零配置即可安全运行
- 一个开关控制所有行为
- 按需启用，配置简单
- 内置智能默认值

## 常见问题

### Q: 是否需要移除 `opentelemetry-spring-boot-starter` 依赖？

A: 不需要。组件会自动管理其行为，保留依赖即可。

### Q: 如果我使用 Java Agent 怎么办？

A: Java Agent 的配置优先级更高，会覆盖应用内配置。建议二选一使用。

### Q: 能否只导出 Traces 不导出 Metrics？

A: 目前通过 `management.otlp.metrics.enabled` 统一控制。如需细粒度控制，可手动设置：
```yaml
otel:
  traces:
    exporter: otlp
  metrics:
    exporter: none
  logs:
    exporter: none
```

### Q: 支持 gRPC 协议吗？

A: 默认使用 HTTP 协议（端口 4318）。如需 gRPC（端口 4317），需额外配置：
```yaml
otel:
  exporter:
    otlp:
      protocol: grpc
```

## 更多文档

- [OpenTelemetry 自动配置详细说明](./OpenTelemetry-自动配置说明.md)
- [Redis Stream 使用指南](./Redis-Stream-使用指南.md)

## 反馈

如有问题或建议，请联系：richie696

