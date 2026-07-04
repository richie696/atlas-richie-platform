# Atlas Richie MongoDB组件 (atlas-richie-component-mongodb)

> 面向业务开发的 MongoDB 组件 — Fluent API · 横切注解 · 可观测性 · 熔断降级

## 📖 概述

`atlas-richie-component-mongodb` 是基于 Spring Data MongoDB 的业务级封装，提供：

- **Fluent API** — `Mongodb` 主类 + `QueryBuilder` / `UpdateBuilder` / `DeleteBuilder` 链式调用，类型安全（Lambda 字段引用）
- **横切注解** — `@SoftDelete` / `@TenantScoped` / `@AuditFields` / `@ExpireAfter`，业务方零样板
- **可观测性** — OTel 链路追踪 + Micrometer 指标 + 慢查询日志
- **熔断降级** — Sentinel 集成，通过 Nacos 热加载降级规则
- **语义化异常** — 4 个专属异常，错误处理更明确

业务方在 5 分钟内可上手；底层 Spring Data MongoDB 能力依然可用。

---

## 📖 目录

- [📖 概述](#📖-概述)
- [📎 5 分钟上手](#📎-5-分钟上手)
  - [第 1 步：定义实体](#第-1-步：定义实体)
  - [第 2 步：注入 `Mongodb`](#第-2-步：注入-mongodb)
  - [第 3 步：CRUD](#第-3-步：crud)
- [🔧 核心能力](#🔧-核心能力)
  - [`Mongodb` 主类方法](#mongodb-主类方法)
  - [`QueryBuilder` 链式条件](#querybuilder-链式条件)
  - [`UpdateBuilder` 链式字段操作](#updatebuilder-链式字段操作)
  - [`DeleteBuilder` 链式删除](#deletebuilder-链式删除)
  - [`IndexBuilder` 启动自动建索引](#indexbuilder-启动自动建索引)
- [📎 横切注解](#📎-横切注解)
  - [`@SoftDelete`（类级别）](#@softdelete（类级别）)
  - [`@TenantScoped`（类级别）](#@tenantscoped（类级别）)
  - [`@AuditFields`（类级别）](#@auditfields（类级别）)
  - [`@ExpireAfter`（字段级别）](#@expireafter（字段级别）)
- [📎 可观测性](#📎-可观测性)
  - [OpenTelemetry 链路追踪](#opentelemetry-链路追踪)
  - [Micrometer 指标](#micrometer-指标)
  - [慢查询日志](#慢查询日志)
  - [Actuator 端点](#actuator-端点)
- [📎 熔断降级](#📎-熔断降级)
  - [工作原理](#工作原理)
  - [资源列表](#资源列表)
  - [默认阈值](#默认阈值)
  - [Nacos 热加载](#nacos-热加载)
  - [自定义降级逻辑](#自定义降级逻辑)
  - [注意事项](#注意事项)
- [🏗️ 架构与模块布局](#🏗️-架构与模块布局)
- [⚙️ 配置参考](#⚙️-配置参考)
  - [基础连接](#基础连接)
  - [连接池](#连接池)
  - [熔断（可选）](#熔断（可选）)
- [📎 从旧 MongodbService 迁移](#📎-从旧-mongodbservice-迁移)
  - [旧 API](#旧-api)
  - [新 API](#新-api)
  - [迁移对照](#迁移对照)
  - [兼容性](#兼容性)
- [📎 版本与兼容性](#📎-版本与兼容性)
- [📎 测试基础设施](#📎-测试基础设施)
- [📎 监听器（高级）](#📎-监听器（高级）)
- [📎 反馈与支持](#📎-反馈与支持)
---

## 📎 5 分钟上手

### 第 1 步：定义实体

```java
@Document(collection = "users")
@SoftDelete
@TenantScoped
@AuditFields
public class User {
    @Id
    private String id;
    private String username;
    private String email;
    private Integer age;
    private LocalDateTime createdAt;     // @AuditFields 自动管理
    private LocalDateTime updatedAt;     // @AuditFields 自动管理
    private String createdBy;            // @AuditFields 自动管理
    private String updatedBy;            // @AuditFields 自动管理
    private String tenantId;             // @TenantScoped 自动管理
    private Boolean deleted;             // @SoftDelete 自动管理
    // getter / setter 省略
}
```

### 第 2 步：注入 `Mongodb`

```java
@Service
public class UserService {
    private final Mongodb mongodb;

    public UserService(Mongodb mongodb) {
        this.mongodb = mongodb;
    }
}
```

### 第 3 步：`CRUD`

```java
// 插入（自动填 audit 4 字段 + tenantId）
User user = new User();
user.setUsername("alice");
mongodb.insert(user);

// 查询（自动加 tenant 过滤 + 软删过滤）
List<User> activeUsers = mongodb.query(User.class)
    .eq(User::getStatus, "ACTIVE")
    .gt(User::getAge, 18)
    .orderByDesc(User::getCreatedAt)
    .list();

User one = mongodb.query(User.class)
    .eq(User::getUsername, "alice")
    .oneOrThrow(() -> new UserNotFoundException("alice"));

// 更新（自动追加 updatedAt / updatedBy）
boolean ok = mongodb.update(User.class)
    .eq(User::getId, userId)
    .set(User::getStatus, "DISABLED")
    .execute();

// 删除（默认软删 → `deleted = true`）
mongodb.delete(User.class).eq(User::getId, userId).execute();
mongodb.delete(User.class).eq(User::getId, userId).force().execute();  // 物理删
```

---

## 🔧 核心能力

### `Mongodb` 主类方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `query(Class<T>)` | `QueryBuilder<T>` | 链式查询 |
| `update(Class<T>)` | `UpdateBuilder<T>` | 链式更新 |
| `delete(Class<T>)` | `DeleteBuilder<T>` | 链式删除 |
| `insert(T)` | `T` | 插入（重复键抛 `DuplicateKeyException`） |
| `insertAll(List<T>)` | `List<T>` | 批量插入 |
| `save(T)` | `T` | Upsert by `@Id` |
| `findById(Class<T>, id)` | `T` | 按 ID 查（可为 null） |
| `findByIdOrThrow(Class<T>, id, Supplier)` | `T` | 按 ID 查，找不到时抛业务异常 |
| `existsById(Class<T>, id)` | `boolean` | 按 ID 判断存在 |
| `deleteById(Class<T>, id)` | `void` | 按 ID 物理删除（不走 builder） |
| `dropCollection(Class<T>)` | `void` | 删除集合（危险操作，被熔断保护） |

### `QueryBuilder` 链式条件

```java
mongodb.query(User.class)
    // 条件
    .eq(User::getStatus, "ACTIVE")             // =
    .ne(User::getStatus, "DISABLED")           // !=
    .gt(User::getAge, 18)                      // >
    .ge(User::getAge, 18)                     // >=
    .lt(User::getAge, 60)                     // <
    .le(User::getAge, 60)                     // <=
    .between(User::getAge, 18, 60)            // BETWEEN
    .like(User::getName, "ali")               // contains
    .in(User::getRole, List.of("A", "B"))     // IN
    .nin(User::getRole, List.of("C"))         // NOT IN
    .exists(User::getEmail)                   // field exists
    .isNull(User::getEmail)                   // field is null
    .isNotNull(User::getEmail)                // field is not null
    // 组合
    .and(builder -> builder
        .eq(User::getStatus, "ACTIVE")
        .gt(User::getAge, 18))
    .or(User::getStatus, "PENDING")
    // 排序 + 投影
    .orderByAsc(User::getAge)
    .orderByDesc(User::getCreatedAt)
    .select(User::getId, User::getUsername)
    // 分页
    .page(1, 20)
    .skip(10).limit(20)
    // 旁路（业务方偶尔需要）
    .bypassTenant()       // 跨租户查询
    .ignoreSoftDelete()   // 包含已软删
    // 终态
    .list();              // List<T>
    .one();               // T（null if not found）
    .oneOpt();            // Optional<T>
    .oneOrThrow(supplier);// T，找不到时抛业务异常
    .count();             // long
    .pageResult();        // PageResult<T> 含 total
```

### `UpdateBuilder` 链式字段操作

```java
boolean updated = mongodb.update(User.class)
    .eq(User::getId, userId)
    .set(User::getStatus, "DISABLED")        // 字段赋值
    .inc(User::getLoginCount, 1)             // 数字自增
    .unset(User::getResetToken)              // 字段删除
    .push(User::getTags, "vip")              // 数组 push
    .pull(User::getTags, "vip")              // 数组 pull
    .addToSet(User::getRoles, "ADMIN")       // 数组去重 add
    .rename(User::getOldName, "newName")     // 字段重命名
    .execute();                              // 返回受影响行数

mongodb.update(User.class)
    .eq(User::getStatus, "PENDING")
    .gt(User::getCreateTime, expiredBefore)
    .set(User::getStatus, "EXPIRED")
    .executeAndReturn();                     // 返回更新后的文档（如果有）
```

### `DeleteBuilder` 链式删除

```java
mongodb.delete(User.class)
    .eq(User::getStatus, "DISABLED")
    .execute();          // 软删（受 @SoftDelete 注解影响）

mongodb.delete(User.class)
    .eq(User::getStatus, "DISABLED")
    .force()             // 物理删除
    .execute();
```

### `IndexBuilder` 启动自动建索引

```java
// 在实体上声明
@Document(collection = "users")
public class User {
    @Id
    private String id;

    @Indexed(unique = true)              // Spring Data 内置注解
    private String username;

    @Indexed                            // 普通索引
    @ExpireAfter(seconds = 3600)        // 启动时自动建 TTL 索引
    private String resetToken;
}
```

`@ExpireAfter` 注解触发 `IndexBuilder` 在 Spring 启动时自动创建 TTL 索引，无需业务方手动调用。

---

## 📎 横切注解

### `@SoftDelete`（类级别）

```java
@Document
@SoftDelete                    // 默认字段名 "deleted"
public class User { @SoftDelete("isRemoved") private Boolean isRemoved; }
```

行为：
- `QueryBuilder` / `DeleteBuilder` 自动加 `deleted = false` 过滤条件
- 业务方调用 `.force()` 跳过过滤，物理删除
- `Mongodb` 主类 `deleteById()` 不受此影响（永远是物理删除）

### `@TenantScoped`（类级别）

```java
@Document
@TenantScoped                  // 默认字段名 "tenantId"
public class User { @TenantScoped("orgId") private String orgId; }
```

行为：
- `QueryBuilder` / `UpdateBuilder` / `DeleteBuilder` 自动加 `tenantId = 当前租户` 条件
- 业务方调用 `.bypassTenant()` 跳过过滤（跨租户场景）
- 插入时自动填 tenantId（从 `TenantContext` 读取）

租户上下文设置（业务方负责）：

```java
try {
    TenantContext.set("tenant-001");
    // ... 业务代码
} finally {
    TenantContext.clear();
}
```

如果项目里有既有的租户上下文（比如从 Token / Header 解析），建议在拦截器 / Filter 中把租户 ID 写入 `TenantContext`。

### `@AuditFields`（类级别）

```java
@Document
@AuditFields
public class User {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
```

行为：
- `insert` / `save` 时自动填 `createdAt` / `updatedAt` = 当前时间，`createdBy` / `updatedBy` = 当前登录用户
- `update` 时自动追加 `updatedAt` = 当前时间，`updatedBy` = 当前登录用户
- 当前用户从 Spring Security `SecurityContextHolder` 读取，无 Security 时回退到 `"system"`

### `@ExpireAfter`（字段级别）

```java
@ExpireAfter(seconds = 3600)        // 1 小时后自动过期
private String resetToken;
```

行为：启动时自动创建 TTL 索引，MongoDB 在文档过期时间到达后自动删除。

---

## 📎 可观测性

### `OpenTelemetry` 链路追踪

每次 `Mongodb` 公共方法调用都创建一个 OTel Span（`SpanKind.CLIENT`），属性：

| 属性 | 示例值 | 来源 |
|---|---|---|
| `db.system` | `mongodb` | 常量 |
| `db.operation` | `query` / `update` / `insert` / `delete` / `save` | 操作类型 |
| `db.mongodb.collection` | `users` | `@Document.collection` |
| `db.statement` | `eq(age, 18).list()` | 截断至 1KB |

降级时（被 Sentinel 熔断）Span 标记为 `StatusCode.ERROR`，事件包含 `sentinel.degrade.reason`。

### `Micrometer` 指标

| 指标 | 类型 | 标签 |
|---|---|---|
| `mongodb.operation.duration` | Timer | `operation`, `collection`, `result` |
| `mongodb.errors` | Counter | `operation`, `collection`, `error_type` |

### 慢查询日志

默认阈值 200ms。超过则 WARN 日志：

```
WARN  c.r.c.m.observability.MongodbSlowQueryLogger : 
  Slow MongoDB operation: query on users took 287ms
```

阈值可通过 `MongodbProperties.slowQueryThresholdMs` 调整。

### `Actuator` 端点

启用以下 Actuator endpoint：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  metrics:
    tags:
      application: ${spring.application.name}
```

访问：
- `GET /actuator/health` — Mongo 健康检查（由 `MongoHealthIndicator` 提供）
- `GET /actuator/metrics/mongodb.operation.duration` — 指标详情
- `GET /actuator/prometheus` — Prometheus 抓取（需 `micrometer-registry-prometheus` 依赖，已默认包含）

---

## 📎 熔断降级

集成 Alibaba Sentinel 1.8.6，实现**业务级慢调用熔断**（不是网关层的安全访问熔断）。

### 工作原理

- **熔断策略**：`SLOW_REQUEST_RATIO`（慢请求比例）
- **资源粒度**：按操作类型划分，共 9 个资源
- **AOP 切点**：`Mongodb` 主类公共方法（不在 `QueryBuilder` 等内部 builder 上切，避免重复计数）
- **降级行为**：返回安全的空结果（空列表 / null / 0），不抛异常
- **OTel 联动**：降级时 Span 标记为 `ERROR` 并附 reason

### 资源列表

| 资源名 | 对应方法 |
|---|---|
| `mongodb.query` | `query()` |
| `mongodb.update` | `update()` |
| `mongodb.delete` | `delete()` |
| `mongodb.insert` | `insert()` / `insertAll()` |
| `mongodb.save` | `save()` |
| `mongodb.findById` | `findById()` / `findByIdOrThrow()` |
| `mongodb.existsById` | `existsById()` |
| `mongodb.deleteById` | `deleteById()` |
| `mongodb.dropCollection` | `dropCollection()` |

### 默认阈值

| 参数 | 默认值 | 说明 |
|---|---|---|
| `maxRt` | 100ms | 慢请求判定阈值（MongoDB Atlas + `slowms` 行业标准） |
| `slowRatioThreshold` | 0.5 | 慢请求比例（50%） |
| `timeWindow` | 10s | 熔断持续时间 |
| `minRequestAmount` | 10 | 触发熔断的最小请求数 |
| `statIntervalMs` | 1000ms | 统计窗口 |

### `Nacos` 热加载

在 Nacos 控制台创建配置：
- **Data ID**: `mongodb-sentinel-degrade-rules`
- **Group**: `DEFAULT_GROUP`
- **格式**: JSON（数组形式）

```json
[
  {
    "resource": "mongodb.query",
    "grade": 3,
    "count": 100,
    "slowRatioThreshold": 0.5,
    "minRequestAmount": 10,
    "statIntervalMs": 1000,
    "timeWindow": 10
  },
  {
    "resource": "mongodb.update",
    "grade": 3,
    "count": 200,
    "slowRatioThreshold": 0.6,
    "minRequestAmount": 5,
    "statIntervalMs": 1000,
    "timeWindow": 30
  }
]
```

字段说明：
- `grade: 3` — 慢请求比例模式
- `count` — 慢请求 RT 阈值（毫秒）
- `slowRatioThreshold` — 慢请求比例（0.5 = 50%）
- `timeWindow` — 熔断持续时间（秒）

完整示例见 `circuitbreaker/mongodb-sentinel-degrade-rules.json`。

### 自定义降级逻辑

如需自定义降级处理，使用 `@SentinelResource` 注解：

```java
@Service
public class UserService {
    @Autowired
    private Mongodb mongodb;

    @SentinelResource(value = "mongodb.query",
                      blockHandler = "queryBlockHandler",
                      fallback = "queryFallback")
    public List<User> findActiveUsers() {
        return mongodb.query(User.class)
            .eq(User::getStatus, "ACTIVE")
            .list();
    }

    // 熔断时调用
    public List<User> queryBlockHandler(BlockException ex) {
        log.warn("MongoDB query blocked by Sentinel: {}", ex.getMessage());
        return Collections.emptyList();
    }

    // 业务异常时调用
    public List<User> queryFallback(Throwable t) {
        log.error("MongoDB query failed", t);
        return Collections.emptyList();
    }
}
```

### 注意事项

1. 熔断只针对业务级慢查询，**不解决**网络层问题（连接超时、DNS 失败等走 Mongo 驱动原生重试）
2. `dropCollection` 是危险操作，框架已设 `minRequestAmount=1`（1 次慢即熔断）
3. 降级返回空结果时，业务代码应能正确处理空（`isEmpty()` 等）
4. Nacos 规则修改后自动热加载，无需重启

---

## 🏗️ 架构与模块布局

| 异常 | 触发场景 | 业务处理建议 |
|---|---|---|
| `MongodbException` | 基类异常 | 通用兜底 |
| `DuplicateKeyException` | 唯一键冲突 | 提示用户 "记录已存在" 或重命名后重试 |
| `ConnectionException` | 连接/网络问题 | 提示 "服务暂不可用，请稍后重试" |
| `TransactionException` | 事务回滚 | 提示用户 "操作失败，请重试" |

**降级不抛异常**（被 Sentinel 熔断时返回空结果），业务代码无需 try-catch。

---

## ⚙️ 配置参考

### 基础连接

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `platform.component.mongodb.uri` | - | MongoDB 连接 URI（优先级最高） |
| `platform.component.mongodb.host` | localhost | 主机名 |
| `platform.component.mongodb.port` | 27017 | 端口 |
| `platform.component.mongodb.database` | example | 数据库名 |
| `platform.component.mongodb.username` | - | 用户名 |
| `platform.component.mongodb.password` | - | 密码 |
| `platform.component.mongodb.auth-database` | admin | 认证数据库 |

### 连接池

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `platform.component.mongodb.max-connection-pool-size` | 100 | 最大连接数 |
| `platform.component.mongodb.min-connection-pool-size` | 0 | 最小连接数 |
| `platform.component.mongodb.connect-timeout-ms` | 10000 | 连接超时（毫秒）|
| `platform.component.mongodb.socket-timeout-ms` | 10000 | 读取超时（毫秒） |

### 熔断（可选）

```yaml
platform:
  component:
    mongodb:
      circuit-breaker:
        enabled: true                              # 是否启用
        nacos-data-id: mongodb-sentinel-degrade-rules
        nacos-group: DEFAULT_GROUP
        max-rt: 100
        slow-ratio-threshold: 0.5
        time-window: 10
        min-request-amount: 10
        stat-interval-ms: 1000
```

不启用时熔断切面不生效（默认）。

---

## 📎 从旧 MongodbService 迁移

### 旧 `API`

```java
@Autowired
private MongodbService mongodbService;

Query q = new Query(Criteria.where("age").gt(18));
List<User> users = mongodbService.find(q, "users", User.class);

mongodbService.updateOne(q, new Update().set("status", "DISABLED"), "users", User.class);
```

### 新 `API`

```java
@Autowired
private Mongodb mongodb;

List<User> users = mongodb.query(User.class)
    .gt(User::getAge, 18)
    .list();

mongodb.update(User.class)
    .eq(User::getId, userId)
    .set(User::getStatus, "DISABLED")
    .execute();
```

### 迁移对照

| 旧 `MongodbService` 方法 | 新 `Mongodb` 等价 |
|---|---|
| `find(Query, coll, Class)` | `mongodb.query(Class).list()` 链式 |
| `findOne(Query, coll, Class)` | `mongodb.query(Class).one()` |
| `updateOne(Query, Update, coll, Class)` | `mongodb.update(Class).set(...).execute()` |
| `delete(Query, coll, Class)` | `mongodb.delete(Class).execute()` |
| `count(Query, coll, Class)` | `mongodb.query(Class).count()` |
| `insert(coll, doc)` | `mongodb.insert(doc)` |
| `insertMany(coll, docs)` | `mongodb.insertAll(docs)` |
| `findById(coll, id, Class)` | `mongodb.findById(Class, id)` |
| `createIndex(coll, Index)` | `@Indexed` 注解 + `IndexBuilder` |
| `aggregate(Aggregation, coll, Class)` | 暂未封装，业务方用 `MongoTemplate` |
| `executeInTransaction(Supplier)` | 暂未封装，业务方用 `MongoTransactionManager` |
| `bulkWrite(coll, ops)` | 暂未封装，业务方用 `MongoTemplate` |
| `storeFileToGridFS(...)` | 暂未封装，业务方用 `GridFsTemplate` |
| `watchChangeStream(...)` | 暂未封装，业务方用 `MongoTemplate` |

### 兼容性

- 旧 `MongodbService` 标记为 `@Deprecated(forRemoval="2.0")`，当前仍可用
- 计划保留 1-2 个 minor 版本后删除
- 建议新业务代码使用 `Mongodb`；旧代码按需迁移

---

## 📎 版本与兼容性

| 维度 | 说明 |
|---|---|
| Java | 17+ |
| Spring Boot | 3.x（4.0.x 验证） |
| MongoDB | 4.0+（事务支持需 4.0+） |
| OTel | 自动检测项目里的 `GlobalOpenTelemetry` |
| Sentinel | 1.8.6 |
| 旧 MongodbService | 保留至 2.0 之前 |

---

## 📎 测试基础设施

模块提供 `MongodbIntegrationTest` 注解，业务方测试时只需在测试类上添加：

```java
@MongodbIntegrationTest
class MyServiceIT {
    @Autowired private Mongodb mongodb;
    // 自动启动 Testcontainers Mongo
}
```

依赖：
- `org.testcontainers:mongodb`
- `com.richie.testing:testing-testcontainers`（项目基础包）

---

## 📎 监听器（高级）

`DefaultMongoServerListener` 和 `DefaultMongoServerMonitorListener` 用于监控 MongoDB 连接状态与心跳。业务方一般无需关心，自定义方式：

```java
@Component
public class CustomServerListener implements ServerListener {
    // 覆盖需要的方法
}

@Component
public class CustomServerMonitorListener implements ServerMonitorListener {
    // 覆盖需要的方法
}
```

---

## 📎 反馈与支持

- **API 问题**：找组件维护者
- **Bug 反馈**：提 issue（建议附上 MongoDB 版本、调用堆栈、复现代码）
- **新功能**：先讨论设计再实施

---

**最后更新**：覆盖 4 阶段重构后版本（Phase 1-4：Fluent API + 横切注解 + 可观测性 + 熔断降级）
