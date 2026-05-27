# atlas-richie-component-dao 多租户实现分析报告

> **日期**: 2026-05-26  
> **范围**: `atlas-richie-component/atlas-richie-component-dao/src/main/java/com/richie/component/dao/tenant/`  
> **代码作者**: yuyue  
> **目的**: 评估当前实现质量，识别架构问题，提出重构方案  

---

## 目录

- [一、现状概览](#一现状概览)
- [二、架构核心问题](#二架构核心问题)
- [三、逐项问题清单](#三逐项问题清单)
- [四、业界标准对比](#四业界标准对比)
- [五、重构方案](#五重构方案)
- [六、附录：文件清单](#六附录文件清单)

---

## 一、现状概览

### 1.1 代码规模

`tenant/` 目录共 21 个文件，约 1000+ 行代码：

```
tenant/
├── MybatisPlusTenantAutoConfiguration.java    # 主自动配置（299 行，最大的类）
├── TenantProperties.java                       # 配置属性
├── TenantConstant.java                         # 常量
├── TenantCodeContextHolder.java                # ThreadLocal 上下文
├── TenantDataSourcePropertyMapCache.java       # 两级缓存（128 行）
├── TenantOperator.java                         # 租户操作接口 + 实现
├── DatasourceOperator.java                     # 数据源操作接口 + 实现
├── DataSourceInterceptor.java                  # HTTP 拦截器
├── DataSourceListener.java                     # Redisson 消息监听器
├── DynamicDataSourceCustomAnnotationInterceptor.java  # @DS 注解拦截器
├── WebMvcConfigurerDataSourceInterceptor.java  # 拦截器注册
├── ModifyMybatisPlusInterceptor.java           # 注入租户插件
├── AddTenantCode.java                          # DTO
├── domain/TenantDatasource.java                # 实体
├── service/TenantDatasourceService.java        # 服务接口
├── service/impl/TenantDatasourceServiceImpl.java
├── mapper/TenantDatasourceMapper.java
├── controller/TenantDatasourceController.java  # REST 端点
├── annotation/CommonDataSource.java            # 自定义注解
└── aspect/CommonDataSourceAspect.java          # AOP 切面
```

### 1.2 技术栈依赖

| 依赖 | 用途 | scope |
|------|------|-------|
| `mybatis-plus-spring-boot4-starter` | ORM 框架 | compile |
| `dynamic-datasource-spring-boot3-starter` | 动态数据源切换 | **provided** |
| `redisson-spring-boot-starter` | 分布式锁/消息 | **compile** |
| `spring-boot-starter-data-redis` | Redis 连接 | compile |
| `mybatis-plus-jsqlparser` | SQL 解析（租户拦截器） | compile |

---

## 二、架构核心问题

### 2.1 致命缺陷：同时应用两种互斥的隔离策略

```
                        HTTP Request
                             │
                             ▼
                ┌─────────────────────────┐
                │  DataSourceInterceptor   │
                │  解析 tenant → Context   │
                └───────────┬─────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                                     ▼
┌──────────────────────┐          ┌──────────────────────┐
│  策略 A: 物理隔离      │          │  策略 B: 逻辑隔离      │
│  dynamic-datasource   │          │  TenantLineInner      │
│  切换到租户独立 DB     │          │  Interceptor          │
│                       │          │  SQL 注入              │
│                       │          │  WHERE tenant_code=?  │
└──────────────────────┘          └──────────────────────┘
          │                                     │
          └─────────────────┬───────────────────┘
                            ▼
                    租户数据库（已隔离）
                            │
                    又加了 tenant_code 过滤
```

**分析**：

- **物理隔离**（数据库级）：通过 `dynamic-datasource` 根据租户 ID 将请求路由到**不同的数据库**。不同租户的数据已经在物理层面分离。

- **逻辑隔离**（行级）：通过 `TenantLineInnerInterceptor` 在每条 SQL 中追加 `AND tenant_code = ?`，用于在**共享数据库**中区分不同租户的行。

- **问题**：如果已经路由到独立数据库，租户列过滤是多余的；如果共享数据库用行级过滤，动态数据源的路由复杂性是不需要的。同时启用两者说明代码是分阶段"贴上去"的，没有做过架构决策。

### 2.2 两个假设场景

| 场景 | 实际使用的策略 | 多余的策略 | 影响 |
|------|---------------|-----------|------|
| 每个租户独立 DB | 策略 A (`dynamic-datasource`) | 策略 B (`WHERE tenant_code=?`) | 无意义开销 + 易出 bug |
| 共享 DB + 行级隔离 | 策略 B (`TenantLineInnerInterceptor`) | 策略 A (`dynamic-datasource`) | 引入不必要的 Redisson/dynamic-datasource 依赖 |
| 混合（部分独立 + 部分共享） | 需要两者 | — | 正常，但当前实现未区分 |

**结论**：需要明确设计意图。如果是混合场景，应显式声明策略而非"全开"。

---

## 三、逐项问题清单

### 🔴 致命问题

#### #1: 同时应用两种互斥的隔离策略

- **位置**: `MybatisPlusTenantAutoConfiguration.java`
- **表现**: 同时注册了 `jdbcDynamicDataSourceProvider`（物理隔离）和 `TenantLineInnerInterceptor`（行级过滤）
- **根本原因**: 缺少策略选择机制，代码同时打开了两种模式
- **影响**: 逻辑矛盾，性能浪费，难以排查数据问题

#### #2: 类级别 `@MapperScan` 导致条件化失效

- **位置**: `MybatisPlusTenantAutoConfiguration.java:65-66`
- **代码**:
  ```java
  @MapperScan("com.richie.component.dao.tenant.mapper")
  @ConditionalOnProperty(prefix = ..., name = "enable-tenant", havingValue = "true")
  public class MybatisPlusTenantAutoConfiguration extends ...
  ```
- **问题**: `@MapperScan` 是**类级别注解**，在类加载时就执行；`@ConditionalOnProperty` 只能阻止**bean 注册**，但 MapperScan 已经执行完毕
- **影响**: 即使 `enable-tenant=false`，`TenantDatasourceMapper` 仍被 MyBatis 扫描注册

#### #3: 继承 `MybatisConfiguration` 的架构异味

- **位置**: `MybatisPlusTenantAutoConfiguration.java:67`
- **代码**:
  ```java
  public class MybatisPlusTenantAutoConfiguration extends MybatisConfiguration
  ```
- **问题**: `MybatisConfiguration` 是 MyBatis 核心配置类，承担 mapper 注册、类型处理器、插件链管理等职责。让一个业务级别的"租户自动配置"去**继承**它，语义完全错误。没有任何一个主流开源项目这么做。
- **影响**: 破坏 Spring Bean 的类型层次结构，可能导致意外的 MyBatis 行为

#### #4: SQL 注入风险

- **位置**: `MybatisPlusTenantAutoConfiguration.java:100-101`
- **代码**:
  ```java
  String sql = "select * from %s where %s='%s'";
  rs = statement.executeQuery(String.format(sql, tenantProperties.getTenantTableName(), TenantConstant.SERVICE_NAME_COLUMN, appName));
  ```
- **问题**: 
  - `tenantTableName` 来自配置 `spring.datasource.dynamic.tenant.tenant-table-name`，默认值 `tenant_datasource`，理论上可被篡改
  - 未使用 `PreparedStatement` 参数化查询
  - 虽然实际 risk 低（配置值通常是内部控制的），但不符合安全编码规范
- **修复**: 使用 `PreparedStatement` 或白名单校验表名

---

### 🟠 严重问题

#### #5: 裸线程创建

- **位置**: `DataSourceListener.java:34`
- **代码**:
  ```java
  public void listen() {
      new Thread(() -> { ... }).start();
  }
  ```
- **问题**: 绕过 Spring 的 `TaskExecutor` 线程池管理；线程无法监控和控制生命周期
- **修复**: 改用 `@Async` + `TaskExecutor` 或 `@EventListener`

#### #6: 空方法体

- **位置**: `DatasourceOperator.java:102-104`
- **代码**:
  ```java
  @Override
  public void refreshDatasource() {
      // 空实现
  }
  ```
- **问题**: 方法是接口定义的一部分，但实现为空。调用者会以为数据源已刷新，实际上什么都没做
- **推测**: 可能是未完成的代码，或者之前的刷新逻辑被移除了但接口未更新

#### #7: 静默错误吞噬 — 找不到租户时 fallback 到 "0"

- **位置**: `DataSourceInterceptor.java:67`
- **代码**:
  ```java
  private String getHeaderTenantCode(HttpServletRequest request) {
      String headerTenantCode = request.getHeader(GlobalConstants.X_TENANT_CODE_TOKEN);
      if (StringUtils.isBlank(headerTenantCode)) {
          log.error("headerTenantCode is null");  // 仅记日志！
      } else {
          return headerTenantCode;
      }
      return "0";  // 静默 fallback！
  }
  ```
- **问题**: 
  - 找不到租户 → 返回 `"0"` 
  - `TenantLineHandler.ignoreTable()` 中判断 `tenantCode == 0` → 跳过隔离
  - 结果：租户配置错误的请求会被当作"无租户"处理，可能造成**数据泄漏或写错数据库**
- **修复**: 严格模式下应直接返回 401/403，或至少区分"故意无租户"和"配置错误"

#### #8: Hack 式修改 MyBatis-Plus 拦截器链

- **位置**: `ModifyMybatisPlusInterceptor.java:29-47`
- **代码**:
  ```java
  @PostConstruct
  public void mybatisPlusInnerInterceptorBeanPostProcessor() {
      mybatisPlusInterceptor.addInnerInterceptor(tenantLineInnerInterceptor);
      List<InnerInterceptor> interceptors = mybatisPlusInterceptor.getInterceptors(); // unmodifiableList
      List<InnerInterceptor> newList = new ArrayList<>();
      // 重建整个 list 确保分页拦截器在最后...
      mybatisPlusInterceptor.setInterceptors(newList);
  }
  ```
- **问题**: 
  - 利用 `@PostConstruct` 时机 hack 内部拦截器顺序
  - 操作的是一个 `Collections.unmodifiableList`
  - MyBatis-Plus 版本升级可能导致顺序机制改变，此代码极易失效
- **修复**: 在创建 `MybatisPlusInterceptor` 时直接按正确顺序添加拦截器，而非事后修改

#### #9: Redisson compile scope 强制依赖

- **位置**: `pom.xml:134-138`
- **代码**:
  ```xml
  <dependency>
      <groupId>org.redisson</groupId>
      <artifactId>redisson-spring-boot-starter</artifactId>
      <scope>compile</scope>  <!-- 即使不用租户也引入 -->
  </dependency>
  ```
- **问题**: 所有使用 `atlas-richie-component-dao` 的应用都被强制加载 Redisson + Redis 连接
- **修复**: 改为 `provided` scope，或移到独立租户模块

---

### 🟡 中等问题

#### #10: 配置前缀侵入 Spring 标准命名空间

- **位置**: `TenantProperties.java:18`
- **代码**:
  ```java
  @ConfigurationProperties(prefix = "spring.datasource.dynamic.tenant")
  ```
- **问题**: 使用了 `spring.datasource.dynamic.*` 前缀，这与 `dynamic-datasource` 框架的官方命名空间重叠
- **修复**: 使用项目统一前缀: `platform.component.dao.tenant`

#### #11: 自动配置类始终被 JVM 加载

- **位置**: `AutoConfiguration.imports`
- **内容**:
  ```
  com.richie.component.dao.config.DaoAutoConfiguration
  com.richie.component.dao.tenant.MybatisPlusTenantAutoConfiguration  ← 总是加载
  com.richie.component.dao.snowflake.IdBuilderAutoConfiguration
  ```
- **问题**: 即使 `enable-tenant=false`，JVM 仍会加载这个类（虽然 bean 不创建）。最坏情况下，如果类中有静态初始化块或类级别的 `@MapperScan`（#2），就会产生副作用
- **修复**: 将租户配置移出 `AutoConfiguration.imports`，放到独立模块中

#### #12: DataSourceInterceptor 全局拦截所有请求

- **位置**: `WebMvcConfigurerDataSourceInterceptor.java:26`
- **代码**:
  ```java
  public void addInterceptors(InterceptorRegistry interceptorRegistry) {
      interceptorRegistry.addInterceptor(new DataSourceInterceptor(tenantDataSourcePropertyMapCache));
  }
  ```
- **问题**: 没有添加路径白名单（如 `/health`, `/actuator/**`, `/swagger-ui/**`），所有 HTTP 请求都会经历 token 解析 + 数据源切换
- **影响**: 健康检查、API 文档等非业务请求也会消耗租户上下文操作的开销

#### #13: TenantCodeContextHolder 使用 `protected` 访问修饰

- **位置**: `TenantCodeContextHolder.java:16-26`
- **代码**:
  ```java
  protected static void setTenantCode(Long tenantCode) { CTX.set(tenantCode); }
  public static Long getTenantCode() { return CTX.get(); }
  protected static void clearContext() { CTX.remove(); }
  ```
- **问题**: `setTenantCode` 和 `clearContext` 用了 `protected` — 意味着**同包的 `DataSourceInterceptor`** 才可以调用，这是一种奇怪的封装设计。如果剥离到独立模块，`protected` 就无法工作了
- **修复**: 统一使用 `public` 或设计为内部 API（module-info / internal 包）

---

## 四、业界标准对比

### 4.1 多租户四种标准模型

（参考 Hibernate 官方多租户指南 和 [MyBatis-Plus 文档](https://github.com/baomidou/mybatis-plus-doc/blob/master/src/content/docs/plugins/tenant.md)）

| 模型 | 隔离级别 | 成本 | 复杂度 | Spring Boot 实现方式 | 适用场景 |
|------|---------|------|--------|---------------------|---------|
| **① 独立数据库** (Database-per-Tenant) | 最高 | 高（N 个 DB） | 中 | `dynamic-datasource` 切换数据源；每个租户独立 MySQL/PostgreSQL 实例 | 金融、医疗合规，大客户 |
| **② 独立 Schema** (Schema-per-Tenant) | 中高 | 中（N 个 Schema） | 中 | PostgreSQL `search_path` / 多 Schema | Oracle/PostgreSQL 生态 |
| **③ 共享数据库+租户字段** (Shared DB+Discriminator) | 中 | 低（共享 DB） | 低 | MyBatis-Plus `TenantLineInnerInterceptor` 自动注入 `WHERE tenant_id = ?` | SaaS 中小客户（推荐） |
| **④ 混合模式** (Hybrid) | 混合 | 中 | 高 | 策略模式组合 ①+③ | 复杂企业场景 |

### 4.2 模型③：TenantLineInnerInterceptor 工作原理

**SQL 修改流程：**

```
用户 SQL: SELECT * FROM orders WHERE status = 1
                    ↓
         TenantLineInnerInterceptor
                    ↓
  1. 从 TenantLineHandler 获取 tenantId（ThreadLocal）
  2. JSQLParser 解析 SQL AST
  3. 注入 WHERE 条件
                    ↓
修改后 SQL: SELECT * FROM orders 
            WHERE status = 1 AND tenant_id = 100
```

**`TenantLineHandler` 接口**（MyBatis-Plus 官方定义）：

```java
public interface TenantLineHandler {
    Expression getTenantId();           // 获取租户 ID 表达式
    String getTenantIdColumn();         // 默认 "tenant_id"（可覆写）
    boolean ignoreTable(String tableName); // 忽略的表返回 true，跳过隔离
}
```

**SQL 修改矩阵：**

| 操作 | 原 SQL | 修改后 |
|------|--------|--------|
| SELECT | `SELECT * FROM orders` | `SELECT * FROM orders WHERE tenant_id = 100` |
| INSERT | `INSERT INTO orders (...) VALUES (...)` | `INSERT INTO orders (..., tenant_id) VALUES (..., 100)` |
| UPDATE | `UPDATE orders SET ... WHERE id = 1` | `UPDATE orders SET ... WHERE id = 1 AND tenant_id = 100` |
| DELETE | `DELETE FROM orders WHERE id = 1` | `DELETE FROM orders WHERE id = 1 AND tenant_id = 100` |

> ⚠️ **注意**：INSERT 时如果需要写入 `tenant_id` 列，需要 handler 的 `getTenantIdColumn()` 返回字段名且实体类包含该字段；否则仅追加 WHERE 条件。

### 4.3 模型①：dynamic-datasource 租户数据源路由

**核心机制：**

```java
// DynamicRoutingDataSource 内部维护数据源 map
Map<String, DataSource> dataSourceGroupMap = {
    "master"     → DataSource_Master,    // 主库
    "tenant_100" → DataSource_TenantA,   // 租户A的数据库
    "tenant_200" → DataSource_TenantB,   // 租户B的数据库
};

// 运行时创建新租户数据源
DynamicRoutingDataSource ds = (DynamicRoutingDataSource) dataSource;
DataSourceProperty property = new DataSourceProperty();
property.setUrl("jdbc:mysql://host:port/tenant_300");
DataSource newDs = DefaultDataSourceCreator.createDataSource(property);
ds.addDataSource("tenant_300", newDs);
```

**使用 `@DS` 注解切换：**

```java
@DS("tenant_100")  // 路由到租户 100 的数据库
public List<Order> getOrders() {
    return orderMapper.selectList();  // SQL 在 tenant_100 数据库执行
}
```

### 4.4 业界参考：RuoYi-Vue-Plus 的租户实现

RuoYi-Vue-Plus（GitHub 10k+ stars）是当前最成熟的 Spring Boot + MyBatis-Plus 多租户参考实现：

```java
// 1. 模块独立：ruoyi-common-tenant 作为可选模块
ruoyi-common-tenant/
├── TenantProperties.java       # @ConfigurationProperties(prefix = "tenant")
├── PlusTenantLineHandler.java  # 实现 TenantLineHandler
├── TenantConfig.java           # @ConditionalOnProperty("tenant.enable")
└── TenantHelper.java           # ThreadLocal 上下文

// 2. 正确的条件化：只在 tenant.enable=true 时生效
@AutoConfiguration(after = {RedisConfig.class})
@ConditionalOnProperty(value = "tenant.enable", havingValue = "true")
public class TenantConfig { ... }

// 3. 安全地处理无租户场景
@Override
public Expression getTenantId() {
    String tenantId = TenantHelper.getTenantId();
    if (StringUtils.isBlank(tenantId)) {
        return new NullValue();  // ← 返回 NULL → 查不出任何数据，不会泄露
    }
    return new StringValue(tenantId);
}
```

| 对比维度 | RuoYi-Vue-Plus | 当前实现 |
|---------|---------------|---------|
| 模块位置 | 独立可选模块 `ruoyi-common-tenant` | 嵌在 DAO 的 `tenant/` 子包，无法移除 |
| 启用方式 | `@ConditionalOnProperty("tenant.enable")` ✅ | 同注解，但 `@MapperScan` 不受控制 ❌ |
| 租户策略 | 单一策略：行级过滤 | 两种互斥策略盲目叠加 ❌ |
| 无租户处理 | 返回 `NullValue`（安全） | 静默回退 `"0"`（不安全）❌ |
| 配置前缀 | `tenant.*` ✅ | `spring.datasource.dynamic.tenant`（侵入第三方命名空间）❌ |
| 线程安全 | ThreadLocal ✅ | TransmittableThreadLocal ✅ |

### 4.5 关键原则总结

| 原则 | 说明 | 当前是否满足？ |
|------|------|---------------|
| **策略互斥** | 不在同一请求中同时启用 Database-per-Tenant 和 Discriminator | ❌ 两者同时启用 |
| **显式声明** | 通过 `strategy = DISCRIMINATOR | DATABASE | HYBRID` 显式选择 | ❌ 无策略概念 |
| **条件化隔离** | 所有租户 bean（含 MapperScan）完全受 `@ConditionalOnProperty` 控制 | ❌ MapperScan 在类级别 |
| **独立模块** | 租户作为可选模块，不污染基础 DAO | ❌ 耦合在 DAO 内部 |
| **安全 fallback** | 无租户时返回 `NullValue` 或抛出明确异常 | ❌ 静默 fallback "0" |
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  Tenant A    │   │  Tenant B    │   │  Tenant C    │
│  Database    │   │  Database    │   │  Database    │
└──────────────┘   └──────────────┘   └──────────────┘
```

- 每个租户拥有独立数据库实例
- 通过 `dynamic-datasource` 动态切换数据源
- **不需要** `TenantLineInnerInterceptor`（物理已隔离）
- 最高隔离度，最高成本

#### 模型 ③：Shared DB + Discriminator Column（共享数据库 + 租户字段）

```
┌──────────────────────────────────────────────┐
│         Single Database                       │
│  ┌─────────────────────────────────────────┐ │
│  │  Table: users                           │ │
│  │  id | name  | tenant_id                 │ │
│  │  1  | Alice | 100                       │ │
│  │  2  | Bob   | 200                       │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

- 所有租户共享数据库和表
- `TenantLineInnerInterceptor` 自动在 SQL 中注入 `WHERE tenant_id = ?`
- **不需要** `dynamic-datasource`（只有一个数据源）
- 最低隔离度，最低成本

#### 模型 ④：Hybrid（混合模式）

```
┌──────────────────────────────────────────────────┐
│  全局数据 (sys_dict, sys_config 等)               │
│  → 不启用租户过滤 (ignoreTable)                   │
├──────────────────────────────────────────────────┤
│  共享业务数据 (orders, products 等)               │
│  → 行级过滤 (TenantLineInnerInterceptor)          │
├──────────────────────────────────────────────────┤
│  大客户独立数据库 (Enterprise Tier)               │
│  → dynamic-datasource 路由到独立 DB              │
│  → 同时不需要行级过滤（物理隔离）                  │
└──────────────────────────────────────────────────┘
```

**关键原则**：Hybrid 模式下，行级过滤和数据库路由应**按表/租户粒度显式区分**，而非在同一个请求流程中同时启用两种策略。

### 4.3 `TenantLineInnerInterceptor` 工作原理

#### SQL 注入流程

```
原始 SQL:                          拦截后 SQL:
SELECT * FROM orders               SELECT * FROM orders
WHERE status = 1                   WHERE status = 1
                                   AND tenant_id = 100

INSERT INTO orders                 INSERT INTO orders
(name, price) VALUES               (name, price, tenant_id)
('item', 10)                       VALUES ('item', 10, 100)
```

#### `TenantLineHandler` 接口（MyBatis-Plus 官方）

```java
public interface TenantLineHandler {
    /**
     * 获取租户 ID 值（返回 NullValue 表示无租户，防止数据泄漏）
     */
    Expression getTenantId();

    /**
     * 获取租户字段名（默认: tenant_id）
     */
    default String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 根据表名判断是否忽略租户过滤
     */
    default boolean ignoreTable(String tableName) {
        return false;
    }
}
```

**最佳实践**：找不到租户时应返回 `new NullValue()`，**不返回**默认值，以防止数据泄漏。

#### RuoYi-Vue-Plus 参考实现

> 来源: [dromara/RuoYi-Vue-Plus](https://github.com/dromara/RuoYi-Vue-Plus/tree/5.X) — 国内最成熟的开源多租户实现之一

```java
@Slf4j
@AllArgsConstructor
public class PlusTenantLineHandler implements TenantLineHandler {

    @Override
    public Expression getTenantId() {
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            log.error("无法获取有效的租户id -> Null");
            return new NullValue();  // ← 关键: 返回 NULL 而非默认值
        }
        return new StringValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        List<String> tables = ListUtil.toList("gen_table", "gen_table_column");
        tables.addAll(tenantProperties.getExcludes());
        return StringUtils.equalsAnyIgnoreCase(tableName, tables.toArray(new String[0]));
    }
}
```

**配置方式**：
```yaml
# Disabled (single-tenant mode)
tenant:
  enable: false

# Enabled (multi-tenant mode)
tenant:
  enable: true
  excludes:
    - sys_user
    - sys_role
```

**条件化自动配置**：
```java
@AutoConfiguration
@ConditionalOnProperty(value = "tenant.enable", havingValue = "true")
public class TenantConfig {
    @Bean
    public TenantLineInnerInterceptor tenantLineInnerInterceptor() {
        return new TenantLineInnerInterceptor(new PlusTenantLineHandler(tenantProperties));
    }
}
```

**细粒度控制**（`@InterceptorIgnore`）：
```java
// 整个 Mapper 忽略租户过滤
@InterceptorIgnore(tenantLine = "true")
public interface SystemDictMapper extends BaseMapper<SysDict> {}

// 单个方法忽略租户过滤
public interface OrderMapper extends BaseMapper<Order> {
    @InterceptorIgnore(tenantLine = "true")
    List<Order> selectAllOrders();  // Admin 查看全部订单
}
```

### 4.4 `dynamic-datasource` 工作原理

#### 核心架构

```
┌──────────────────────────────────────────────────┐
│          DynamicRoutingDataSource                │
│  ┌────────────────────────────────────────────┐ │
│  │  Map<String, DataSource> dataSourceMap     │ │
│  │                                            │ │
│  │  "tenant_100"  → DataSource_A             │ │
│  │  "tenant_200"  → DataSource_B             │ │
│  │  "master"      → DataSource_Master        │ │
│  └────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

#### 动态数据源创建

```java
// 运行时动态添加租户数据源
DynamicRoutingDataSource ds = (DynamicRoutingDataSource) dataSource;

DataSourceProperty property = new DataSourceProperty();
property.setUrl(String.format("jdbc:mysql://%s:%s/%s", host, port, dbName));
property.setUsername(username);
property.setPassword(password);

DataSource newDs = defaultDataSourceCreator.createDataSource(property);
ds.addDataSource("tenant_" + tenantCode, newDs);
```

#### @DS 注解用法

```java
@DS("tenant_100")  // 类级别: 所有方法使用 tenant_100 数据源
public class TenantOrderService {

    @DS("master")  // 方法级别: 覆盖类级别注解
    public List<Order> getOrdersFromMaster() {
        return orderMapper.selectList();
    }
}
```

### 4.5 当前实现 vs 业界标准 — 差距对照表

| 维度 | 业界标准 (RuoYi-Vue-Plus) | 当前实现 (atlas-richie-dao) | 差距 |
|------|--------------------------|---------------------------|------|
| **策略选择** | 单一职责，仅用 Discriminator | 同时启用 Discriminator + Database routing | 🔴 致命 |
| **条件化** | `@ConditionalOnProperty("tenant.enable")` | `@ConditionalOnProperty` + 类级别 `@MapperScan` 绕过条件 | 🔴 致命 |
| **无租户时处理** | 返回 `NullValue()` 防泄漏 | 静默 fallback 到 `"0"` | 🔴 致命 |
| **配置前缀** | `tenant.*` | `spring.datasource.dynamic.tenant` (侵入框架命名空间) | 🟠 严重 |
| **模块设计** | 独立 `common-tenant` 模块 | 耦合在 DAO 模块内 | 🟡 中等 |
| **细粒度控制** | `@InterceptorIgnore` 注解 | `@CommonDataSource` AOP (仅数据源切换) | 🟡 中等 |
| **线程管理** | Spring `@Async` / `TaskExecutor` | `new Thread()` 裸线程 | 🟠 严重 |

### 4.6 关键设计原则

| 原则 | 说明 |
|------|------|
| **策略互斥（同请求）** | 不应在同一请求流程中同时启用 Database-per-Tenant 和 Discriminator Column |
| **策略可混用（跨表/跨租户）** | Hybrid 模式下，行级过滤和数据库路由应按表/租户粒度显式区分 |
| **显式声明** | 通过 `platform.dao.tenant.strategy = DATABASE \| DISCRIMINATOR \| HYBRID` 显式选择 |
| **策略模式** | 不同策略对应不同的 Bean 注册集，互不干扰 |
| **真·条件化** | 所有租户相关 bean（包括 MapperScan）必须完全受 `@ConditionalOnProperty` 控制 |
| **独立模块** | 租户功能应作为可选模块（`atlas-richie-component-dao-tenant`），不污染基础 DAO |
| **零成本关闭** | `tenant.enable=false` 时，零性能开销、零依赖污染 |
| **失败安全** | 无租户时抛异常或返回 `NullValue()`，不静默 fallback |

---

## 五、重构方案

### 5.1 模块拆分

```
当前结构                           重构后结构
─────────────────────────          ───────────────────────────────
atlas-richie-component-dao/       atlas-richie-component-dao/
├── config/  ← 纯 DAO              ├── config/  ← 不变
├── interceptor/                   ├── interceptor/
├── handler/                       ├── handler/
├── snowflake/                     ├── snowflake/
├── spy/                           ├── spy/
└── tenant/  ← 21 files            └── pom.xml  ← 移除 Redisson 等依赖

                                   atlas-richie-component-dao-tenant/  (NEW)
                                   ├── pom.xml  ← 依赖 dao + dynamic-datasource
                                   ├── TenantAutoConfiguration.java
                                   ├── TenantProperties.java
                                   ├── TenantContextHolder.java
                                   ├── strategy/
                                   │   ├── TenantIsolationStrategy.java (接口)
                                   │   ├── DiscriminatorStrategy.java
                                   │   ├── DatabasePerTenantStrategy.java
                                   │   └── HybridStrategy.java
                                   ├── DataSourceInterceptor.java
                                   ├── TenantDataSourcePropertyMapCache.java
                                   ├── TenantOperator.java
                                   ├── DatasourceOperator.java
                                   ├── controller/TenantDatasourceController.java
                                   └── ...
```

### 5.2 策略模式设计

```java
// 策略枚举
public enum TenantIsolationType {
    DISCRIMINATOR,    // 共享数据库 + 行级过滤
    DATABASE,         // 独立数据库
    HYBRID            // 混合模式
}

// 策略接口
public interface TenantIsolationStrategy {
    void registerInterceptors(MybatisPlusInterceptor interceptor);
    String resolveDataSource(Long tenantCode);
}

// 配置属性
@ConfigurationProperties(prefix = "platform.component.dao.tenant")
public class TenantProperties {
    private boolean enabled = false;                              // 总开关
    private TenantIsolationType strategy = TenantIsolationType.DISCRIMINATOR; // 策略
    private Set<String> ignoreTables = Collections.emptySet();    // 忽略的表
    private String tenantIdColumn = "tenant_code";                // 租户列名
    // Database-per-tenant 配置...
    private String tenantTableName = "tenant_datasource";
    // ...
}
```

### 5.3 关键修复清单

| # | 问题 | 修复方式 |
|---|------|---------|
| 1 | 两种策略同时启用 | 策略模式，`TenantIsolationType` 枚举互斥选择 |
| 2 | `@MapperScan` 类级别 | 移到 `@Conditional` bean 方法内或移除（用 `@Mapper` 注解在接口上） |
| 3 | `extends MybatisConfiguration` | 移除继承，改为普通 `@Configuration` |
| 4 | SQL 注入 | 使用 `PreparedStatement` |
| 5 | 裸线程 | 改用 `@Async` 或 `ThreadPoolTaskExecutor` |
| 6 | `refreshDatasource()` 空实现 | 要么实现，要么从接口移除 |
| 7 | 静默 fallback `"0"` | 抛异常或根据 strict mode 配置决定行为 |
| 8 | Hack 式拦截器顺序 | 在创建 `MybatisPlusInterceptor` 时按正确顺序注册 |
| 9 | Redisson compile scope | 移入 dao-tenant 模块 |
| 10 | 配置前缀 `spring.datasource` | 改为 `platform.component.dao.tenant` |
| 11 | AutoConfiguration.imports 始终加载 | 移除，放在独立模块的 `imports` 中 |
| 12 | 全局拦截所有请求 | 配置路径白名单 |

### 5.4 迁移兼容性

- 保留旧的 `TenantProperties` 前缀（`spring.datasource.dynamic.tenant`）作为 deprecated，新前缀 `platform.component.dao.tenant` 优先
- 老项目只需将依赖从 `atlas-richie-component-dao` 改为 `atlas-richie-component-dao-tenant` 即可无缝切换

---

## 六、附录：文件清单

### 6.1 当前 tenant/ 目录完整文件列表

| 文件 | 大小 | 说明 |
|------|------|------|
| `MybatisPlusTenantAutoConfiguration.java` | 299 行 | 主配置，最大的类 |
| `TenantDataSourcePropertyMapCache.java` | 128 行 | 两级缓存 |
| `TenantOperator.java` | 127 行 | 租户操作（接口 + 实现在一起） |
| `DatasourceOperator.java` | 107 行 | 数据源操作（接口 + 实现在一起） |
| `DataSourceInterceptor.java` | 70 行 | HTTP 拦截器 |
| `DynamicDataSourceCustomAnnotationInterceptor.java` | 68 行 | @DS 注解拦截器 |
| `TenantConstant.java` | 62 行 | 常量 |
| `TenantProperties.java` | 50 行 | 配置属性 |
| `ModifyMybatisPlusInterceptor.java` | 49 行 | 拦截器链修改 |
| `DataSourceListener.java` | 44 行 | Redisson 消息监听 |
| `CommonDataSourceAspect.java` | 44 行 | AOP 切面 |
| `WebMvcConfigurerDataSourceInterceptor.java` | 31 行 | 拦截器注册 |
| `TenantCodeContextHolder.java` | 28 行 | ThreadLocal 上下文 |
| `CommonDataSource.java` | 17 行 | 自定义注解 |
| `TenantDatasource.java` | - | 实体类 |
| `TenantDatasourceController.java` | - | REST 端点 |
| `TenantDatasourceService.java` | - | 服务接口 |
| `TenantDatasourceServiceImpl.java` | - | 服务实现 |
| `TenantDatasourceMapper.java` | - | MyBatis Mapper |
| `AddTenantCode.java` | - | DTO |

### 6.2 涉及的非 tenant 文件

| 文件 | 关联 |
|------|------|
| `config/DaoConstant.java` | 定义了 `DAO_PREFIX` 和 `DAO_ENABLE_TENANT_PREFIX` |
| `config/DaoAutoConfiguration.java` | `@ComponentScan("com.richie.component.dao")` 会扫描到 tenant 包 |
| `AutoConfiguration.imports` | 直接注册了 `MybatisPlusTenantAutoConfiguration` |

---

> **总结**: 当前实现的核心问题是**架构层面缺乏明确的策略选择**，导致物理隔离和逻辑隔离被同时应用。代码质量方面存在 SQL 注入风险、裸线程创建、空方法体等技术债。建议采用**策略模式**重构，并将租户功能**剥离为独立模块**（`atlas-richie-component-dao-tenant`），使基础 DAO 组件保持轻量和纯净。
