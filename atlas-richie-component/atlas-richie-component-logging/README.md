# Richie Logging Component

基于 AOP 的访问日志和链路追踪组件，提供自动化的请求日志记录、方法追踪、多存储方式（文件/Redis/消息队列/数据库）等功能。

## 📋 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [核心功能](#核心功能)
- [配置说明](#配置说明)
- [生命周期回调函数](#生命周期回调函数)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

---

## ✨ 功能特性

### 核心能力

- ✅ **访问日志记录**：自动记录 HTTP 请求的请求参数、响应结果、执行时间等信息
- ✅ **链路追踪**：支持方法级别的链路追踪，记录方法调用链和执行时间
- ✅ **多存储方式**：支持文件、Redis、消息队列（Kafka）、数据库等多种存储方式
- ✅ **自动持久化**：支持自动批量持久化到数据库，提升性能
- ✅ **操作人追踪**：自动从 JWT Token 中提取操作人信息
- ✅ **请求/响应体控制**：支持控制是否记录请求/响应体，支持大小限制

### 高级特性

- ✅ **全局切面控制**：支持全局启用或按方法启用日志记录
- ✅ **数据库表自动创建**：支持 Liquibase 自动创建数据库表结构
- ✅ **批量处理**：数据库持久化支持批量处理，提升性能
- ✅ **异常处理**：完善的异常处理和日志记录
- ✅ **性能监控**：记录请求执行时间，便于性能分析
- ✅ **生命周期回调**：支持在日志记录的不同阶段执行自定义逻辑（记录前、记录后、持久化前、异常处理）

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-logging</artifactId>
</dependency>

<!-- 如果需要使用 Redis 存储 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>

<!-- 如果需要使用消息队列存储 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-messaging</artifactId>
</dependency>
```

### 2. 配置日志组件

```yaml
# application.yml
platform:
  component:
    logging:
      # 是否启用操作日志
      enable: true
      # 记录类型：FILE（文件）、REDIS（缓存）、MQ（消息队列）
      record-type: FILE
      # 是否启用请求参数体持久化
      request-body-persistent: true
      # 是否限制请求参数体大小
      request-body-size-limit: false
      # 请求参数体最大字符长度
      request-body-max-length: 200
      # 是否启用响应参数体持久化
      response-body-persistent: true
      # 是否限制响应参数体大小
      response-body-size-limit: false
      # 响应参数体最大字符长度
      response-body-max-length: 200
      # 是否启用数据库持久化
      db-persistent: true
      # 持久化数据库时一次获取的数据量
      db-batch-size: 500
      # 记录类型为消息队列时，消息队列的 topic 名称
      mq-topic-name: access-log-out-0
      # 日志保存到redis时临时暂存的路径
      cache-access-log-key: platform:access-log
      # 启用全局切点，进入Controller的所有请求都会被拦截
      enable-global-advice: false
      # 是否打印异常信息
      print-exception: false
      # 数据库模式管理配置
      schema:
        # 是否启用自动 DDL
        enable-auto-ddl: false
        # 表前缀
        table-prefix: ""
        # 访问日志表名
        access-log-table: access_log_info
        # 是否启用 Liquibase 迁移
        enable-liquibase: true
        # Liquibase 变更日志文件路径
        liquibase-change-log: classpath:db/changelog/db.changelog-master.yaml
        # Liquibase 是否仅生成 SQL 而不执行（干运行模式）
        liquibase-dry-run: false
```

### 3. 使用访问日志

#### 方式一：使用注解（推荐）

```java
import annotations.com.richie.component.logging.AccessLog;

@RestController
public class UserController {

    @PostMapping("/users")
    @AccessLog(value = "创建用户", persistent = true)  // 记录日志并持久化到数据库
    public ResultVO<User> createUser(@RequestBody CreateUserRequest request) {
        // 业务逻辑
        return ResultVO.success(user);
    }

    @GetMapping("/users/{id}")
    @AccessLog("查询用户")  // 仅记录日志，不持久化
    public ResultVO<User> getUser(@PathVariable String id) {
        // 业务逻辑
        return ResultVO.success(user);
    }
}
```

#### 方式二：全局启用

```yaml
platform:
  component:
    logging:
      enable-global-advice: true  # 所有 Controller 方法都会记录日志
```

### 4. 使用链路追踪

```java
import annotations.com.richie.component.logging.LogTrace;
import annotations.com.richie.component.logging.LogMethodTrace;

@Service
@LogTrace("用户服务")  // 类级别注解
public class UserServiceImpl {

    @LogMethodTrace(value = "创建用户", level = LogLevelEnum.INFO, ignoreArgs = false)
    public User createUser(CreateUserRequest request) {
        // 业务逻辑
        return user;
    }

    @LogMethodTrace(value = "查询用户", level = LogLevelEnum.DEBUG)
    public User getUser(String id) {
        // 业务逻辑
        return user;
    }
}
```

---

## 🔧 核心功能

### 1. 访问日志（AccessLog）

访问日志自动记录以下信息：

- **请求信息**：URL、HTTP 方法、请求参数、请求体
- **响应信息**：响应状态码、响应体
- **操作人信息**：操作人ID、操作人名称、租户ID（从 JWT Token 提取）
- **时间信息**：操作时间、执行耗时
- **其他信息**：IP 地址、请求头信息

#### 注解参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 日志描述 |
| `persistent` | boolean | `false` | 是否持久化到数据库 |

#### 存储方式

```yaml
platform:
  component:
    logging:
      record-type: FILE  # FILE、REDIS、MQ
```

- **FILE**：记录到日志文件（通过 SLF4J）
- **REDIS**：记录到 Redis Hash（使用 `cache-access-log-key` 作为 key 前缀）
- **MQ**：发送到消息队列（使用 `mq-topic-name` 作为 topic）

### 2. 链路追踪（LogTrace）

链路追踪支持方法级别的调用链追踪，记录以下信息：

- **类信息**：类名、类标签
- **方法信息**：方法名、方法标签、代码行号（通过 ASM 字节码分析获取）
- **参数信息**：方法参数（可选）
- **返回值**：方法返回值（可选）
- **执行时间**：开始时间、结束时间、耗时
- **线程信息**：线程ID、线程名称
- **异常信息**：异常堆栈、异常行号

**默认切点规则**：
- 默认切点：`execution(public * com.richie..*.service..*ServiceImpl.*(..))`
- 需要同时满足以下条件才会生效：
  1. 类上有 `@LogTrace` 注解
  2. 方法上有 `@LogMethodTrace` 注解
- 如果类上没有 `@LogTrace` 注解，即使方法上有 `@LogMethodTrace` 注解也不会生效

#### 注解参数

**@LogTrace（类级别）**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 日志标签 |

**@LogMethodTrace（方法级别）**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 日志标签 |
| `level` | LogLevelEnum | `INFO` | 日志级别（TRACE、DEBUG、INFO、WARN、ERROR） |
| `ignoreArgs` | boolean | `true` | 是否忽略方法参数 |
| `ignoreResult` | boolean | `true` | 是否忽略方法返回值 |

### 3. 数据库持久化

访问日志支持自动批量持久化到数据库：

```yaml
platform:
  component:
    logging:
      db-persistent: true        # 启用数据库持久化
      db-batch-size: 500         # 批量大小
```

**持久化机制**：
- 日志先异步写入本地缓存（`LocalCache`）缓冲区
- 定时任务每分钟执行一次（`@Scheduled(cron = "0 0/1 * * * ?")`），从本地缓存中批量取出日志并持久化到数据库
- 每次批量处理的数量由 `db-batch-size` 控制（默认 500 条）
- 如果本地缓存中的数据量小于 `db-batch-size`，定时任务会等待下次执行

**异步处理**：
- `AccessLogServiceImpl.doRecordLog()` 使用 `@Async` 注解，确保日志写入本地缓存不会阻塞主流程
- 本地缓存使用 `ACCESS_LOG` 区域存储日志数据
- 定时任务从本地缓存中批量取出日志并持久化，避免频繁的数据库操作

### 4. 数据库表结构

组件使用 Liquibase 管理数据库表结构：

```yaml
platform:
  component:
    logging:
      schema:
        enable-liquibase: true
        access-log-table: access_log_info
        liquibase-change-log: classpath:db/changelog/db.changelog-master.yaml
```

**表结构**（`access_log_info`）：
- `id`：日志ID（雪花算法生成）
- `title`：日志标题
- `operator`：操作人
- `operator_id`：操作人ID
- `tenant_id`：租户ID
- `operate_time`：操作时间
- `url`：请求URL
- `method`：HTTP 方法
- `request_body`：请求体
- `response_body`：响应体
- `elapsed_time`：执行耗时（毫秒）
- `ip`：IP 地址
- `extra`：扩展信息

---

## ⚙️ 配置说明

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.logging.enable` | boolean | `true` | 是否启用操作日志 |
| `platform.component.logging.record-type` | String | `FILE` | 记录类型：`FILE`、`REDIS`、`MQ` |
| `platform.component.logging.enable-global-advice` | boolean | `false` | 是否启用全局切点 |
| `platform.component.logging.print-exception` | boolean | `false` | 是否打印异常信息（AOP 日志记录异常时） |

### 请求/响应体配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.logging.request-body-persistent` | boolean | `true` | 是否启用请求参数体持久化 |
| `platform.component.logging.request-body-size-limit` | boolean | `false` | 是否限制请求参数体大小 |
| `platform.component.logging.request-body-max-length` | int | `200` | 请求参数体最大字符长度 |
| `platform.component.logging.response-body-persistent` | boolean | `true` | 是否启用响应参数体持久化 |
| `platform.component.logging.response-body-size-limit` | boolean | `false` | 是否限制响应参数体大小 |
| `platform.component.logging.response-body-max-length` | int | `200` | 响应参数体最大字符长度 |

### 数据库持久化配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.logging.db-persistent` | boolean | `true` | 是否启用数据库持久化 |
| `platform.component.logging.db-batch-size` | int | `500` | 持久化数据库时一次获取的数据量 |

### 消息队列配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.logging.mq-topic-name` | String | `access-log-out-0` | 消息队列的 topic 名称 |

### Redis 配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.logging.cache-access-log-key` | String | `platform:access-log` | 日志保存到redis时临时暂存的路径 |

### 数据库模式配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.logging.schema.enable-auto-ddl` | boolean | `false` | 是否启用自动 DDL（如果启用了 Liquibase，会跳过 DDL 逻辑） |
| `platform.component.logging.schema.table-prefix` | String | `""` | 表前缀 |
| `platform.component.logging.schema.access-log-table` | String | `access_log_info` | 访问日志表名 |
| `platform.component.logging.schema.enable-liquibase` | boolean | `true` | 是否启用 Liquibase 迁移 |
| `platform.component.logging.schema.liquibase-change-log` | String | `classpath:db/changelog/db.changelog-master.yaml` | Liquibase 变更日志文件路径 |
| `platform.component.logging.schema.liquibase-dry-run` | boolean | `false` | Liquibase 是否仅生成 SQL 而不执行（干运行模式） |

**enable-auto-ddl 说明**：
- 如果启用了 Liquibase（`enable-liquibase: true`），`LoggingSchemaInitializer` 会跳过 DDL 逻辑
- 如果未启用 Liquibase 且表不存在，`LoggingSchemaInitializer` 会抛出异常并提示启用 Liquibase 或手动创建表
- 建议始终使用 Liquibase 管理数据库表结构，而不是启用 `enable-auto-ddl`

### 生命周期回调配置

组件会自动从 Spring 容器中扫描实现了回调接口的 Bean，无需手动配置。只需在实现类上添加 `@Component` 注解即可。

---

## 🔄 生命周期回调函数

组件支持在日志记录的不同阶段执行自定义逻辑，通过生命周期回调函数实现。

### 回调函数类型

组件提供了4个生命周期回调函数：

1. **BeforeLogCallback**：日志记录前回调，可以自定义生成日志信息
2. **AfterLogCallback**：日志记录后回调，可以执行后续处理（如发送通知）
3. **BeforePersistCallback**：持久化前回调，可以修改日志信息（如脱敏、添加字段）
4. **OnErrorCallback**：异常处理回调，可以自定义错误响应

### 使用方式

组件会自动从 Spring 容器中扫描实现了回调接口的 Bean。只需在实现类上添加 `@Component` 注解即可：

```java
@Component  // 自动扫描，无需配置
public class CustomBeforeLogCallback implements LogLifecycleCallback.BeforeLogCallback {
    // ...
}
```

**自动扫描优先级**（如果存在多个实现）：
1. `@Primary` 标记的实现（最高优先级）
2. `@Order` 注解值最小的实现
3. 默认按 Bean 名称排序

**性能说明**：
- **启动时性能**：自动扫描在应用启动时执行一次，需要遍历 Spring 容器中的所有 Bean（O(n) 复杂度），但通常 Bean 数量较少（几十到几百个），影响可忽略（< 10ms）
- **运行时性能**：直接调用已注册的回调函数引用，无额外性能开销
- **内存占用**：仅存储回调函数引用，内存占用极小

### 实现示例

#### 1. 日志记录前回调（BeforeLogCallback）

```java
import callback.com.richie.component.logging.LogLifecycleCallback;
import domain.com.richie.component.logging.AccessLogInfo;
import vo.api.common.com.richie.context.ResultVO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自定义日志记录前回调
 * 只需添加 @Component 注解，组件会自动扫描并注册
 */
@Component  // 自动扫描，无需配置
public class CustomBeforeLogCallback implements LogLifecycleCallback.BeforeLogCallback {

    @Override
    public AccessLogInfo apply(Map<String, Object> requestData, ResultVO<?> responseData) {
        // 自定义逻辑：可以修改请求数据或响应数据，然后生成日志信息
        // 如果返回null，则使用默认的日志生成逻辑
        AccessLogInfo logInfo = new AccessLogInfo();
        // ... 设置自定义字段
        return logInfo;
    }
}
```

#### 2. 日志记录后回调（AfterLogCallback）

```java
import callback.com.richie.component.logging.LogLifecycleCallback;
import domain.com.richie.component.logging.AccessLogInfo;
import org.springframework.stereotype.Component;

/**
 * 自定义日志记录后回调
 * 可以在日志记录完成后执行后续处理
 */
@Component
public class CustomAfterLogCallback implements LogLifecycleCallback.AfterLogCallback {

    @Override
    public boolean apply(AccessLogInfo logInfo, Throwable throwable) {
        // 自定义逻辑：例如发送通知、记录到其他系统等
        // 返回true继续执行，false终止执行
        if (throwable != null) {
            // 发生异常时的处理
            System.err.println("日志记录时发生异常: " + throwable.getMessage());
        }
        return true; // 继续执行
    }
}
```

#### 3. 持久化前回调（BeforePersistCallback）

```java
import callback.com.richie.component.logging.LogLifecycleCallback;
import domain.com.richie.component.logging.AccessLogInfo;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

/**
 * 自定义持久化前回调
 * 可以在持久化前修改日志信息
 */
@Component
public class CustomBeforePersistCallback implements LogLifecycleCallback.BeforePersistCallback {

    @Override
    public AccessLogInfo apply(AccessLogInfo logInfo, ProceedingJoinPoint joinPoint) {
        // 自定义逻辑：在持久化前修改日志信息
        // 例如添加额外字段、脱敏敏感信息等
        logInfo.setExtra("custom data added before persist");
        return logInfo; // 返回修改后的日志信息
    }
}
```

#### 4. 异常处理回调（OnErrorCallback）

```java
import callback.com.richie.component.logging.LogLifecycleCallback;
import vo.api.common.com.richie.context.ResultVO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自定义异常处理回调
 * 可以在发生异常时自定义错误响应
 */
@Component
public class CustomOnErrorCallback implements LogLifecycleCallback.OnErrorCallback {

    @Override
    public ResultVO<?> apply(Map<String, Object> requestData, Throwable throwable) {
        // 自定义错误处理逻辑
        // 返回null则使用默认的错误响应
        return ResultVO.getError("CUSTOM_ERROR", "自定义错误信息: " + throwable.getMessage());
    }
}
```

### 回调函数签名

| 回调函数 | 函数签名 | 返回值说明 |
|---------|---------|-----------|
| `BeforeLogCallback` | `(Map<String, Object> requestData, ResultVO<?> responseData) -> AccessLogInfo` | 返回null使用默认实现 |
| `AfterLogCallback` | `(AccessLogInfo logInfo, Throwable throwable) -> Boolean` | 返回true继续执行，false终止 |
| `BeforePersistCallback` | `(AccessLogInfo logInfo, ProceedingJoinPoint joinPoint) -> AccessLogInfo` | 返回null使用原始日志信息 |
| `OnErrorCallback` | `(Map<String, Object> requestData, Throwable throwable) -> ResultVO<?>` | 返回null使用默认错误响应 |

### 使用场景

1. **日志记录前回调**：
   - 自定义日志信息生成逻辑
   - 修改请求数据或响应数据
   - 添加额外的日志字段

2. **日志记录后回调**：
   - 发送通知（如邮件、短信）
   - 记录到其他系统（如监控系统）
   - 执行清理操作

3. **持久化前回调**：
   - 脱敏敏感信息（如密码、身份证号）
   - 添加业务相关字段
   - 格式化日志信息

4. **异常处理回调**：
   - 自定义错误响应格式
   - 记录详细的错误信息
   - 发送错误告警

### 注意事项

1. **Spring Bean**：回调实现类必须添加 `@Component` 注解，组件会自动扫描并注册
2. **异常处理**：回调函数中的异常不会影响主流程，但会记录错误日志
3. **性能考虑**：回调函数会在主流程中同步执行，注意避免耗时操作
4. **参数类型**：`BeforePersistCallback` 的第二个参数是 `ProceedingJoinPoint`，不是 `String recordType`

---

## 🎯 最佳实践

### 1. 访问日志使用

#### 推荐方式：按需启用

```java
@RestController
public class UserController {
    
    // 重要操作：记录日志并持久化
    @PostMapping("/users")
    @AccessLog(value = "创建用户", persistent = true)
    public ResultVO<User> createUser(@RequestBody CreateUserRequest request) {
        // ...
    }
    
    // 普通查询：仅记录日志
    @GetMapping("/users/{id}")
    @AccessLog("查询用户")
    public ResultVO<User> getUser(@PathVariable String id) {
        // ...
    }
    
    // 不需要记录日志的方法：不添加注解
    @GetMapping("/health")
    public ResultVO<String> health() {
        return ResultVO.success("ok");
    }
}
```

#### 不推荐：全局启用

全局启用会导致所有请求都记录日志，包括健康检查、监控接口等，产生大量无用日志。

### 2. 链路追踪使用

```java
@Service
@LogTrace("用户服务")  // 类级别：标识服务名称
public class UserServiceImpl {
    
    // 重要方法：记录参数和返回值
    @LogMethodTrace(
        value = "创建用户",
        level = LogLevelEnum.INFO,
        ignoreArgs = false,    // 记录参数
        ignoreResult = false  // 记录返回值
    )
    public User createUser(CreateUserRequest request) {
        // ...
    }
    
    // 普通方法：仅记录基本信息
    @LogMethodTrace("查询用户")
    public User getUser(String id) {
        // ...
    }
}
```

### 3. 存储方式选择

- **开发环境**：使用 `FILE`，便于查看日志
- **测试环境**：使用 `REDIS`，便于快速查询和分析
- **生产环境**：使用 `MQ`，异步处理，不影响主流程性能

### 4. 请求/响应体大小控制

对于可能包含大文件的接口，建议限制请求/响应体大小：

```yaml
platform:
  component:
    logging:
      request-body-size-limit: true
      request-body-max-length: 500
      response-body-size-limit: true
      response-body-max-length: 500
```

### 5. 数据库持久化优化

```yaml
platform:
  component:
    logging:
      db-persistent: true
      db-batch-size: 1000  # 根据数据库性能调整批量大小
```

---

## ❓ 常见问题

### Q1: 为什么返回值必须是 ResultVO？

**A:** 组件需要从返回值中提取响应数据，`ResultVO` 提供了统一的结构。如果返回值不是 `ResultVO`，组件会记录错误日志，但不会中断请求。

**错误日志示例**：
```
返回值不是 ResultVO 类型，违反强制性规则，无法进行日志记录。
违规类：com.richie.example.controller.UserController
违规函数：public String getUser(String id)
```

### Q2: 如何获取操作人信息？

**A:** 组件自动从 JWT Token 中提取操作人信息。Token 需要包含以下字段：
- `username`：操作人名称
- `tenantCode`：租户代码
- `id`：操作人ID（可选，通过 `OperatorContextHolder` 设置）

**手动设置操作人信息**：

如果需要在非 HTTP 请求场景（如定时任务、消息队列消费等）中设置操作人信息，可以使用 `OperatorContextHolder`：

```java
import handler.com.richie.component.logging.OperatorContextHolder;
import domain.com.richie.component.logging.OperatorInfo;

// 设置操作人信息
String token = "custom-token"; // 自定义 token（可以是任何唯一标识）
OperatorContextHolder.

        setOperator(
                token,           // token
    "operator-123",  // 操作人ID
            "张三",          // 操作人名称
            3600_000L        // 过期时间（毫秒）
        );

        // 检查操作人信息是否存在
        boolean exists = OperatorContextHolder.hasOperator(token);

        // 获取操作人信息
        OperatorInfo operator = OperatorContextHolder.getOperator(token);
```

**注意事项**：
- 操作人信息存储在 Redis 中，使用 `platform:operator:{token}` 作为 key
- 需要确保 Redis 连接正常，否则操作人信息无法正确存储和获取
- 操作人信息会在过期时间后自动清除

### Q3: 如何禁用某个接口的日志记录？

**A:** 不添加 `@AccessLog` 注解，并且设置 `enable-global-advice: false`。

### Q4: 日志记录会影响性能吗？

**A:** 
- **文件记录**：影响较小，异步写入本地缓存，由定时任务批量持久化
- **Redis 记录**：影响较小，异步写入本地缓存，由定时任务批量持久化
- **消息队列**：影响最小，完全异步，不阻塞主流程
- **数据库持久化**：影响较小，使用异步写入本地缓存 + 定时任务批量持久化的机制，避免频繁的数据库操作

**性能优化机制**：
1. 日志先异步写入本地缓存（`LocalCache`），不阻塞主流程
2. 定时任务每分钟执行一次，批量从本地缓存中取出日志并持久化到数据库
3. 批量大小可配置（`db-batch-size`），默认 500 条，可根据数据库性能调整

### Q5: 如何查看链路追踪日志？

**A:** 链路追踪日志通过 SLF4J 输出，根据 `@LogMethodTrace` 的 `level` 参数控制日志级别。

### Q6: 数据库表如何创建？

**A:** 组件使用 Liquibase 管理数据库表结构。确保：
1. `enable-liquibase: true`
2. 配置正确的 `liquibase-change-log` 路径
3. 应用启动时自动执行迁移

**Liquibase 干运行模式**：
- 设置 `liquibase-dry-run: true` 可以仅生成 SQL 而不执行，用于预览迁移脚本
- 适用于生产环境迁移前的验证场景

**自动 DDL 说明**：
- `enable-auto-ddl: true` 仅在未启用 Liquibase 时生效
- 如果启用了 Liquibase，`LoggingSchemaInitializer` 会跳过 DDL 逻辑
- 建议始终使用 Liquibase 管理数据库表结构，而不是启用 `enable-auto-ddl`

### Q7: 如何自定义日志格式？

**A:** 组件使用标准的日志格式，如需自定义，可以：
1. 使用生命周期回调函数（推荐）：通过 `BeforeLogCallback` 或 `BeforePersistCallback` 自定义日志信息
2. 继承 `AccessLogAspect` 或 `LogTraceAspect`：重写日志记录方法
3. 配置自定义切面

### Q8: 如何使用生命周期回调函数？

**A:** 组件会自动从 Spring 容器中扫描实现了回调接口的 Bean。只需在实现类上添加 `@Component` 注解即可：

```java
@Component  // 自动扫描，无需配置
public class CustomBeforeLogCallback implements LogLifecycleCallback.BeforeLogCallback {
    @Override
    public AccessLogInfo apply(Map<String, Object> requestData, ResultVO<?> responseData) {
        // 自定义逻辑
        return null; // 返回null使用默认实现
    }
}
```

如果存在多个实现，组件会按以下优先级选择：
1. `@Primary` 标记的实现（最高优先级）
2. `@Order` 注解值最小的实现
3. 默认按 Bean 名称排序

---

## 📝 总结

Richie Logging Component 提供了完整的访问日志和链路追踪解决方案，支持多种存储方式和灵活的配置选项。通过合理使用注解和配置，可以构建完善的日志体系。

**关键要点**：

1. **按需启用**：使用 `@AccessLog` 注解按需启用日志记录，避免全局启用
2. **合理配置**：根据环境选择合适的存储方式和批量大小
3. **性能优化**：使用消息队列或批量处理减少对主流程的影响
4. **链路追踪**：使用 `@LogTrace` 和 `@LogMethodTrace` 进行方法级别的追踪
5. **数据库管理**：使用 Liquibase 管理数据库表结构
6. **生命周期回调**：通过生命周期回调函数在日志记录的不同阶段执行自定义逻辑，实现灵活的扩展

