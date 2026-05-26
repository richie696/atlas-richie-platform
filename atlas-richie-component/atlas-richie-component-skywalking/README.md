# Richie SkyWalking Component

Apache SkyWalking 链路追踪组件依赖管理，提供 SkyWalking Java Agent 工具包，支持分布式链路追踪、性能监控、日志关联等功能。

## 📋 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [核心功能](#核心功能)
- [配置说明](#配置说明)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

---

## ✨ 功能特性

### 核心能力

- ✅ **分布式链路追踪**：自动追踪跨服务的调用链
- ✅ **性能监控**：监控方法执行时间、数据库查询时间等
- ✅ **日志关联**：自动关联日志和链路追踪信息
- ✅ **指标收集**：收集应用性能指标（APM）
- ✅ **多框架支持**：支持 Spring、Spring Boot、Spring Cloud 等

### 工具包支持

- ✅ **OkHttp 支持**：`apm-okhttp-common` - OkHttp 客户端追踪
- ✅ **Logback 支持**：`apm-toolkit-logback-1.x` - Logback 日志关联
- ✅ **Log4j2 支持**：`apm-toolkit-log4j-2.x` - Log4j2 日志关联
- ✅ **Micrometer 支持**：`apm-toolkit-micrometer-1.10` - Micrometer 指标集成
- ✅ **OpenTracing 支持**：`apm-toolkit-opentracing` - OpenTracing 标准支持
- ✅ **WebFlux 支持**：`apm-toolkit-webflux` - Spring WebFlux 追踪
- ✅ **Trace 工具**：`apm-toolkit-trace` - 手动追踪工具
- ✅ **Meter 工具**：`apm-toolkit-meter` - 指标收集工具
- ✅ **Kafka 支持**：`apm-toolkit-kafka` - Kafka 消息追踪

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-skywalking</artifactId>
</dependency>
```

### 2. 配置 SkyWalking Agent

#### 方式一：JVM 参数（推荐）

```bash
-javaagent:/path/to/skywalking-agent/skywalking-agent.jar
-Dskywalking.agent.service_name=your-service-name
-Dskywalking.collector.backend_service=localhost:11800
```

#### 方式二：环境变量

```bash
export SW_AGENT_NAME=your-service-name
export SW_AGENT_COLLECTOR_BACKEND_SERVICES=localhost:11800
export JAVA_OPTS="-javaagent:/path/to/skywalking-agent/skywalking-agent.jar"
```

### 3. 配置文件

创建 `skywalking-agent.config` 文件：

```properties
# Agent 配置
agent.service_name=${SW_AGENT_NAME:your-service-name}
agent.namespace=${SW_AGENT_NAMESPACE:}

# Collector 配置
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:localhost:11800}

# 日志配置
logging.level=INFO
logging.dir=${SW_LOGGING_DIR:logs}
logging.max_file_size=300000000
logging.max_history_files=10

# 采样配置
agent.sample_n_per_3_secs=-1  # -1 表示采样所有请求
```

### 4. 使用日志关联

#### Logback 配置

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdConverter">
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%thread] %-5level %logger{36} - %msg%n</pattern>
            </layout>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

#### Log4j2 配置

```xml
<!-- log4j2.xml -->
<Configuration>
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
    
    <Loggers>
        <Root level="INFO">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

### 5. 使用手动追踪

```java
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

@Service
public class OrderService {
    
    /**
     * 自动追踪方法
     */
    @Trace
    public Order createOrder(OrderRequest request) {
        // 获取 Trace ID
        String traceId = TraceContext.traceId();
        log.info("创建订单，Trace ID: {}", traceId);
        
        // 设置自定义标签
        ActiveSpan.tag("order.amount", String.valueOf(request.getAmount()));
        ActiveSpan.tag("order.userId", request.getUserId());
        
        // 记录日志事件
        ActiveSpan.info("开始创建订单");
        
        // 业务逻辑
        Order order = processOrder(request);
        
        ActiveSpan.info("订单创建完成");
        return order;
    }
    
    /**
     * 异步方法追踪
     */
    @Trace(async = true)
    public void asyncProcess(Order order) {
        // 异步处理逻辑
    }
}
```

