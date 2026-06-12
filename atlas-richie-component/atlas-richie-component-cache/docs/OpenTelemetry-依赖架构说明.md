# OpenTelemetry 依赖架构说明

## 🎯 设计原则

**核心思想：保留功能，按需激活**

- ✅ cache 组件内部使用 OpenTelemetry API 实现链路追踪功能
- ✅ 但**不自动激活** Spring Boot 的 OpenTelemetry 自动配置
- ✅ 需要链路追踪的项目只需引入 starter 即可激活

## 📦 依赖分层

### 第一层：核心依赖（必需，不设为 optional）

这些依赖被 cache 组件内部使用，必须保留：

```xml
<!-- OpenTelemetry API -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <!-- 不设为 optional -->
</dependency>

<!-- OpenTelemetry SDK -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <!-- 不设为 optional -->
</dependency>

<!-- 其他核心依赖：semconv、导出器、trace SDK 等 -->
```

**为什么不设为 optional？**
- `RedisStreamManager` 的构造函数需要 `OpenTelemetry` 类型
- `RedisStreamTracingUtils` 等工具类使用 OpenTelemetry API
- 如果设为 optional，会导致 `NoClassDefFoundError`

### 第二层：自动配置层（optional）

这些依赖会触发 Spring Boot 自动配置和自动连接，设为 optional：

```xml
<!-- Spring Boot OpenTelemetry 集成 -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <optional>true</optional>  <!-- ✅ 设为 optional -->
</dependency>

<!-- Micrometer OTLP 集成 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-otlp</artifactId>
    <optional>true</optional>  <!-- ✅ 设为 optional -->
</dependency>
```

**为什么设为 optional？**
- `opentelemetry-spring-boot-starter` 会自动配置并尝试连接 OTLP endpoint
- `micrometer-registry-otlp` 会自动尝试导出 metrics 到 OTLP
- 设为 optional 后，不会被传递到依赖它的项目

## 🚀 使用场景

### 场景一：不需要链路追踪（默认）

**依赖配置**：
```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

**效果**：
- ✅ cache 组件正常工作
- ✅ 引入了 OpenTelemetry API/SDK（但不激活）
- ✅ 不会自动配置 OpenTelemetry
- ✅ 不会尝试连接 OTLP endpoint
- ✅ 没有连接错误

**实现原理**：
```
引入 cache 组件
  ├─ OpenTelemetry API/SDK (传递引入)
  ├─ opentelemetry-spring-boot-starter (optional，不传递)
  └─ micrometer-registry-otlp (optional，不传递)

结果：
  - @ConditionalOnClass(OpenTelemetry.class) ✅ 满足
  - RedisStreamTracingAutoConfiguration 不激活（需要配置）
  - Spring Boot OpenTelemetry 自动配置不存在 ✅
  - 没有自动连接行为 ✅
```

### 场景二：需要链路追踪

**依赖配置**：
```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>

<!-- 显式引入 starter 激活链路追踪 -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

**配置文件**：
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

**效果**：
- ✅ Spring Boot OpenTelemetry 自动配置激活
- ✅ Redis Stream 链路追踪功能启用
- ✅ 正常导出 traces 数据

### 场景三：同时需要 Traces 和 Metrics

**依赖配置**：
```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>

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

**配置文件**：
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

## 🏗️ 架构对比

### 之前的尝试（失败）：所有依赖都设为 optional

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <optional>true</optional>  <!-- ❌ 导致 NoClassDefFoundError -->
</dependency>
```

**问题**：
- `RedisStreamManager` 的构造函数需要 `OpenTelemetry` 类型
- 类加载时找不到 `io.opentelemetry.api.OpenTelemetry`
- 即使不使用链路追踪功能也会报错

### 当前方案（成功）：分层控制

| 依赖类型 | optional 设置 | 原因 |
|---------|--------------|------|
| opentelemetry-api | ❌ 否 | cache 组件内部使用 |
| opentelemetry-sdk | ❌ 否 | cache 组件内部使用 |
| opentelemetry-semconv | ❌ 否 | cache 组件内部使用 |
| opentelemetry-exporter-* | ❌ 否 | cache 组件内部使用 |
| opentelemetry-sdk-trace | ❌ 否 | cache 组件内部使用 |
| opentelemetry-sdk-extension-autoconfigure | ❌ 否 | cache 组件内部使用 |
| **opentelemetry-spring-boot-starter** | **✅ 是** | **会自动配置和连接** |
| **micrometer-registry-otlp** | **✅ 是** | **会自动导出 metrics** |

## 📊 依赖传递图

### 不需要链路追踪的项目

```
your-project
  └── richie-component-cache
        ├── opentelemetry-api (传递)
        ├── opentelemetry-sdk (传递)
        ├── opentelemetry-semconv (传递)
        ├── opentelemetry-exporter-* (传递)
        ├── opentelemetry-spring-boot-starter (optional，不传递) ✅
        └── micrometer-registry-otlp (optional，不传递) ✅

结果：有 API/SDK，但没有自动配置 → 不会连接
```

### 需要链路追踪的项目

```
your-project
  ├── richie-component-cache
  │     ├── opentelemetry-api (传递)
  │     ├── opentelemetry-sdk (传递)
  │     └── ... (其他核心依赖)
  └── opentelemetry-spring-boot-starter (显式引入) ✅

结果：有 API/SDK + 有 starter → 自动配置激活 → 正常导出
```

## 💡 设计优势

1. **零配置**
   - 不需要链路追踪的项目：无需任何配置
   - 不会有连接错误

2. **按需激活**
   - 需要链路追踪的项目：只需引入 starter
   - 简单明了

3. **向后兼容**
   - cache 组件的链路追踪功能保持不变
   - `RedisStreamManager` 正常工作

4. **最小依赖**
   - 不使用时不引入不必要的自动配置
   - 减少潜在的依赖冲突

## 🎓 最佳实践

### ✅ 推荐做法

**方式一：使用 JavaAgent（最推荐）**
```bash
# 无需在项目中引入任何 OpenTelemetry 依赖
java -javaagent:/path/to/opentelemetry-javaagent.jar \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -jar your-app.jar
```

**方式二：应用内集成**
```xml
<!-- 只需引入 starter -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

### ❌ 不推荐做法

**不要排除所有 OpenTelemetry 依赖**
```xml
<!-- ❌ 错误：会导致 NoClassDefFoundError -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
    <exclusions>
        <exclusion>
            <groupId>io.opentelemetry</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**如果确实不想引入任何 OpenTelemetry 依赖**
```xml
<!-- ✅ 正确：只排除会自动连接的组件 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
    <exclusions>
        <exclusion>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-spring-boot-starter</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-otlp</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

## 📚 相关文档

- [OpenTelemetry 快速开始](./OpenTelemetry-快速开始.md)
- [Redis Stream 链路追踪说明](../../atlas-richie-component-redis-streammq/docs/Redis-Stream-Tracing-透传说明.md)
- [CHANGELOG](./CHANGELOG-OpenTelemetry.md)

## 🔄 版本历史

- **5.0.1**：采用分层 optional 策略，解决自动连接问题
- **1.0.0**：初始版本，所有依赖都设为 optional（有问题）

