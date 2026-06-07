# Richie Component ThreadPool

## 概述

`richie-component-threadpool` 是Richie平台动态线程池组件，基于 Dynamic-TP 提供动态线程池管理能力，支持线程池参数动态调整、监控告警、适配多种框架（OkHttp、RabbitMQ、Tomcat、Undertow、Jetty等）。

## 核心特性

- ✅ **动态线程池** - 基于 Dynamic-TP，支持线程池参数动态调整
- ✅ **多框架适配** - 支持 OkHttp、RabbitMQ、Tomcat、Undertow、Jetty 等
- ✅ **监控告警** - 支持线程池监控指标采集和告警
- ✅ **Nacos 配置** - 支持通过 Nacos 动态配置线程池参数
- ✅ **异步线程池** - 提供自定义异步线程池，支持 MDC 上下文传播
- ✅ **自动配置** - Spring Boot 自动配置，开箱即用

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-threadpool</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. 配置

```yaml
spring:
  dynamic:
    tp:
      # 是否启用动态线程池（默认：true）
      enabled: true
      # 是否开启监控指标采集（默认：false）
      enabled-collect: true
      # 监控数据采集器类型（logging | micrometer | internal_logging），默认micrometer
      collector-types: micrometer,logging
      # 监控时间间隔（报警判断、指标采集），默认5s
      monitor-interval: 5
      # Nacos配置（可选）
      etcd:
        authority:
      # OkHttp3 线程池配置
      okhttp3-tp:
        - thread-pool-name: okHttpTp
          core-pool-size: 10
          maximum-pool-size: 200
          keep-alive-time: 60
      # Tomcat WebServer 线程池配置
      tomcat-tp:
        thread-pool-name: tomcatTp
        core-pool-size: 10
        maximum-pool-size: 200
        keep-alive-time: 60
      # Undertow WebServer 线程池配置
      undertow-tp:
        thread-pool-name: undertowTp
        core-pool-size: 10
        maximum-pool-size: 200
        keep-alive-time: 60
      # Jetty WebServer 线程池配置
      jetty-tp:
        thread-pool-name: jettyTp
        core-pool-size: 10
        maximum-pool-size: 200
        keep-alive-time: 60
      # RabbitMQ 线程池配置
      rabbitmq-tp:
        - thread-pool-name: rabbitmqTp
          core-pool-size: 10
          maximum-pool-size: 200
          keep-alive-time: 60

# 自定义异步线程池配置
platform:
  component:
    threadpool:
      async:
        # 是否启动异步线程池（默认：false）
        enable: true
        # 核心线程数（默认：CPU核心数 * 2）
        corePoolSize: 10
        # 线程池最大线程数（默认：CPU核心数 * 4）
        maxPoolSize: 200
        # 线程队列最大线程数（默认：2000）
        queueCapacity: 2000
        # 自定义线程名前缀（默认：customtl-）
        threadNamePrefix: customtl-
        # 线程池中线程最大空闲时间（默认：60秒）
        keepAliveSeconds: 60
        # 核心线程是否允许超时（默认：false）
        allowCoreThreadTimeOut: false
        # IOC容器关闭时是否阻塞等待剩余的任务执行完成（默认：false）
        waitForTasksToCompleteOnShutdown: true
        # 阻塞IOC容器关闭的时间（默认：120秒）
        awaitTerminationSeconds: 120
        # 是否覆盖@Async注解的默认线程池（默认：false）
        overrideDefaultAsync: true
        # 是否启用线程上下文传播参数（默认：false）
        enableTaskDecorator: true
        # 是否启用DynamicDataSourceContextHolder传播参数（默认：false）
        enableDynamicDataSourceContextHolder: true
```

### 3. 使用

#### 使用 @Async 注解

```java
@Service
public class AsyncService {
    
    @Async  // 使用自定义异步线程池
    public void asyncMethod() {
        // 异步执行的任务
        log.info("异步执行任务");
    }
}
```

#### 使用 Dynamic-TP 动态线程池

```java
@Service
@RequiredArgsConstructor
public class ThreadPoolService {
    
    private final ThreadPoolExecutor okHttpTp;
    
    public void executeTask() {
        okHttpTp.execute(() -> {
            // 执行任务
            log.info("使用动态线程池执行任务");
        });
    }
}
```

## 配置说明

### Dynamic-TP 配置

Dynamic-TP 支持多种线程池配置：

#### 1. OkHttp3 线程池

```yaml
spring:
  dynamic:
    tp:
      okhttp3-tp:
        - thread-pool-name: okHttpTp
          core-pool-size: 10
          maximum-pool-size: 200
          keep-alive-time: 60
          queue-capacity: 1000
          reject-policy: CallerRunsPolicy
```

#### 2. Tomcat WebServer 线程池

