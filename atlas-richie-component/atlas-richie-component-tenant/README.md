# atlas-richie-component-tenant — 多租户组件

## 目录

- [这是什么样的组件](#这是什么样的组件)
- [5 种隔离模式 — 选型决策树](#5种隔离模式--选型决策树)
- [快速开始](#快速开始)
- [架构总览](#架构总览)
- [核心 API](#核心-api)
- [隔离模式详解](#隔离模式详解)
  - [COLUMN 模式](#column-模式)
  - [TABLE 模式](#table-模式)
  - [SCHEMA 模式](#schema-模式)
  - [DATABASE 模式](#database-模式)
  - [HYBRID 模式](#hybrid-模式)
- [Web 集成](#web-集成)
- [事务管理](#事务管理)
- [异步与多线程](#异步与多线程)
- [数据源路由与熔断](#数据源路由与熔断)
- [配置参考](#配置参考)
- [SPI 扩展点](#spi-扩展点)
- [API 全签名参考](#api-全签名参考)
- [错误码参考](#错误码参考)
- [注意事项与陷阱](#注意事项与陷阱)
- [文档索引](#文档索引)
- [端到端最小可运行示例](#端到端最小可运行示例)

---

## 这是什么样的组件

`atlas-richie-component-tenant` 是 Atlas Richie 平台的多租户能力组件,覆盖**从 HTTP 请求进入到 MyBatis SQL 执行**的完整链路:

| 链路节点                 | 涉及的类                                                 | 作用                                                               |
|----------------------|------------------------------------------------------|------------------------------------------------------------------|
| HTTP 请求进入            | `TenantIdentityFilter`                               | 从 JWT 提取租户 → 校验 → 绑定到线程上下文                                       |
| 业务层调用                | `TenantContext` (静态门面)                               | 业务代码读/写当前租户                                                      |
| 异步任务派发               | `TenantTaskDecoratorBeanPostProcessor`               | 自动给所有线程池注入上下文装饰器                                                 |
| 事务内调用                | `TransactionTenantHolder`                            | 冻结租户,防同一事务切换不同租户                                                 |
| MyBatis SQL 执行前      | `TenantStrategyInterceptor`                          | 查租户信息 → 熔断检查 → 调度对应策略                                            |
| Column 模式 SQL 改写     | `TenantLineInnerInterceptor`                         | 给 SELECT/UPDATE/DELETE 加 `tenant_id` 条件,给 INSERT 加 `tenant_id` 列 |
| Table 模式 SQL 改写      | `DynamicTableNameInnerInterceptor`                   | 给所有表名追加租户后缀                                                      |
| Schema 模式            | `SchemaStrategy`                                     | `SET LOCAL search_path` 切换 Schema                                |
| Database 模式          | `DynamicTenantDataSource` + `DatabaseStrategy`       | 数据源 key 路由到独立 DB                                                 |
| MyBatis 实体 INSERT 填充 | `TenantMetaObjectHandler`                            | 兜底,INSERT 时自动填 `tenantId`                                        |
| 数据源熔断                | `DataSourceCircuitBreaker` + `DataSourceHealthProbe` | 失败计数 → 熔断 → 探测恢复                                                 |
| 异常处理                 | `TenantExceptionHandler` + `TenantErrorCode`         | 统一 4xx/5xx 响应                                                    |

---

## 5种隔离模式--选型决策树

> **第一步先选模式**,再考虑代码怎么写。选错模式的代价是 DDL 大改 + 数据迁移。

### 对比矩阵

| 模式           | 共享什么        | 隔离什么                   | 适合场景           | 运维成本                | 性能    | 安全性               |
|--------------|-------------|------------------------|----------------|---------------------|-------|-------------------|
| **COLUMN**   | 整张表         | 通过 `tenant_id` 列       | 租户数据量小,共享业务    | ⭐ 最低                | ⭐⭐⭐⭐  | ⭐⭐ (靠 SQL 改写 + 列) |
| **TABLE**    | 数据库         | 表名后缀 (如 `orders_1001`) | 中等数据量,需要按租户分表  | ⭐⭐ 需 DDL            | ⭐⭐⭐   | ⭐⭐⭐               |
| **SCHEMA**   | DB instance | Schema (PG/Oracle)     | 中等-大量,需要独立 DDL | ⭐⭐⭐ 需维护 schema      | ⭐⭐⭐   | ⭐⭐⭐⭐              |
| **DATABASE** | 应用服务        | 独立 DB instance         | 金融/医疗,合规要求     | ⭐⭐⭐⭐ 需独立 DB         | ⭐⭐    | ⭐⭐⭐⭐⭐             |
| **HYBRID**   | 混合          | 按租户动态选                 | 大型平台,租户异质      | ⭐⭐⭐⭐ 需 sys_tenant 表 | 取决于委派 | 取决于委派             |

### 决策流程

```
开始
  │
  ├─ Q1: 租户之间是否需要独立 DB instance (合规/灾备/数据所有权要求)?
  │     ├─ 是 → DATABASE
  │     └─ 否 ↓
  │
  ├─ Q2: 是否需要为不同租户运行不同的 DDL (字段扩展/定制表结构)?
  │     ├─ 是 → SCHEMA (PG/Oracle) 或 DATABASE
  │     └─ 否 ↓
  │
  ├─ Q3: 单租户数据量是否大到需要分表 (如 > 5000 万行)?
  │     ├─ 是 → TABLE
  │     └─ 否 ↓
  │
  ├─ Q4: 租户之间数据隔离需求是否强,业务上不能共享表?
  │     ├─ 是 → TABLE
  │     └─ 否 ↓
  │
  └─ 99% 情况 → COLUMN (默认)
```

### 实际选型经验

| 业务类型                   | 推荐模式                | 理由                        |
|------------------------|---------------------|---------------------------|
| SaaS 通用业务 (CRM/ERP/工单) | **COLUMN**          | 简单,共享表足够,业务方不用关心 DDL      |
| 平台型业务 (不同租户 UI 都不同)    | **HYBRID** + SCHEMA | 大租户给独立 schema,小租户走 column |
| 金融/医疗                  | **DATABASE**        | 合规要求,独立 DB instance       |
| IoT/日志型 (单租户数据爆炸)      | **TABLE**           | 分表,水平扩展                   |
| 内部多业务线                 | **COLUMN**          | 业务线之间天然独立,SQL 隔离够用        |

---

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-tenant</artifactId>
</dependency>
```

**零配置即可使用 COLUMN 模式 + Web 集成**。启动日志看到:
```
TenantContext initialized with ScopedValue (preferred for virtual threads)
[多租户] 微服务通信框架已就绪: HTTP (Feign/RestClient)
```

### 2. 准备 `sys_tenant` 表

```sql
CREATE TABLE sys_tenant (
    id              BIGINT PRIMARY KEY,
    tenant_name     VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL,    -- ACTIVE/EXPIRED/MIGRATING/DISABLED
    isolation_mode  VARCHAR(20) NOT NULL,    -- COLUMN/TABLE/SCHEMA/DATABASE/HYBRID
    data_source_key VARCHAR(50),             -- Database 模式专用
    table_suffix    VARCHAR(50),             -- Table 模式专用
    schema_name     VARCHAR(50),             -- Schema 模式专用
    expired_time    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### 3. 实现 `TenantInfoProvider` SPI

```java
@Component
public class MyTenantInfoProvider implements TenantInfoProvider {
    @Autowired private SysTenantMapper mapper;
    
    @Override
    public TenantInfo getTenantInfo(Long tenantId) {
        SysTenant entity = mapper.selectById(tenantId);
        if (entity == null) return null;
        return TenantInfo.builder()
            .id(entity.getId())
            .name(entity.getTenantName())
            .status(TenantStatus.valueOf(entity.getStatus()))
            .mode(IsolationMode.valueOf(entity.getIsolationMode()))
            .dataSourceName(entity.getDataSourceKey())
            .tableSuffix(entity.getTableSuffix())
            .schemaName(entity.getSchemaName())
            .build();
    }
    
    @Override
    public boolean exists(Long tenantId) {
        return mapper.exists(tenantId);
    }
}
```

> ⚠️ **必须实现此 SPI**,组件默认提供一个 NoOp 实现 (返回 null),会导致所有 SQL 抛出 `TenantNotFoundException`。

### 4. 配置 application.yml

```yaml
multi-tenancy:
  enabled: true
  mode: COLUMN                  # 默认隔离模式
  tenant-id-column: tenant_id
  ignore-tables:                 # 字典表、配置表等不需要租户隔离
    - sys_dict
    - sys_config
```

### 5. 业务代码

```java
// Service 层 - 完全无感
@Service
public class OrderService {
    @Autowired private OrderMapper orderMapper;
    
    public Order getById(Long orderId) {
        return orderMapper.selectById(orderId);  
        // 自动加 WHERE tenant_id = ? (从 TenantContext 读)
        // 自动填 INSERT tenant_id
    }
}
```

### 6. 启动验证

```bash
curl -H "X-ACCESS-TOKEN: $JWT" -H "X-Tenant-ID: 1001" \
     http://localhost:8080/api/orders/123

# 响应: 200 OK + 该租户的订单
# SQL 实际执行: SELECT * FROM orders WHERE id = 123 AND tenant_id = 1001
```

---

## 架构总览

```
┌────────────────────────────────────────────────────────────────┐
│                        HTTP 请求进入                            │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│ TenantIdentityFilter (OncePerRequestFilter)                     │
│   1. 白名单检查                                                  │
│   2. 从 JWT (X-ACCESS-TOKEN) 解析 TenantPrincipal              │
│   3. 降级:从 X-Tenant-ID header 解析 (Feign 内部调用)            │
│   4. 校验 tenantId 格式 + 存在性 + 过期                          │
│   5. JWT ↔ Header 交叉校验 (防伪造)                              │
│   6. TenantContext.runWithTenant(principal, () -> doFilter)   │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                      业务层 (Controller → Service → DAO)         │
│                                                                 │
│   业务代码: TenantContext.getTenantId() (读)                     │
│   入口层:   TenantContext.runWithTenant(principal, task) (写)    │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│ MyBatis SQL 执行前 — 三个拦截器链                                │
│                                                                 │
│  ① TenantStrategyInterceptor                                   │
│     - 调 TenantInfoProvider 拿 TenantInfo                       │
│     - 熔断检查 (DataSourceCircuitBreaker)                       │
│     - 按 mode 调度对应策略 (ColumnStrategy/TableStrategy/...)   │
│     - 策略完成数据源路由/表后缀/Schema 切换                        │
│     - 记录 success/failure 到熔断器                              │
│                                                                 │
│  ② TenantLineInnerInterceptor (Column/Hybrid-COLUMN)           │
│     - SELECT/UPDATE/DELETE: 追加 WHERE tenant_id = ?            │
│     - INSERT: 追加 tenant_id 列和值 (若缺失)                     │
│     - 改写失败 → 降级原 SQL (WARN 日志)                          │
│                                                                 │
│  ③ DynamicTableNameInnerInterceptor (Table/Hybrid-TABLE)       │
│     - 给 FROM/JOIN/UPDATE/DELETE/INSERT 表名追加后缀            │
│     - 改写失败 → 降级原 SQL (WARN 日志)                          │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│ MyBatis-Plus MetaObjectHandler — 实体 INSERT 兜底                │
│                                                                 │
│   TenantMetaObjectHandler.insertFill()                          │
│     - metaObject 有 tenantId 字段 → 填当前租户 ID                │
│     - 未绑定上下文 → 填 0L (平台默认)                            │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                       SQL 执行 → 数据库                          │
└────────────────────────────────────────────────────────────────┘
```

### 上下文传播全景

| 场景 | 上下文保持 | 实现机制 |
|------|----------|---------|
| 单线程请求 | ✅ | ScopedValue / ThreadLocal |
| `StructuredTaskScope.fork()` | ✅ 自动 | ScopedValue 继承 |
| `@Async` 线程池 | ✅ 自动 | TenantTaskDecoratorBeanPostProcessor |
| `@Scheduled` 定时任务 | ✅ 自动 | 同上 |
| CompletableFuture 链 | ⚠️ 部分 | 依赖业务用 TaskDecorator |
| Reactive (WebFlux) | ❌ | 需业务方手动 capture/restore |
| Feign 调用下游 | ✅ | 由 `atlas-richie-component-microservice` 处理 |
| RocketMQ 消费 | ✅ | 由 `atlas-richie-component-messaging` 处理 |

---

## 核心 API

### TenantContext (静态门面)

**所有外部代码统一调这个,不要直接动 `TenantContextHolder` 实现。**

```java
// ============ 读取 (任何代码都可用) ============

Long tenantId = TenantContext.getTenantId();              // null 表示未绑定
TenantPrincipal p = TenantContext.get();                  // 完整租户主体
String name = TenantContext.getTenantName();

Long tid = TenantContext.requireTenantId();               // 抛 BusinessException if 未绑定
TenantPrincipal rp = TenantContext.require();

// ============ 绑定 (入口层用) ============

// Runnable 版本
TenantContext.runWithTenant(principal, () -> {
    // 业务代码,内部 TenantContext.get() 一定非空
});

// Supplier 版本 (有返回值)
Order order = TenantContext.runWithTenant(principal, () -> 
    orderMapper.selectById(orderId)
);
```

**重要约束**:
- 同一线程重复 `runWithTenant` 走的是嵌套作用域(ScopedValue 支持)
- 事务内 `runWithTenant(另一个租户, ...)` 会抛 `TenantSwitchInTransactionException` (由 `TransactionTenantHolder` 检测)
- 异步任务 (新线程) 默认不继承上下文,需用 `TenantTaskDecorator` (框架已自动配)

### TenantPrincipal (租户身份)

```java
// 定义在 atlas-richie-contract 模块
@Data @Accessors(chain = true)
public class TenantPrincipal implements Serializable {
    private Long tenantId;                  // 租户 ID
    private String tenantName;              // 展示名
    private OffsetDateTime expiredTime;     // 过期时间 (UTC)
}

// 创建方式
TenantPrincipal p = new TenantPrincipal()
    .setTenantId(1001L)
    .setTenantName("某租户")
    .setExpiredTime(OffsetDateTime.parse("2026-12-31T23:59:59Z"));
```

### IsolationMode (5 种模式枚举)

```java
public enum IsolationMode {
    COLUMN,    // 共享表 + tenant_id 列
    TABLE,     // 共享库 + 表名后缀
    SCHEMA,    // 共享 DB + 独立 Schema (PG/Oracle)
    DATABASE,  // 独立 DB instance
    HYBRID     // 按租户动态选 (从 sys_tenant 表读)
}
```

### TenantInfo (租户运行时信息)

```java
@Data @Builder
public class TenantInfo {
    private Long id;
    private String name;
    private TenantStatus status;          // ACTIVE/EXPIRED/MIGRATING/DISABLED
    private IsolationMode mode;           // 该租户实际隔离模式
    private String dataSourceName;        // DATABASE 模式: 数据源 key
    private String tableSuffix;           // TABLE 模式: 表名后缀
    private String schemaName;            // SCHEMA 模式: schema 名
    private OffsetDateTime expiredTime;
}
```

---

## 隔离模式详解

### COLUMN 模式

**最简单,默认模式**。共享整张业务表,所有租户的数据都在同一张表里,通过 `tenant_id` 列区分。

#### 改写效果示例

```sql
-- 业务 SQL (Mapper 写的)
SELECT * FROM orders WHERE id = 123;

-- 实际执行 (1001 租户上下文)
SELECT * FROM orders WHERE id = 123 AND tenant_id = 1001;
```

```sql
-- 业务 SQL
INSERT INTO orders (product, amount) VALUES ('X', 100);

-- 实际执行 (1001 租户上下文)
INSERT INTO orders (product, amount, tenant_id) VALUES ('X', 100, 1001);
```

#### 工作机制

| 拦截器 | 作用 | 触发条件 |
|--------|------|---------|
| `TenantStrategyInterceptor` | 调 `ColumnStrategy.beforeSqlExecute()` 做前置校验 | 总是 |
| `TenantLineInnerInterceptor` | 用 JSqlParser 改写 SQL,追加 `tenant_id` 条件/列 | COLUMN 模式总是改写;HYBRID 模式下若该租户为 COLUMN 模式才改写;TABLE/SCHEMA/DATABASE 模式**自动跳过** |
| `TenantMetaObjectHandler` | MyBatis-Plus INSERT 时填 `tenantId` 字段 | INSERT 实体有 `tenantId` 字段 |

#### 配置

```yaml
multi-tenancy:
  mode: COLUMN
  tenant-id-column: tenant_id     # 可改为 org_id 等
  ignore-tables:                  # 不做租户隔离的表
    - sys_dict
    - sys_config
```

#### DDL 约定

```sql
-- 业务表必须有 tenant_id 列
CREATE TABLE orders (
    id          BIGINT PRIMARY KEY,
    product     VARCHAR(100),
    amount      DECIMAL(10,2),
    tenant_id   BIGINT NOT NULL DEFAULT 0,
    INDEX idx_tenant (tenant_id)         -- 性能关键: tenant_id 必须建索引
);
```

#### 业务代码

**零侵入**。Mapper / Service 完全不用关心租户,框架自动改写。

```java
public interface OrderMapper extends BaseMapper<Order> {
    // 这里的方法签名看不出任何租户相关
    List<Order> selectByUserId(@Param("userId") Long userId);
}
```

#### 不用会怎样

| 不接的类 | 后果 |
|---------|------|
| 不接 `TenantLineInnerInterceptor` | SQL 无 `tenant_id` 条件 → **跨租户数据泄露** |
| 不接 `TenantMetaObjectHandler` | INSERT 不自动填 `tenant_id` → 数据 `tenant_id=0` 混在平台默认租户里 |
| 不接 `TenantStrategyInterceptor` | 不做租户信息查询,所有请求按 `mode` 配置走,无法按租户动态配置 |

#### 适用 / 不适用

✅ 适用: 租户数 10-1000,单租户数据量 < 100 万行
❌ 不适用: 单租户数据量极大(> 1000 万行,SQL 慢);合规要求物理隔离

---

### TABLE 模式

**共享库,但每个租户一套物理表**。表名后缀默认 `_${tenant}`,可改。

#### 改写效果示例

```sql
-- 业务 SQL
SELECT * FROM orders WHERE id = 123;

-- 1001 租户上下文实际执行
SELECT * FROM orders_1001 WHERE id = 123;
```

#### 工作机制

| 拦截器 | 作用 |
|--------|------|
| `TenantStrategyInterceptor` | 调 `TableStrategy.beforeSqlExecute()`,从 `TenantInfo.tableSuffix` 取后缀,写入 `TableSuffixHolder` |
| `DynamicTableNameInnerInterceptor` | 读 `TableSuffixHolder` 拿后缀,改写 SQL 中所有表名 |
| `TenantLineInnerInterceptor` | ✅ **自动跳过**(内部短路):TABLE 模式不需要 `tenant_id` 列改写 |

#### 配置

```yaml
multi-tenancy:
  mode: TABLE
  table-name-suffix: "_${tenant}"    # 默认,后缀模板
  ignore-tables:
    - sys_dict                       # 全局共享表,不改后缀
```

#### DDL 约定

```sql
-- 需要为每个租户手工或脚本创建表
CREATE TABLE orders_1001 (id BIGINT PRIMARY KEY, ...);
CREATE TABLE orders_1002 (id BIGINT PRIMARY KEY, ...);
CREATE TABLE orders_1003 (id BIGINT PRIMARY KEY, ...);
```

#### 业务代码

仍然零侵入,Mapper 写 `FROM orders`,框架改写为 `FROM orders_1001`。

#### 不用会怎样

| 不接的类 | 后果 |
|---------|------|
| 不接 `DynamicTableNameInnerInterceptor` | SQL 查 `orders` → 找不到表 → **SQL 报错** |
| 不接 `TableStrategy` | `TableSuffixHolder` 为空 → 拦截器无后缀可加 → **SQL 报错** |

#### 适用 / 不适用

✅ 适用: 单租户数据量极大(分表分散 IO),允许按租户手动建表
❌ 不适用: 租户数多(> 10000),DDL 维护爆炸;租户动态开通(无法预先建表)

---

### SCHEMA 模式

**共享 DB instance,每个租户独立 Schema**。仅 PostgreSQL/Oracle 支持。

#### 工作机制

| 拦截器/策略 | 作用 |
|------------|------|
| `SchemaStrategy.beforeSqlExecute` | (可选) 自动 CREATE SCHEMA;`SET LOCAL search_path TO <schema>` |
| `TenantLineInnerInterceptor` | ✅ **自动跳过**(内部短路):SCHEMA 模式不需要 `tenant_id` 列改写 |
| `DynamicTableNameInnerInterceptor` | ✅ **自动跳过**:`TableSuffixHolder` 在 SCHEMA 模式下为空,改写逻辑不会触发 |

> Schema 模式**不做 SQL 改写**。表名一致,靠 PostgreSQL 的 `search_path` 解析到对应 schema。
> 两个改写类拦截器在 SCHEMA 模式下均**自动跳过**(内部短路),无需手工把它们加入 `ignore-tables`。

> ⚠️ **前置条件 — 必须有活动事务**
>
> `SET LOCAL search_path` 是 PostgreSQL 的事务局部设置,**仅在事务内(autoCommit=false)生效**。
> 如果调用方未启用事务(MyBatis 默认 `autoCommit=true`),PG 会**静默忽略**该语句,数据写入错的 schema 而不报错。
>
> 本组件 `SchemaStrategy` 已做 fail-fast 检查:遇到非事务连接立即抛 `TENANT_SCHEMA_REQUIRES_TRANSACTION`(500) 异常,
> 避免 silent failure 导致的数据泄漏。
>
> ✅ 正确写法:**所有走 Schema 模式的业务方法必须加 `@Transactional` 注解**,或在外部开启事务后再调用。
>
> ```java
> @Service
> @Transactional  // 必须
> public class OrderService {
>     public Order findById(Long id) {  // 这里任何 SQL 都会在事务内执行
>         return orderMapper.selectById(id);  // SchemaStrategy 内部检查 autoCommit=false 才放行
>     }
> }
> ```
>
> ❌ 错误写法:不加 `@Transactional`,SchemaStrategy 抛 `TENANT_SCHEMA_REQUIRES_TRANSACTION`,立即 fail-fast 暴露问题。

#### 配置

```yaml
multi-tenancy:
  mode: SCHEMA
  schema-prefix: "tenant_"            # tenantId=1001 → schema "tenant_1001"
  schema-auto-create: true            # 首次访问自动 CREATE SCHEMA
```

#### 业务代码

零侵入,Mapper 写 `FROM orders`,PostgreSQL 自动从 `search_path` 解析到 `tenant_1001.orders`。

#### 不用会怎样

| 不接的类 | 后果 |
|---------|------|
| 不接 `SchemaStrategy` | `search_path` 不切换 → 查的是 `public` schema → **数据错乱/查不到** |

#### 适用 / 不适用

✅ 适用: PostgreSQL/Oracle,需要租户级 DDL 定制,租户数适中
❌ 不适用: MySQL (无 Schema 概念);租户数极大(> 5000,Schema 数量爆炸)

---

### DATABASE 模式

**每个租户独立 DB instance**。最强的物理隔离,支持跨 DB 类型(如部分租户用 PG,部分用 MySQL)。

#### 工作机制

| 组件 | 作用 |
|------|------|
| `TenantStrategyInterceptor` | 调 `DatabaseStrategy.beforeSqlExecute()`,从 `TenantInfo.dataSourceName` 取 key,写入 `DataSourceContextHolder` |
| `DynamicTenantDataSource` (extends `AbstractRoutingDataSource`) | Spring JDBC 调用时,按 `DataSourceContextHolder` key 路由到对应 DataSource |
| `DataSourceCircuitBreaker` | 熔断器,失败 N 次后该租户数据源短路 |
| `DataSourceHealthProbe` | 后台定时探测熔断中的数据源,半开恢复 |
| `TenantLineInnerInterceptor` | ✅ **自动跳过**(内部短路):DATABASE 模式不需要 `tenant_id` 列改写 |
| `DynamicTableNameInnerInterceptor` | ✅ **自动跳过**:`TableSuffixHolder` 在 DATABASE 模式下为空,改写逻辑不会触发 |

> 两个改写类拦截器在 DATABASE 模式下均**自动跳过**(内部短路),无需手工把它们加入 `ignore-tables`。

#### 配置

```yaml
multi-tenancy:
  mode: DATABASE
  datasource:
    shared:                              # 可选:用于 sys_tenant 等平台表
      url: jdbc:postgresql://platform-db/platform
      username: platform
      password: ${PLATFORM_PWD}
    tenants:
      "1001":                            # 租户 1001 独立库
        url: jdbc:postgresql://tenant-1001-db/tenant_1001
        username: tenant_1001
        password: ${TENANT_1001_PWD}
        hikari:
          maximum-pool-size: 20
      "1002":
        url: jdbc:mysql://tenant-1002-db:3306/tenant_1002
        username: tenant_1002
        password: ${TENANT_1002_PWD}
```

#### 注册 DynamicTenantDataSource

```java
@Configuration
public class MyDataSourceConfig {
    
    @Bean
    @Primary
    public DataSource dataSource(
        @Value("${multi-tenancy.datasource.shared.url}") String url,
        @Value("${multi-tenancy.datasource.shared.username}") String username,
        @Value("${multi-tenancy.datasource.shared.password}") String password,
        MultiTenancyProperties props
    ) {
        // 1. 创建 shared 数据源 (Hikari)
        HikariDataSource shared = new HikariDataSource();
        shared.setJdbcUrl(url);
        shared.setUsername(username);
        shared.setPassword(password);
        
        // 2. 创建 DynamicTenantDataSource
        DynamicTenantDataSource ds = new DynamicTenantDataSource(shared);
        
        // 3. 注册所有租户数据源
        for (var entry : props.getDatasource().getTenants().entrySet()) {
            TenantDataSourceConfig cfg = entry.getValue();
            HikariDataSource tenantDs = new HikariDataSource();
            tenantDs.setJdbcUrl(cfg.getUrl());
            tenantDs.setUsername(cfg.getUsername());
            tenantDs.setPassword(cfg.getPassword());
            ds.addTenantDataSource(entry.getKey(), tenantDs);
        }
        
        return ds;
    }
}
```

#### 业务代码

零侵入,Mapper 完全不感知底层是哪个库。

#### 不用会怎样

| 不接的类 | 后果 |
|---------|------|
| 不接 `DatabaseStrategy` | `DataSourceContextHolder` 为空 → 路由到 shared → **查错库** |
| 不注册 `DynamicTenantDataSource` | 走 shared 数据源 → **数据泄露到 shared** |

#### 适用 / 不适用

✅ 适用: 金融/医疗/政企,合规要求物理隔离;不同租户可用不同 DB 类型
❌ 不适用: 租户数多(> 1000),DB 实例管理成本爆炸

---

### HYBRID 模式

**按租户动态选模式**。`sys_tenant.isolation_mode` 字段决定该租户走哪种模式。

#### 工作机制

```
请求进入 → 拿到 tenantId
   ↓
TenantInfoProvider 返回 TenantInfo (含 mode 字段)
   ↓
TenantStrategyInterceptor 按 tenantInfo.mode 从工厂取策略
   ↓
委派给 ColumnStrategy / TableStrategy / SchemaStrategy / DatabaseStrategy
   ↓
执行该策略的 beforeSqlExecute()
```

`HybridStrategy` 内部 `switch(mode)` 编译期派发,新增 `IsolationMode` 会编译报错。

#### 改写类拦截器在 HYBRID 下的行为

| 拦截器 | HYBRID 下的行为 |
|--------|----------------|
| `TenantLineInnerInterceptor` | **按租户查实际模式** —— 调 `TenantInfoProvider.getTenantInfo(tenantId)`,若该租户是 COLUMN 模式才改写;若是 TABLE/SCHEMA/DATABASE 则跳过 |
| `DynamicTableNameInnerInterceptor` | 按租户查实际模式后,只有租户是 TABLE 模式且 `TableSuffixHolder` 有 suffix 才改写 |

> ⚠️ **性能注意**:`TenantLineInnerInterceptor` 在 HYBRID 模式下**每次 SQL 都查** `TenantInfoProvider`,接入方**必须**在自己的实现里加缓存(例如 Caffeine 60 秒 TTL),否则 P99 延迟会翻倍。

#### 配置

```yaml
multi-tenancy:
  mode: HYBRID        # 全局声明为混合模式
  # 其他字段保持默认,实际行为按 sys_tenant 表走
```

#### sys_tenant 表 (重要)

```sql
-- 1001 大客户 → 独立 DB
UPDATE sys_tenant SET isolation_mode = 'DATABASE', data_source_key = '1001' WHERE id = 1001;

-- 1002 中型客户 → 独立 schema
UPDATE sys_tenant SET isolation_mode = 'SCHEMA', schema_name = 'tenant_1002' WHERE id = 1002;

-- 1003 小客户 → 共享 column
UPDATE sys_tenant SET isolation_mode = 'COLUMN' WHERE id = 1003;
```

#### 适用 / 不适用

✅ 适用: 大型平台,租户异质(少数大租户 + 大量小租户)
❌ 不适用: 所有租户同质;团队人手少(多模式运维成本)

---

## Web 集成

### TenantIdentityFilter

`OncePerRequestFilter`,在 `Ordered.HIGHEST_PRECEDENCE + 500` 排序,**最早执行**。

**解析流程**:

1. **白名单检查** — 配置的路径(如 `/actuator/**`, `/login`)直接放行,不绑定租户
2. **从 JWT 解析** — `JwtUtils.getTenantPrincipal(token)` 拿 `TenantPrincipal`
3. **降级从 Header** — Feign 内部调用场景,`X-Tenant-ID` header 携带
4. **校验** — tenantId 必须是正整数
5. **查租户信息** — `TenantInfoProvider.getTenantInfo(tenantId)`,校验存在/过期/迁移中
6. **JWT ↔ Header 交叉校验** — 防 header 伪造
7. **`runWithTenant` 包裹整条 chain** — 保证整个请求生命周期租户上下文一致

### 超级管理员 (无租户用户)

JWT 中**无 `tenantId` claim** → 视为平台超管,**不绑定租户上下文**,直接放行。

适用场景: 平台管理后台、租户开通/审核、跨租户数据查询。

### 配置白名单

```java
@Component
public class MyWhitelistConfig {
    @Bean
    public List<String> tenantWhitelistPaths() {
        return List.of(
            "/actuator/",
            "/login",
            "/public/",
            "/v3/api-docs"
        );
    }
}
```

### 错误响应格式

```json
{
  "code": "TENANT_AUTH_EXPIRED",
  "msg": "Tenant account expired: 1001",
  "timestamp": 1719388400000,
  "data": null
}
```

---

## 事务管理

### 事务内租户冻结

**核心约束**: 同一事务内**禁止切换租户**。因为切换租户意味着数据源连接要换,但 Spring 事务的 Connection 已经在第一个 SQL 时绑定,中途换会脏读/泄露。

`TransactionTenantHolder` 在事务开启时记录"事务内租户",`runWithTenant(另一个租户, ...)` 时检测并拒绝。

```java
@Transactional
public void doSomething() {
    // 假设已经在租户 1001 上下文
    TenantContext.runWithTenant(newPrincipal(1002), () -> {
        // ❌ 抛 TenantSwitchInTransactionException
    });
}
```

### 设计意图

事务是"逻辑工作单元",跨租户混操作破坏 ACID。组件选择**强一致**而非灵活。

---

## 异步与多线程

### 核心问题

Java `Thread.start()` / `ExecutorService.submit()` / `@Async` 默认不继承 `ScopedValue`。子线程 `TenantContext.get()` 返回 `null` → 拦截器跳过 → SQL 不带租户条件 → **数据泄露**。

### 解决方案 (按推荐度排序)

#### 方案 1: `StructuredTaskScope` (推荐,JDK 21+)

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Task<Order> t1 = scope.fork(() -> queryOrderFromDb());    // 自动继承 ScopedValue
    Task<User> t2 = scope.fork(() -> queryUserFromCache());   // 自动继承
    
    scope.join();
    scope.throwIfFailed();
    
    return new Result(t1.get(), t2.get());
}
```

✅ **ScopedValue 自动继承**,无额外配置
✅ 子任务生命周期受父任务约束(结构化并发)
❌ 仅 JDK 21+ 支持

#### 方案 2: `TenantTaskDecorator` (自动)

**框架默认已配**。`TenantTaskDecoratorBeanPostProcessor` 是 `BeanPostProcessor`,Spring 启动时自动给所有 `ThreadPoolTaskExecutor` / `ThreadPoolTaskScheduler` 注入 `TenantTaskDecorator`,**业务代码零感知**。

```java
@Async
public CompletableFuture<Order> asyncQuery(Long orderId) {
    // 子线程能拿到当前租户 ID
    return CompletableFuture.completedFuture(orderMapper.selectById(orderId));
}
```

机制: `TenantTaskDecorator` 基于 micrometer `ContextSnapshot`,提交任务时**捕获**当前线程所有 `ThreadLocalAccessor` 值,执行时**恢复**。覆盖租户上下文、数据源路由、表名后缀等全部上下文。

#### 方案 3: 手动传递 (兜底)

```java
TenantPrincipal p = TenantContext.get();
executor.submit(() -> {
    TenantContext.runWithTenant(p, () -> {
        // 业务代码
    });
});
```

仅在方案 1/2 都不适用时用(自定义线程池,且 BPP 未生效)。

### 覆盖 vs 失效场景

| 场景 | 是否继承上下文 | 备注 |
|------|--------------|------|
| 同一线程 | ✅ | ScopedValue / ThreadLocal 默认 |
| `StructuredTaskScope.fork()` | ✅ | 结构化并发 |
| `@Async` 线程池 | ✅ | BPP 自动注入 |
| `CompletableFuture.supplyAsync(supplier, executor)` | ✅ | executor 是 Spring 托管的 `ThreadPoolTaskExecutor` 时 |
| `new Thread(() -> {...}).start()` | ❌ | **必须**手动包 `runWithTenant` |
| `ForkJoinPool.commonPool()` | ❌ | 公共池,BPP 无法注入 |
| WebFlux `Mono`/`Flux` operator chain | ❌ | Reactive 不传播 ScopedValue |
| Spring `@Scheduled` 定时任务 | ✅ | BPP 注入到 `ThreadPoolTaskScheduler` |

---

## 数据源路由与熔断

### Database 模式下的数据源路由

```
请求 → TenantIdentityFilter 绑定 tenant=1001
   ↓
业务代码 → orderMapper.selectById(...)
   ↓
TenantStrategyInterceptor → DatabaseStrategy.beforeSqlExecute()
   ↓
   调 TenantInfoProvider 拿 TenantInfo (含 dataSourceName)
   ↓
   写入 DataSourceContextHolder.set("1001")
   ↓
SQL 执行 → MyBatis → Spring JDBC
   ↓
DynamicTenantDataSource.determineCurrentLookupKey()
   ↓
   读 DataSourceContextHolder.get() → "1001"
   ↓
路由到租户 1001 的 HikariDataSource
   ↓
真正执行 SQL
```

### 熔断器

`DataSourceCircuitBreaker` 防止**单个租户的数据源故障**拖垮整个应用。

**熔断状态机**:
```
CLOSED (正常) ── 连续失败 N 次 ──→ OPEN (熔断,直接拒绝)
                                       │
                                       │ openWindowMs 后
                                       ↓
                                  HALF_OPEN (放一次探测)
                                       │
                              ┌────────┴────────┐
                              ↓                 ↓
                            成功              失败
                              ↓                 ↓
                          CLOSED              OPEN
```

**配置**:
```yaml
multi-tenancy:
  circuit:
    failure-threshold: 5         # 连续失败 5 次熔断
    open-window-ms: 30000        # 熔断 30s 后进入半开
  health:
    probe-interval-ms: 30000     # 健康探测间隔
```

**触发场景**:
- 租户数据库连接超时
- 租户数据库 CPU 100%
- 租户数据源密码过期

**故障表现**:
- 触发 `DataSourceUnavailableException` → HTTP 503
- 错误码 `TENANT_DATA_SOURCE_UNAVAILABLE` (单租户) 或 `TENANT_SHARED_DS_UNAVAILABLE` (shared)

### 灰度发布

支持某租户走"灰度数据源"(独立 URL),与全量数据源物理隔离。

```yaml
multi-tenancy:
  datasource:
    tenants:
      "1001":
        url: jdbc:postgresql://prod-db/tenant_1001
        canary-url: jdbc:postgresql://canary-db/tenant_1001   # 灰度 URL
  canary:
    tenants:
      - id: 1001
        ratio: 100                                            # 100% 灰度
```

---

## 配置参考

### 完整配置项 (MultiTenancyProperties)

| 配置 | 默认值 | 说明 |
|------|-------|------|
| `multi-tenancy.enabled` | `true` | 总开关。`false` 时所有拦截器/策略跳过 |
| `multi-tenancy.mode` | `COLUMN` | 全局默认模式 (Hybrid 时按 `sys_tenant.isolation_mode`) |
| `multi-tenancy.tenant-id-header` | `X-Tenant-ID` | 租户 ID header 名 |
| `multi-tenancy.enforce-auth-tenant` | `true` | 是否强制要求 JWT tenantId 存在且合法 |
| `multi-tenancy.tenant-id-column` | `tenant_id` | Column 模式的列名 |
| `multi-tenancy.ignore-tables` | `[]` | 跳过租户隔离的表名列表 |
| `multi-tenancy.table-name-suffix` | `_${tenant}` | Table 模式的表名后缀模板 |
| `multi-tenancy.schema-prefix` | `tenant_` | Schema 模式的 schema 前缀 |
| `multi-tenancy.schema-auto-create` | `false` | Schema 模式是否自动 CREATE SCHEMA |
| `multi-tenancy.force-thread-local` | `false` | 强制 ThreadLocalHolder 降级 |
| `multi-tenancy.microservice` | `true` | 是否微服务架构 (false 跳过通信框架检测) |
| `multi-tenancy.datasource.shared.*` | — | shared 数据源 (Database 模式 sys_tenant 表) |
| `multi-tenancy.datasource.tenants.*` | `{}` | 租户独立数据源 Map (key=tenantId) |
| `multi-tenancy.canary.tenants` | `[]` | 灰度租户列表 |
| `multi-tenancy.circuit.failure-threshold` | `5` | 熔断失败阈值 |
| `multi-tenancy.circuit.open-window-ms` | `30000` | 熔断打开后等待时间 (ms) |
| `multi-tenancy.health.probe-interval-ms` | `30000` | 健康探测间隔 (ms) |

### 通信框架依赖

微服务模式 (`microservice=true`) 需要引入通信框架之一:

| 框架 | 引入依赖 |
|------|---------|
| HTTP (Feign / RestClient) | `atlas-richie-component-microservice` |
| gRPC | `atlas-richie-component-grpc` |

未引入会 WARN 启动日志,但不阻断。`microservice=false` 跳过检测。

### 启动日志参考

正常启动:
```
TenantContext initialized with ScopedValue (preferred for virtual threads)
[多租户] 微服务通信框架已就绪: HTTP (Feign/RestClient)
```

降级模式:
```
TenantContext initialized with ThreadLocal + micrometer context-propagation (force-thread-local=true)
```

未配通信框架 (微服务模式):
```
[多租户] 微服务模式已开启但未检测到通信框架！跨服务调用时租户上下文将中断。
  请根据通信协议引入其中一个组件:
    • HTTP (Feign/RestClient) → atlas-richie-component-microservice
    • gRPC                    → atlas-richie-component-grpc
  若为单体应用,请设置 multi-tenancy.microservice=false 关闭此检测
```

---

## SPI 扩展点

### TenantInfoProvider (必实现)

```java
public interface TenantInfoProvider {
    /** 拿租户完整信息,返回 null 表示租户不存在 */
    TenantInfo getTenantInfo(Long tenantId);
    
    /** 租户是否存在(轻量检查) */
    boolean exists(Long tenantId);
}
```

> ⚠️ **必须实现此 SPI**。组件默认 NoOp 实现返回 `null`,会导致所有 SQL 抛 `TenantNotFoundException`。

> ⚠️ **必须加缓存**。`TenantInfoProvider.getTenantInfo(tenantId)` 在 COLUMN 模式 + HYBRID 模式下被 `TenantLineInnerInterceptor` 和 `TenantStrategyInterceptor` **每次 SQL 都调用**,如果实现里直接查 `sys_tenant` 表,P99 延迟会爆炸。建议接入方用 Caffeine 加 30-60 秒 TTL 缓存(`@Cacheable` 也可),租户状态变更时通过消息广播失效。

---

## API 全签名参考

业务开发者只需要熟悉本节列出的 facade 类;`interceptor/`、`strategy/`、`datasource/` 下的类是**框架内部组件**,业务代码不应直接调用。

### 业务必用 facade

#### `com.richie.component.tenant.context.TenantContext` (静态 facade)

```java
public final class TenantContext {

    // ========== 初始化(框架启动时调用,业务代码不要碰) ==========
    public static void init(TenantContextHolder tenantContextHolder);
    public static TenantContextHolder getHolder();

    // ========== 读取 API(任何代码都可调用) ==========
    public static TenantPrincipal get();                      // null = 未绑定
    public static Long getTenantId();                         // null = 未绑定
    public static String getTenantName();                     // null = 未绑定
    public static TenantPrincipal require();                  // 抛 BusinessException
    public static Long requireTenantId();                     // 抛 BusinessException

    // ========== 绑定 API(入口层调用:Filter / MQ / 定时任务) ==========
    public static void runWithTenant(TenantPrincipal principal, Runnable task);
    public static <T> T runWithTenant(TenantPrincipal principal, Supplier<T> task);

    // ========== 清理(几乎用不到,runWithTenant 自动管理) ==========
    public static void clear();
}
```

**典型用法**:

```java
// 1. 业务代码读取当前租户
@GetMapping("/orders")
public List<Order> list() {
    Long tenantId = TenantContext.requireTenantId();          // 强制租户上下文
    return orderMapper.selectByTenant(tenantId);
}

// 2. MQ 消费者入口绑定租户
@RabbitListener(queues = "tenant.orders")
public void onMessage(OrderEvent event) {
    TenantPrincipal principal = new TenantPrincipal()
        .setTenantId(event.getTenantId())
        .setTenantName(event.getTenantName());
    TenantContext.runWithTenant(principal, () -> {
        orderService.apply(event);
    });
}

// 3. 定时任务入口绑定租户(遍历所有租户)
@Scheduled(cron = "0 0 2 * * ?")
public void dailyReport() {
    tenantRepository.findActiveIds().forEach(tenantId -> {
        TenantPrincipal p = new TenantPrincipal().setTenantId(tenantId);
        TenantContext.runWithTenant(p, () -> reportService.generate(tenantId));
    });
}
```

#### `com.richie.contract.model.TenantPrincipal` (共享契约)

```java
@Data
@Accessors(chain = true)
public class TenantPrincipal implements Serializable {
    private Long tenantId;                  // 数据库主键,正整数
    private String tenantName;              // 展示名(日志/审计用)
    private OffsetDateTime expiredTime;     // 过期时间;null = 永不过期
}
```

> 来源模块: `atlas-richie-contract`,所有上层组件都依赖它。

### 枚举类

#### `com.richie.component.tenant.model.IsolationMode`

```java
public enum IsolationMode {
    COLUMN,    // 列隔离:共享库+共享表+tenant_id 列
    TABLE,     // 表隔离:共享库+_${tenant} 后缀表
    SCHEMA,    // Schema 隔离:同库实例不同 schema(仅 PG/Oracle)
    DATABASE,  // 数据库隔离:每个租户独立 DB
    HYBRID     // 混合:按租户 sys_tenant.isolation_mode 字段分派
}
```

#### `com.richie.component.tenant.model.TenantStatus`

```java
public enum TenantStatus {
    ACTIVE,        // 活跃,可访问
    INACTIVE,      // 停用,拒绝访问
    MIGRATING,     // 迁移中,返回 503
    PROVISIONING,  // 初始化中,未就绪
    EXPIRED        // 已过期,拒绝访问
}
```

### 配置 properties

#### `com.richie.component.tenant.config.MultiTenancyProperties`

```java
@Data
@ConfigurationProperties(prefix = "multi-tenancy")
public class MultiTenancyProperties {
    private boolean enabled = true;                              // 总开关
    private IsolationMode mode = IsolationMode.COLUMN;          // 默认模式
    private String tenantIdHeader = "X-Tenant-ID";               // HTTP header key
    private boolean enforceAuthTenant = true;                    // JWT ↔ Header 交叉校验
    private String tenantIdColumn = "tenant_id";                 // 列名
    private List<String> ignoreTables = new ArrayList<>();       // 不隔离的表
    private String tableNameSuffix = "_${tenant}";               // TABLE 模式后缀
    private String schemaPrefix = "tenant_";                     // SCHEMA 模式前缀
    private boolean schemaAutoCreate = false;                    // 是否自动 CREATE SCHEMA
    private boolean forceThreadLocal = false;                    // 降级开关
    private boolean microservice = true;                         // 单体应用设为 false
    private DataSourceConfig datasource = new DataSourceConfig();
    private CanaryConfig canary = new CanaryConfig();
    private CircuitBreakerConfig circuit = new CircuitBreakerConfig();
    private HealthProbeConfig health = new HealthProbeConfig();

    @Data public static class DataSourceConfig {
        private SharedDataSourceConfig shared = new SharedDataSourceConfig();
        private Map<String, TenantDataSourceConfig> tenants = new HashMap<>();
    }
    @Data public static class SharedDataSourceConfig {
        private String url, username, password;
        private HikariConfig hikari = new HikariConfig();
    }
    @Data public static class TenantDataSourceConfig {
        private String url, username, password;
        private String canaryUrl;                                // 灰度 URL
        private HikariConfig hikari;                             // null = 继承 shared
    }
    @Data public static class HikariConfig {
        private int maximumPoolSize = 0;                         // 0 = 走 Spring Boot 默认
        private int minimumIdle = 0;
        private long idleTimeout = 0;
        private long connectionTimeout = 0;
    }
    @Data public static class CanaryConfig {
        private List<CanaryTenant> tenants = new ArrayList<>();
    }
    @Data public static class CanaryTenant {
        private Long id;
        private int ratio = 100;                                 // 0-100
    }
    @Data public static class CircuitBreakerConfig {
        private int failureThreshold = 5;
        private long openWindowMs = 30_000;
    }
    @Data public static class HealthProbeConfig {
        private long probeIntervalMs = 30_000;
    }
}
```

### SPI 实现接口

#### `com.richie.component.tenant.spi.TenantInfoProvider` (必实现)

```java
public interface TenantInfoProvider {
    TenantInfo getTenantInfo(Long tenantId);    // null = 租户不存在
    boolean exists(Long tenantId);
}
```

**实现骨架**(必须加缓存):

```java
@Component
public class CachedTenantInfoProvider implements TenantInfoProvider {

    private final SysTenantMapper mapper;                       // 你的 sys_tenant Mapper
    private final Cache<Long, TenantInfo> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(10_000)
        .build();

    @Override
    public TenantInfo getTenantInfo(Long tenantId) {
        return cache.get(tenantId, this::loadFromDb);
    }

    @Override
    public boolean exists(Long tenantId) {
        return getTenantInfo(tenantId) != null;
    }

    private TenantInfo loadFromDb(Long tenantId) {
        SysTenant entity = mapper.selectById(tenantId);
        if (entity == null) return null;
        return new TenantInfo()
            .setTenantId(entity.getId())
            .setMode(IsolationMode.valueOf(entity.getIsolationMode()))
            .setDataSourceName(entity.getDataSourceName())
            .setSchemaName(entity.getSchemaName())
            .setTableSuffix(entity.getTableSuffix())
            .setStatus(TenantStatus.valueOf(entity.getStatus()))
            .setCanary(entity.getCanary() != null && entity.getCanary());
    }
}
```

#### `com.richie.component.tenant.context.TenantContext.TransactionTenantChecker` (内部 SPI,框架调用)

```java
@FunctionalInterface
public interface TransactionTenantChecker {
    void check(Long targetTenantId);   // 抛 TenantSwitchInTransactionException = 拒绝
}
```

> 业务**不要**实现这个 — 框架的 `TransactionTenantHolder` 已提供默认实现。

### 异常体系

```
RuntimeException
├── BusinessException                  (通用业务异常,code = "TENANT_BUSINESS_ERROR" 或自定义)
├── TenantNotFoundException            (租户在 sys_tenant 不存在)
├── DataSourceUnavailableException     (数据源熔断中)
├── TenantSwitchInTransactionException (事务内切换租户)
├── TenantMigratingException           (租户迁移中)
├── TenantModeMigrationException       (模式迁移被拒)
└── TenantProvisionException           (租户开通失败)
```

**统一处理**: `TenantExceptionHandler` (`@RestControllerAdvice`) 已自动捕获所有上述异常并转为 `{code, message}` JSON 响应。HTTP 状态码取自 `TenantErrorCode.httpStatus`。

### 框架内部组件(业务不应直接调用,列出便于查阅)

| 类 | 包 | 业务调用? |
|---|----|----------|
| `TenancyStrategy` (interface) | `strategy` | ❌ |
| `TenancyStrategyFactory` | `strategy` | ❌ |
| `ColumnStrategy` / `TableStrategy` / `SchemaStrategy` / `DatabaseStrategy` / `HybridStrategy` | `strategy` | ❌ |
| `AbstractTenancyStrategy` | `strategy` | ❌ |
| `TenantLineInnerInterceptor` | `interceptor` | ❌(MyBatis plugin 自动触发) |
| `TenantStrategyInterceptor` | `interceptor` | ❌ |
| `DynamicTableNameInnerInterceptor` | `interceptor` | ❌ |
| `ConnectionResetInterceptor` | `interceptor` | ❌ |
| `DynamicTenantDataSource` | `datasource` | ❌(Spring JDBC 自动路由) |
| `DataSourceCircuitBreaker` / `DataSourceHealthProbe` | `circuit` | ❌ |
| `DataSourceContextHolder` / `TableSuffixHolder` / `TransactionTenantHolder` | `context` | ❌ |
| `TenantIdentityFilter` | `web` | ❌(Servlet Filter 自动生效) |
| `TenantExceptionHandler` / `TenantMetaObjectHandler` | `handler` | ❌(全局 @RestControllerAdvice / MyBatis-Plus 自动调用) |
| `TenantTaskDecorator` | `cross` | ⚠️ 仅在自定义非 `@Bean` 线程池时需要手动装配 |
| `TenantTaskDecoratorBeanPostProcessor` | `cross` | ❌(BPP 自动生效) |

### 未来扩展点 (保留)

`TransactionTenantChecker` — 事务内租户切换检测器,默认由 `TransactionTenantHolder` 实现,业务可覆盖。

---

## 错误码参考

`TenantErrorCode` 枚举,所有租户相关异常统一从这里取。

| 错误码 | HTTP | 含义 | 触发场景 |
|--------|------|------|---------|
| `TENANT_AUTH_MISSING_TOKEN` | 401 | 无认证 token | 未登录或 token 过期 |
| `TENANT_AUTH_BLANK_CLAIM` | 403 | JWT tenantId 为空字符串 | token 签发时没填租户 |
| `TENANT_AUTH_INVALID_FORMAT` | 403 | tenantId 格式非法 | 非数字/负数/0 |
| `TENANT_AUTH_EXPIRED` | 403 | 租户已过期 | `expiredTime` < 当前时间 |
| `TENANT_AUTH_MISMATCH` | 403 | JWT 与 Header 不一致 | 防 header 伪造 |
| `TENANT_IDENTITY_NOT_FOUND` | 403 | 租户未注册 | `sys_tenant` 表不存在 |
| `TENANT_NOT_FOUND` | 404 | 策略调度时找不到租户 | `TenantInfoProvider` 返回 null |
| `TENANT_DATA_SOURCE_UNAVAILABLE` | 503 | 租户数据源熔断 | Database 模式熔断器打开 |
| `TENANT_SHARED_DS_UNAVAILABLE` | 503 | shared 数据源熔断 | Column/Table/Schema 模式 |
| `TENANT_SWITCH_IN_TRANSACTION` | 403 | 事务内切换租户 | 同事务 runWithTenant 两次 |
| `TENANT_MIGRATING` | 503 | 租户迁移中 | `status=MIGRATING` |
| `TENANT_MODE_MIGRATION_DENIED` | 403 | 模式迁移被拒 | 管理接口限制 |
| `TENANT_PROVISION_FAILED` | 500 | 租户开通失败 | 资源分配错误 |
| `TENANT_SCHEMA_REQUIRES_TRANSACTION` | 500 | SCHEMA 模式要求事务 | `SchemaStrategy` 检测到 `autoCommit=true`(`SET LOCAL search_path` 静默失效的高危场景) |
| `TENANT_ADMIN_REQUIRED` | 403 | 非管理员访问管理接口 | 缺 platform administrator 角色 |

---

## 注意事项与陷阱

### 🚨 高频踩坑

#### 1. 没实现 `TenantInfoProvider`

**症状**: 启动正常,第一个 SQL 抛 `TenantNotFoundException: Tenant not found: null`

**原因**: 组件默认 NoOp 实现返回 `null`,所有 `tenantId` 都被认为"租户不存在"

**解决**: 必须实现 `TenantInfoProvider`,查 `sys_tenant` 表

#### 2. 业务表没建 `tenant_id` 索引

**症状**: Column 模式全表扫描,租户多了之后 SQL 慢到无法接受

**解决**: `CREATE INDEX idx_tenant ON orders(tenant_id);` 任何走 `tenant_id` 过滤的列都要建索引

#### 3. INSERT 时业务代码手动设了 `tenantId`

**症状**: `tenantMetaObjectHandler.strictInsertFill` 抛"严格模式拒绝覆盖"

**原因**: `MetaObjectHandler.strictInsertFill` 拒绝覆盖已存在的值

**解决**: INSERT 的业务代码**不要**手动设 `tenantId`,交给 `TenantMetaObjectHandler` 自动填

#### 4. `@Async` 任务查不到租户

**症状**: `@Async` 内部 `TenantContext.getTenantId()` 返回 `null`

**解决**:
- 确认项目用了 Spring 托管的 `ThreadPoolTaskExecutor` (而非 `Executors.newFixedThreadPool()`)
- `TenantTaskDecoratorBeanPostProcessor` 已注册 (默认自动注册)
- 自定义 executor Bean 加 `@Bean` 注解才会被 BPP 处理

#### 5. 事务内切租户

**症状**: `TenantSwitchInTransactionException`

**原因**: 设计约束,见 [事务管理](#事务管理)

**解决**: 业务重构,把跨租户操作拆成两个独立事务

#### 6. WebFlux/Reactive 项目用不了

**症状**: Mono/Flux 链中 `TenantContext.get()` 始终 null

**原因**: Reactive 算子不传播 ScopedValue,且本组件没适配 WebFlux

**解决**:
- 项目用 Servlet Web (Spring MVC) 而非 WebFlux
- 如果必须用 WebFlux,自己实现 `Mono.deferContextual` capture/restore 逻辑

#### 7. Table 模式 join 了 sys_dict

**症状**: SQL 报 `relation "sys_dict_1001" does not exist`

**原因**: `DynamicTableNameInnerInterceptor` 给所有表加后缀,sys_dict 也要加

**解决**: 把 sys_dict 加到 `ignore-tables`

#### 8. Schema 模式忘了 `search_path` 隔离

**症状**: 1001 租户查到 1002 租户的数据

**原因**: 业务代码直连 `public` schema 而非走 `search_path` 路由

**解决**: 100% SQL 必须经过 `SchemaStrategy` 切换的 connection,别绕过

#### 9. Database 模式共享数据源误用

**症状**: 1001 租户数据写到了 shared 库

**原因**: `DatabaseStrategy` 没触发,`DataSourceContextHolder` 是空的

**解决**: 确认 `TenantStrategyInterceptor` 触发(看日志),`sys_tenant.isolation_mode='DATABASE'` 没写错

#### 10. 跨服务调用租户上下文丢失

**症状**: A 服务有租户上下文,调 B 服务后 B 服务 `TenantContext.get()` 是 null

**解决**: 引入 `atlas-richie-component-microservice` (HTTP) 或 `atlas-richie-component-grpc` (gRPC),由它们处理出/入站 header 透传

#### 11. `TenantInfoProvider` 未缓存导致 P99 延迟爆炸

**症状**: COLUMN 模式或 HYBRID 模式上线后,慢 SQL P99 从 50ms 涨到 500ms

**原因**: `TenantLineInnerInterceptor` 和 `TenantStrategyInterceptor` **每次 SQL 都调用** `TenantInfoProvider.getTenantInfo(tenantId)`,如果实现里直接查 `sys_tenant` 表,等于每次业务查询都多一次 DB 查询

**解决**: 在 `TenantInfoProvider` 实现里加缓存:

```java
@Component
public class CachedTenantInfoProvider implements TenantInfoProvider {
    private final Cache<Long, TenantInfo> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(10_000)
        .build();
    
    @Override
    public TenantInfo getTenantInfo(Long tenantId) {
        return cache.get(tenantId, this::loadFromDb);  // 缓存未命中才查 DB
    }
}
```

#### 12. SCHEMA 模式没加 `@Transactional` 导致数据写入错的 schema(史上最难排查的 silent failure)

**症状**: `org.postgresql.util.PSQLException: ERROR: relation "xxx" does not exist` 或者**更隐蔽**:数据写到了 `public` schema 而不报错(只在自己查数据时才发现)

**原因**:`SET LOCAL search_path` 是 PG 事务局部语句,**只在事务内生效**。MyBatis 默认 `autoCommit=true`,PG 会**静默忽略** `SET LOCAL`,不报错,但 search_path 没切换。

**解决**: 所有走 SCHEMA 模式的业务方法必须加 `@Transactional`(或 `TransactionTemplate` 包裹),让连接 `autoCommit=false`。

```java
@Service
@Transactional  // 必须,否则 SchemaStrategy 抛 TENANT_SCHEMA_REQUIRES_TRANSACTION
public class OrderService {
    public Order findById(Long id) { return orderMapper.selectById(id); }
}
```

> 修复版本(自 v2.1.0 起)已主动 fail-fast:遇到非事务连接抛 `TENANT_SCHEMA_REQUIRES_TRANSACTION`,不再静默写入错的 schema。
> 升级到 v2.1.0+ 即可获得该保护,无需改业务代码——之前 silent failure 现在会立即报错。

### 🟡 性能注意事项

#### Column 模式 `tenant_id` 必须放复合索引最左前缀

```sql
-- ✅ 走索引
INDEX idx_tenant_user (tenant_id, user_id)
SELECT * FROM orders WHERE tenant_id = 1001 AND user_id = 123;

-- ❌ 不走索引
INDEX idx_tenant (tenant_id)
SELECT * FROM orders WHERE user_id = 123 AND tenant_id = 1001;
-- (MySQL 优化器可能自动调整,PostgreSQL 不一定)
```

#### Table/Schema 模式避免跨租户 JOIN

```sql
-- ❌ 跨租户 join,1001 上下文查 orders_1001 JOIN users_1002,1002 是公共表不会加后缀
-- 会报错 "relation users_1002 does not exist"
SELECT * FROM orders o JOIN users_1002 u ON o.user_id = u.id;
```

公共关联表必须加到 `ignore-tables` 或放在 `public` schema。

### 🟢 最佳实践

1. **COLUMN 模式**: DDL `tenant_id BIGINT NOT NULL DEFAULT 0`,业务代码**永不**手动设 `tenantId`
2. **JVM 调优**: ScopedValue 模式无 ThreadLocal 内存泄漏,推荐生产使用;`force-thread-local=true` 仅作为降级兜底
3. **微服务**: 出/入站 header 透传交给 `atlas-richie-component-microservice` 或 `atlas-richie-component-grpc`,业务无感
4. **灰度**: 新租户上线先用 `canary-url` 灰度,验证通过再切全量
5. **熔断**: 监控 `DataSourceCircuitBreaker` 状态,熔断时人工介入排查数据库
6. **错误码**: 业务异常用 `TenantErrorCode` 枚举,禁止硬编码魔法字符串

---

## 文档索引

### 本文档

| 章节 | 内容 |
|------|------|
| [5 种隔离模式](#5-种隔离模式--选型决策树) | 选型决策树 + 对比矩阵 |
| [快速开始](#快速开始) | 6 步接入 |
| [架构总览](#架构总览) | 全链路流程图 + 上下文传播矩阵 |
| [隔离模式详解](#隔离模式详解) | 5 个模式每个的改写效果、机制、配置、DDL、不用会怎样 |
| [Web 集成](#web-集成) | TenantIdentityFilter 解析流程 + 白名单 + 超管 |
| [事务管理](#事务管理) | 事务内冻结 |
| [异步与多线程](#异步与多线程) | 3 种异步场景方案 + 覆盖矩阵 |
| [数据源路由与熔断](#数据源路由与熔断) | Database 模式路由 + 熔断状态机 + 灰度 |
| [配置参考](#配置参考) | 完整 properties + 启动日志参考 |
| [错误码参考](#错误码参考) | TenantErrorCode 14 个错误码 |
| [注意事项与陷阱](#注意事项与陷阱) | 10 个高频踩坑 + 性能 + 最佳实践 |

### 详细设计 (docs/ 目录)

| 文档 | 内容 |
|------|------|
| [多租户 MyBatis-Plus 通用插件概念设计](docs/多租户MyBatis-Plus通用插件概念设计.md) | 概念、技术架构、数据模型、业务流程 |
| [上下文模块详细设计](docs/上下文模块详细设计.md) | TenantContextHolder SPI、ScopedValue / ThreadLocal 双实现 |
| [策略模块详细设计](docs/策略模块详细设计.md) | TenancyStrategy 工厂、五种隔离策略 |
| [持久层路由与拦截器集成模块详细设计](docs/持久层路由与拦截器集成模块详细设计.md) | DynamicTenantDataSource、拦截器链、事务冻结 |
| [可运维与灰度增强详细设计](docs/可运维与灰度增强详细设计.md) | 健康检查、灰度发布、动态配置刷新 |
| [租户生命周期详细设计](docs/租户生命周期详细设计.md) | 租户开通/迁移/回收全流程 |
| [模式切换数据迁移方案](docs/模式切换数据迁移方案.md) | COLUMN → SCHEMA → DATABASE 模式升级路径 |
| [多租户方案设计](docs/多租户方案设计.md) | 总体方案概览 |
| [多租户设计阅读导览](docs/多租户设计阅读导览.md) | 文档阅读建议 |

### 测试覆盖 (src/test/)

**单元测试** (206 case):
- `context/` — TenantContext、ThreadLocalHolder、ScopedValueHolder、TableSuffixHolder、DataSourceContextHolder、TransactionTenantHolder
- `cross/` — TenantTaskDecorator、TenantTaskDecoratorBeanPostProcessor
- `interceptor/` — TenantLineInnerInterceptor、TenantStrategyInterceptor、DynamicTableNameInnerInterceptor、ConnectionResetInterceptor
- `handler/` — TenantExceptionHandler、TenantMetaObjectHandler
- `strategy/` — 5 个策略的单元测试
- `circuit/` — DataSourceCircuitBreaker、DataSourceHealthProbe
- `web/` — TenantIdentityFilter
- `exception/` — 异常类 + TenantErrorCode
- `datasource/` — DynamicTenantDataSource
- `config/` — MultiTenancyProperties

**集成测试** (63 case,真跑 Testcontainers PostgreSQL):
- `SchemaStrategyIT` — Schema 自动创建、search_path 切换、跨 Schema 隔离、名称校验
- `ColumnStrategyIT` — SQL 改写真跑 + 多租户行级隔离 (INSERT/SELECT/UPDATE/DELETE)
- `TableStrategyIT` — 表级后缀隔离 + SQL 改写真跑
- `DatabaseStrategyIT` — 数据源路由 + 跨数据库真实隔离
- `HybridStrategyIT` — 多模式动态委派
- `TenantAutoConfigurationIT` — Spring 上下文装配 (9 个 Bean 验证)

---

## 端到端最小可运行示例

本节提供一个**可直接 `mvn spring-boot:run` 跑起来**的最小应用骨架。复制粘贴即可作为新业务接入多租户的起点。

### 1. Maven 依赖

```xml
<dependencies>
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-tenant</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>3.5.16</version>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

### 2. 主类

```java
package com.example.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan                       // 让 MultiTenancyProperties 生效
@MapperScan("com.example.app.mapper")              // 你的 MyBatis Mapper 包
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

### 3. sys_tenant DDL

```sql
CREATE TABLE sys_tenant (
    id              BIGINT PRIMARY KEY,
    tenant_name     VARCHAR(200) NOT NULL,
    isolation_mode  VARCHAR(20)  NOT NULL DEFAULT 'COLUMN',  -- COLUMN/TABLE/SCHEMA/DATABASE/HYBRID
    data_source_name VARCHAR(100),                            -- 仅 DATABASE 模式使用
    schema_name     VARCHAR(100),                             -- 仅 SCHEMA 模式使用
    table_suffix    VARCHAR(50),                              -- 仅 TABLE 模式使用
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE/INACTIVE/MIGRATING/PROVISIONING/EXPIRED
    canary          BOOLEAN      NOT NULL DEFAULT FALSE,
    expired_time    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO sys_tenant (id, tenant_name, isolation_mode) VALUES
    (1001, 'Acme Corp',     'COLUMN'),
    (1002, 'Beta Industries', 'COLUMN');
```

### 4. TenantInfoProvider 实现(必加缓存)

```java
package com.example.app.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.entity.SysTenant;
import com.example.app.mapper.SysTenantMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CachedTenantInfoProvider implements TenantInfoProvider {

    private final SysTenantMapper mapper;
    private final Cache<Long, TenantInfo> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(10_000)
        .build();

    public CachedTenantInfoProvider(SysTenantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TenantInfo getTenantInfo(Long tenantId) {
        return cache.get(tenantId, this::loadFromDb);
    }

    @Override
    public boolean exists(Long tenantId) {
        return getTenantInfo(tenantId) != null;
    }

    private TenantInfo loadFromDb(Long tenantId) {
        SysTenant entity = mapper.selectOne(
            new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getId, tenantId));
        if (entity == null) return null;

        return new TenantInfo()
            .setTenantId(entity.getId())
            .setMode(IsolationMode.valueOf(entity.getIsolationMode()))
            .setDataSourceName(entity.getDataSourceName())
            .setSchemaName(entity.getSchemaName())
            .setTableSuffix(entity.getTableSuffix())
            .setStatus(TenantStatus.valueOf(entity.getStatus()))
            .setCanary(Boolean.TRUE.equals(entity.getCanary()));
    }
}
```

### 5. 业务 Entity / Mapper / Service / Controller

#### Entity (含 tenantId 字段)

```java
package com.example.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String product;
    private BigDecimal amount;
    private Long tenantId;        // 必须有 — TenantMetaObjectHandler 自动填
}
```

#### Mapper

```java
package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.app.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    // 这里的方法签名看不出任何租户相关
    // TenantLineInnerInterceptor 会自动给 SELECT/UPDATE/DELETE 加 WHERE tenant_id = ?
    // TenantMetaObjectHandler 会自动给 INSERT 填 tenant_id
}
```

#### Service

```java
package com.example.app.service;

import com.example.app.entity.Order;
import com.example.app.mapper.OrderMapper;
import com.richie.component.tenant.context.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderMapper mapper;

    public OrderService(OrderMapper mapper) {
        this.mapper = mapper;
    }

    public Order create(Order order) {
        // 不要手动设 order.setTenantId(...) — TenantMetaObjectHandler 自动填
        mapper.insert(order);
        return order;
    }

    public List<Order> list() {
        Long tenantId = TenantContext.requireTenantId();
        return mapper.selectList(null);   // SQL 自动追加 WHERE tenant_id = ?
    }
}
```

#### Controller

```java
package com.example.app.web;

import com.example.app.entity.Order;
import com.example.app.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<Order> list() {
        return service.list();
    }

    @PostMapping
    public Order create(@RequestBody Order order) {
        return service.create(order);
    }
}
```

### 6. application.yml — 4 种模式各一个

#### COLUMN 模式(最简单,推荐起步)

```yaml
spring:
  application:
    name: app
  datasource:
    url: jdbc:postgresql://localhost:5432/app
    username: app
    password: ${DB_PWD}
  datasource:
    driver-class-name: org.postgresql.Driver

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 开发环境看 SQL

multi-tenancy:
  enabled: true
  mode: COLUMN
  tenant-id-column: tenant_id
  enforce-auth-tenant: true
  ignore-tables:
    - sys_tenant
    - sys_dict
  # force-thread-local: false   # ScopedValueHolder(Java 25),生产无需改
  microservice: false            # 单体应用关闭通信框架检测
```

#### TABLE 模式

```yaml
multi-tenancy:
  enabled: true
  mode: TABLE
  table-name-suffix: "_${tenant}"     # orders → orders_1001
  ignore-tables:
    - sys_tenant
    - sys_dict
  microservice: false
```

> DDL 需要为每个租户建表:`CREATE TABLE orders_1001 (LIKE orders INCLUDING ALL);`

#### SCHEMA 模式(仅 PG/Oracle)

```yaml
multi-tenancy:
  enabled: true
  mode: SCHEMA
  schema-prefix: "tenant_"             # tenantId=1001 → schema "tenant_1001"
  schema-auto-create: true             # 首次访问自动 CREATE SCHEMA + 复制表结构
  ignore-tables:
    - sys_tenant
  microservice: false
```

#### DATABASE 模式(最强物理隔离)

```yaml
spring:
  datasource:
    # 主数据源用于 sys_tenant / 平台表
    url: jdbc:postgresql://platform-db/platform
    username: platform
    password: ${PLATFORM_PWD}

multi-tenancy:
  enabled: true
  mode: DATABASE
  datasource:
    shared:                            # 共享数据源(可选,用于 sys_tenant 表)
      url: jdbc:postgresql://platform-db/platform
      username: platform
      password: ${PLATFORM_PWD}
    tenants:
      "1001":                          # 租户 1001 独立库
        url: jdbc:postgresql://prod-db/tenant_1001
        username: tenant_1001
        password: ${TENANT_1001_PWD}
      "1002":
        url: jdbc:postgresql://prod-db/tenant_1002
        username: tenant_1002
        password: ${TENANT_1002_PWD}
  microservice: false
```

> 注意:`@SpringBootApplication` 默认只接 1 个 DataSource Bean。DATABASE 模式需要在主类**排除** Spring Boot 的 DataSource 自动配置,然后由 `atlas-richie-component-tenant` 的 `TenantAutoConfiguration` 接管。

```java
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
```

### 7. 启动 & 验证

```bash
# 1. 准备 PG(用 docker)
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_DB=app -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app \
  postgres:18

# 2. 初始化 DDL
psql -h localhost -U app -d app -f init.sql

# 3. 启动
mvn spring-boot:run

# 4. 模拟请求(用 X-Tenant-ID header 模拟 JWT,见 TenantIdentityFilter)
curl -H "X-Tenant-ID: 1001" http://localhost:8080/orders
# 返回: [{"id":1,"product":"X","amount":100,"tenantId":1001}]

curl -H "X-Tenant-ID: 1002" http://localhost:8080/orders
# 返回: []  (跨租户隔离生效)
```

### 8. 常见接入问题

| 问题 | 解决 |
|------|------|
| 启动时 `No qualifying bean of type 'TenantInfoProvider'` | 没自己实现,只用了默认 NoOp。**必须**实现 `CachedTenantInfoProvider` 这种。 |
| `java.lang.IllegalStateException: TenantContext not initialized` | 主类没扫到 `TenantAutoConfiguration`,加 `@SpringBootApplication` 默认就扫,看是否被 `@ComponentScan` 排除了 |
| 数据库模式 `Failed to determine a suitable driver class` | 主类没排除 `DataSourceAutoConfiguration`(DATABASE 模式必需) |
| COLUMN 模式 SQL 报 `column "tenant_id" does not exist` | 业务表确实没建 `tenant_id` 列,要么 DDL 补上,要么把表加到 `ignore-tables` |
| @Async 内 `TenantContext.getTenantId()` 返回 null | 用了 `Executors.newFixedThreadPool()` 而非 Spring `@Bean ThreadPoolTaskExecutor`,前者不会被 `TenantTaskDecoratorBeanPostProcessor` 处理 |
