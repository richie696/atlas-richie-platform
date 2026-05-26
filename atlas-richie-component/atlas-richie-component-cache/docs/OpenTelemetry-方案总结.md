# OpenTelemetry 方案总结

## ✅ 最终采用方案

**分层 optional 策略**：保留核心依赖，仅自动配置层设为 optional

### 📦 依赖配置

```xml
<!-- 核心依赖：不设为 optional（cache 组件内部使用）-->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
</dependency>

<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
</dependency>

<!-- ... 其他核心依赖（SDK、导出器等）... -->

<!-- 自动配置层：设为 optional（避免自动连接）-->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <optional>true</optional>  <!-- ✅ 关键 -->
</dependency>

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-otlp</artifactId>
    <optional>true</optional>  <!-- ✅ 关键 -->
</dependency>
```

## 🎯 设计优势

### 1. **零配置，无错误**

**不需要链路追踪的项目**（默认情况）：
```xml
<!-- 只需引入 cache 组件 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

**效果**：
- ✅ 引入了 OpenTelemetry API/SDK（cache 组件内部使用）
- ✅ 不会激活 Spring Boot OpenTelemetry 自动配置
- ✅ 不会尝试连接 OTLP endpoint
- ✅ 没有 `Failed to connect to localhost:4318` 错误
- ✅ 应用正常启动

### 2. **按需激活，简单明了**

**需要链路追踪的项目**：
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

**效果**：
- ✅ Spring Boot OpenTelemetry 自动配置激活
- ✅ Redis Stream 链路追踪功能启用
- ✅ 正常导出 traces 数据

### 3. **功能完整，向后兼容**

- ✅ cache 组件的所有功能保持不变
- ✅ `RedisStreamManager` 正常工作
- ✅ `RedisStreamTracingUtils` 正常工作
- ✅ 链路追踪功能完整可用

## 📊 对比分析

| 方案 | 核心依赖 | Starter | 优点 | 缺点 |
|------|---------|---------|------|------|
| **方案一**<br>全部 optional | optional | optional | 完全不传递 | ❌ `NoClassDefFoundError` |
| **方案二**<br>全部排除 | 排除 | 排除 | 不引入任何依赖 | ❌ `NoClassDefFoundError` |
| **方案三**<br>分层 optional<br>**✅ 最终方案** | 保留 | optional | ✅ 功能完整<br>✅ 无自动连接<br>✅ 按需激活 | 引入核心依赖<br>（不影响使用）|

## 🔍 技术细节

### 为什么核心依赖不能设为 optional？

`RedisStreamManager` 的构造函数需要 `OpenTelemetry` 类型：

```java
@RequiredArgsConstructor
public class RedisStreamManager {
    private final OpenTelemetry openTelemetry;  // ← 构造函数参数
    // ...
}
```

如果 `opentelemetry-api` 设为 optional：
1. 依赖项目不会引入这个依赖
2. Java 加载 `RedisStreamManager` 类时找不到 `io.opentelemetry.api.OpenTelemetry`
3. 抛出 `NoClassDefFoundError`
4. **即使不使用链路追踪功能也会报错**

### 为什么 starter 要设为 optional？

`opentelemetry-spring-boot-starter` 包含自动配置：
- 自动检测到 OpenTelemetry 类存在
- 自动配置并尝试连接 OTLP endpoint（默认 localhost:4318）
- 导致 `Failed to connect` 错误

设为 optional 后：
- 不会被传递到依赖项目
- `@ConditionalOnClass(OpenTelemetryAutoConfiguration.class)` 条件不满足
- 自动配置不会激活
- 不会尝试连接

## 📝 使用示例

### 示例一：默认使用（无链路追踪）

**pom.xml**：
```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

**application.yml**：
```yaml
# 无需任何配置
```

**验证**：
```bash
✅ 应用启动成功
✅ 没有 OpenTelemetry 相关错误
✅ 没有连接 localhost:4318 的错误
```

### 示例二：启用链路追踪（应用内集成）

**pom.xml**：
```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>

<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

**application.yml**：
```yaml
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

**验证**：
```bash
✅ 应用启动成功
✅ Redis Stream 链路追踪启用
✅ 正常导出 traces 数据到 OTLP Collector
```

### 示例三：使用 JavaAgent（最推荐）

**pom.xml**：
```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
<!-- 无需引入 starter -->
```

**启动命令**：
```bash
java -javaagent:/path/to/opentelemetry-javaagent.jar \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.resource.attributes=service.name=my-service \
     -jar your-app.jar
```

**验证**：
```bash
✅ 应用启动成功
✅ 自动 instrumentation 生效
✅ 完整的链路追踪（HTTP、JDBC、Redis 等）
```

## 🎓 最佳实践

### ✅ 推荐做法

1. **开发环境**：不引入 starter，无链路追踪
2. **测试环境**：引入 starter，启用链路追踪
3. **生产环境**：使用 JavaAgent，完整可观测性

### ❌ 不推荐做法

**不要排除所有 OpenTelemetry 依赖**：
```xml
<!-- ❌ 会导致 NoClassDefFoundError -->
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

**不要将所有依赖设为 optional**：
```xml
<!-- ❌ cache 组件 pom.xml 中不要这样做 -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <optional>true</optional>  <!-- ❌ 错误 -->
</dependency>
```

## 📚 相关文档

- [OpenTelemetry 快速开始](./OpenTelemetry-快速开始.md)
- [OpenTelemetry 依赖架构说明](./OpenTelemetry-依赖架构说明.md)
- [Redis Stream 链路追踪说明](./Redis-Stream-Tracing-透传说明.md)

## 🔄 版本历史

- **5.0.1**：采用分层 optional 策略，完美解决
- **5.0.0**：初始尝试（全部 optional，有问题）

## ✨ 总结

这个方案完美平衡了以下需求：
1. ✅ cache 组件功能完整
2. ✅ 默认无自动连接
3. ✅ 按需激活简单
4. ✅ 向后兼容

**简单、优雅、有效！** 🎉