```yaml
spring:
  dynamic:
    tp:
      tomcat-tp:
        thread-pool-name: tomcatTp
        core-pool-size: 10
        maximum-pool-size: 200
        keep-alive-time: 60
```

#### 3. Undertow WebServer 线程池

```yaml
spring:
  dynamic:
    tp:
      undertow-tp:
        thread-pool-name: undertowTp
        core-pool-size: 10
        maximum-pool-size: 200
        keep-alive-time: 60
```

#### 4. Jetty WebServer 线程池

```yaml
spring:
  dynamic:
    tp:
      jetty-tp:
        thread-pool-name: jettyTp
        core-pool-size: 10
        maximum-pool-size: 200
        keep-alive-time: 60
```

#### 5. RabbitMQ 线程池

```yaml
spring:
  dynamic:
    tp:
      rabbitmq-tp:
        - thread-pool-name: rabbitmqTp
          core-pool-size: 10
          maximum-pool-size: 200
          keep-alive-time: 60
```

### 自定义异步线程池配置

自定义异步线程池提供以下配置项：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `enable` | 是否启用异步线程池 | `false` |
| `corePoolSize` | 核心线程数 | `CPU核心数 * 2` |
| `maxPoolSize` | 最大线程数 | `CPU核心数 * 4` |
| `queueCapacity` | 队列容量 | `2000` |
| `threadNamePrefix` | 线程名前缀 | `customtl-` |
| `keepAliveSeconds` | 线程空闲时间（秒） | `60` |
| `allowCoreThreadTimeOut` | 核心线程是否允许超时 | `false` |
| `waitForTasksToCompleteOnShutdown` | 关闭时是否等待任务完成 | `false` |
| `awaitTerminationSeconds` | 等待任务完成的时间（秒） | `120` |
| `overrideDefaultAsync` | 是否覆盖@Async默认线程池 | `false` |
| `enableTaskDecorator` | 是否启用MDC上下文传播 | `false` |
| `enableDynamicDataSourceContextHolder` | 是否启用数据源上下文传播 | `false` |

## 功能特性

### 1. 动态线程池调整

Dynamic-TP 支持通过 Nacos 动态调整线程池参数，无需重启应用：

```yaml
# Nacos 配置
spring:
  dynamic:
    tp:
      nacos:
        data-id: dynamic-tp-config
        group: DEFAULT_GROUP
        namespace: public
```

### 2. 监控告警

Dynamic-TP 支持多种监控指标采集：

- **Micrometer** - 集成 Prometheus 监控
- **Logging** - 日志输出监控指标
- **Internal Logging** - 内部日志监控

### 3. MDC 上下文传播

自定义异步线程池支持 MDC 上下文传播，确保日志追踪信息正确传递：

```java
@Async
public void asyncMethod() {
    // MDC 中的 traceId、spanId 等信息会自动传播
    log.info("异步执行任务");
}
```

### 4. 数据源上下文传播

支持动态数据源上下文传播，确保异步任务使用正确的数据源：

```java
@Async
public void asyncMethod() {
    // DynamicDataSourceContextHolder 中的数据源信息会自动传播
    // 异步任务会使用正确的数据源
}
```

## 最佳实践

1. **线程池大小设置**
   - CPU 密集型任务：`核心线程数 = CPU核心数 + 1`
   - IO 密集型任务：`核心线程数 = CPU核心数 * 2`
   - 混合型任务：根据实际情况调整

2. **队列容量设置**
   - 根据业务场景设置合理的队列容量
   - 避免队列过大导致内存溢出
   - 考虑使用有界队列

3. **监控告警**
   - 启用监控指标采集
   - 配置合理的告警阈值
   - 定期检查线程池使用情况

4. **优雅关闭**
   - 启用 `waitForTasksToCompleteOnShutdown`
   - 设置合理的 `awaitTerminationSeconds`
   - 确保任务能够正常完成

5. **上下文传播**
   - 启用 MDC 上下文传播，确保日志追踪
   - 启用数据源上下文传播，确保数据源正确

## 常见问题

### Q: 如何动态调整线程池参数？

A: 通过 Nacos 配置中心动态修改线程池配置，Dynamic-TP 会自动应用新配置。

### Q: 如何监控线程池状态？

A: 启用 `enabled-collect: true` 和 `collector-types: micrometer`，通过 Prometheus 监控线程池指标。

### Q: 如何确保异步任务的日志追踪？

A: 启用 `enableTaskDecorator: true`，MDC 上下文会自动传播到异步任务。

### Q: 如何确保异步任务使用正确的数据源？

A: 启用 `enableDynamicDataSourceContextHolder: true`，数据源上下文会自动传播到异步任务。

### Q: 如何覆盖 @Async 的默认线程池？

A: 设置 `overrideDefaultAsync: true`，所有 `@Async` 注解都会使用自定义线程池。

## 相关文档

- [Dynamic-TP 官方文档](https://dynamictp.cn/)
- [Spring Async 文档](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