---

## 🔧 核心功能

### 1. 自动追踪

组件自动追踪以下操作：

- **HTTP 请求**：Spring MVC、Spring WebFlux
- **数据库操作**：JDBC、MyBatis、Hibernate
- **消息队列**：Kafka、RabbitMQ
- **RPC 调用**：Dubbo、gRPC
- **HTTP 客户端**：OkHttp、HttpClient

### 2. 日志关联

通过日志关联，可以在日志中看到 Trace ID：

```
2025-01-09 10:30:00.123 [TID:abc123def456] [main] INFO  OrderService - 创建订单成功
```

### 3. 手动追踪

```java
import org.apache.skywalking.apm.toolkit.trace.*;

// 方式一：使用 @Trace 注解
@Trace
public void processOrder(Order order) {
    // 方法会自动被追踪
}

// 方式二：使用 TraceContext
public void processOrder(Order order) {
    String traceId = TraceContext.traceId();
    String segmentId = TraceContext.segmentId();
    int spanId = TraceContext.spanId();
}

// 方式三：使用 ActiveSpan
public void processOrder(Order order) {
    ActiveSpan.tag("order.id", order.getId());
    ActiveSpan.tag("order.amount", String.valueOf(order.getAmount()));
    ActiveSpan.info("处理订单");
    ActiveSpan.error(new RuntimeException("处理失败"));
}
```

### 4. 跨线程追踪

```java
import org.apache.skywalking.apm.toolkit.trace.RunnableWrapper;
import org.apache.skywalking.apm.toolkit.trace.CallableWrapper;

// Runnable 跨线程追踪
CompletableFuture.runAsync(RunnableWrapper.of(() -> {
    // 异步任务，会自动关联到父线程的 Trace
}));

// Callable 跨线程追踪
CompletableFuture.supplyAsync(CallableWrapper.of(() -> {
    // 异步任务，会自动关联到父线程的 Trace
    return result;
}));
```

### 5. 指标收集

```java
import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.Gauge;
import org.apache.skywalking.apm.toolkit.meter.Histogram;

@Service
public class OrderService {
    
    private final Counter orderCounter = Counter.build("order_total", "订单总数")
            .tag("type", "create")
            .build();
    
    private final Gauge orderGauge = Gauge.build("order_active", "活跃订单数")
            .build();
    
    public void createOrder(OrderRequest request) {
        // 增加计数器
        orderCounter.inc();
        
        // 更新指标
        orderGauge.setValue(getActiveOrderCount());
    }
}
```

---

## ⚙️ 配置说明

### Agent 配置

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| `agent.service_name` | `SW_AGENT_NAME` | - | 服务名称 |
| `agent.namespace` | `SW_AGENT_NAMESPACE` | - | 命名空间 |
| `agent.sample_n_per_3_secs` | `SW_AGENT_SAMPLE` | `-1` | 采样率（-1 表示全部采样） |
| `agent.span_limit_per_segment` | - | `300` | 每个 Segment 的最大 Span 数 |

### Collector 配置

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| `collector.backend_service` | `SW_AGENT_COLLECTOR_BACKEND_SERVICES` | `localhost:11800` | Collector 地址 |

### 日志配置

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| `logging.level` | `SW_LOGGING_LEVEL` | `INFO` | 日志级别 |
| `logging.dir` | `SW_LOGGING_DIR` | `logs` | 日志目录 |
| `logging.max_file_size` | - | `300000000` | 最大日志文件大小（字节） |
| `logging.max_history_files` | - | `10` | 最大历史日志文件数 |

### 插件配置

| 配置项 | 说明 |
|--------|------|
| `plugin.jdbc.trace_sql_parameters` | 是否追踪 SQL 参数 |
| `plugin.springmvc.collect_http_params` | 是否收集 HTTP 参数 |
| `plugin.http.http_headers_length_threshold` | HTTP 头最大长度 |

---

## 🎯 最佳实践

### 1. 服务命名

```properties
# 使用有意义的服务名称
agent.service_name=order-service
agent.namespace=production
```

### 2. 采样配置

```properties
# 生产环境：采样部分请求
agent.sample_n_per_3_secs=100

# 测试环境：采样所有请求
agent.sample_n_per_3_secs=-1
```

### 3. 日志关联

在日志配置中添加 `%tid` 或 `[%tid]` 来显示 Trace ID：

```xml
<!-- Logback -->
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%thread] %-5level %logger{36} - %msg%n</pattern>

<!-- Log4j2 -->
<PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%t] %-5level %logger{36} - %msg%n"/>
```

### 4. 手动追踪

对于重要的业务方法，使用 `@Trace` 注解进行追踪：

```java
@Trace(operationName = "createOrder")
public Order createOrder(OrderRequest request) {
    ActiveSpan.tag("order.amount", String.valueOf(request.getAmount()));
    // 业务逻辑
}
```

### 5. 跨线程追踪

异步任务使用 `RunnableWrapper` 或 `CallableWrapper` 包装：

```java
CompletableFuture.runAsync(RunnableWrapper.of(() -> {
    // 异步任务
}));
```

### 6. 性能优化

```properties
# 限制 Span 数量，避免性能影响
agent.span_limit_per_segment=300

# 禁用不需要的插件
plugin.mysql=false
plugin.redis=false
```

---

## ❓ 常见问题

### Q1: 如何查看链路追踪数据？

**A:** 
1. 启动 SkyWalking OAP Server
2. 访问 SkyWalking UI（默认：http://localhost:8080）
3. 在 UI 中查看服务拓扑、链路追踪、性能指标等

### Q2: Trace ID 在日志中不显示？

**A:** 
1. 确认已添加 SkyWalking Agent JVM 参数
2. 确认日志配置中包含 `%tid` 或 `[%tid]`
3. 确认使用了正确的日志框架（Logback 或 Log4j2）

### Q3: 如何禁用某些插件？

**A:** 在 `skywalking-agent.config` 中配置：

```properties
plugin.mysql=false
plugin.redis=false
plugin.kafka=false
```

### Q4: 如何自定义操作名称？

**A:** 使用 `@Trace` 注解：

```java
@Trace(operationName = "customOperationName")
public void customMethod() {
    // ...
}
```

### Q5: 如何追踪异步方法？

**A:** 使用 `@Trace(async = true)` 或 `RunnableWrapper`/`CallableWrapper`：

```java
@Trace(async = true)
public void asyncMethod() {
    // ...
}
```

### Q6: 采样率如何设置？

**A:** 
- `-1`：采样所有请求（测试环境）
- `0`：不采样
- `N`：每 3 秒采样 N 个请求（生产环境推荐 100-1000）

---

## 📖 参考文档

- [Apache SkyWalking 官方文档](https://skywalking.apache.org/docs/)
- [SkyWalking Java Agent 文档](https://skywalking.apache.org/docs/main/latest/en/setup/service-agent/java-agent/readme/)
- [SkyWalking 工具包文档](https://skywalking.apache.org/docs/main/latest/en/setup/service-agent/java-agent/application-toolkit-guide/)

---

## 📝 总结

Richie SkyWalking Component 提供了 SkyWalking Java Agent 工具包的依赖管理，支持分布式链路追踪、性能监控、日志关联等功能。通过合理配置和使用，可以构建完善的分布式系统可观测性。

**关键要点**：

1. **Agent 配置**：正确配置 Agent 参数，包括服务名称、Collector 地址等
2. **日志关联**：在日志配置中添加 Trace ID，便于问题排查
3. **手动追踪**：对重要业务方法使用 `@Trace` 注解
4. **跨线程追踪**：异步任务使用 `RunnableWrapper`/`CallableWrapper` 包装
5. **性能优化**：合理设置采样率和 Span 限制，避免性能影响

