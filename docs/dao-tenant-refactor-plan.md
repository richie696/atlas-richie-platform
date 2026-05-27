# 多租户重构实施计划

> **基于**: [dao-tenant-analysis.md](./dao-tenant-analysis.md) 的 13 个问题分析
> **前置分析**: Metis 风险预判 + explore 跨模块依赖扫描
> **版本**: v1.0 | **日期**: 2026-05-26

---

## 0. 执行摘要

### 0.1 核心策略

```
当前状态                              目标状态
───────────                           ───────────
atlas-richie-component-dao/          atlas-richie-component-dao/
├── config/                          ├── config/         ← 保留
├── interceptor/                     ├── interceptor/    ← 保留
├── handler/                         ├── handler/        ← 保留
├── snowflake/                       ├── snowflake/      ← 保留
├── spy/                             ├── spy/            ← 保留
└── tenant/  ← 21 files              └── pom.xml         ← 清理

                                     atlas-richie-component-dao-tenant/  🆕
                                     ├── strategy/       ← 新增策略模式
                                     ├── tenant/         ← 迁移自原 dao
                                     └── config/         ← 独立自动配置
```

### 0.2 依赖关系结论

| 发现 | 结论 |
|------|------|
| **0 个**外部模块 import `com.richie.component.dao.tenant.*` | 迁移零影响，无向后兼容负担 |
| **1 个**外部模块使用 `IdBuilder`（snowflake 包） | `IdBuilder` 留在 DAO 核心，不迁移 |
| **8 个**模块依赖 `atlas-richie-component-dao` | 只需更新 pom 引用 |
| **0 个**配置文件使用旧配置前缀 | 配置迁移无负担 |

### 0.3 风险矩阵

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 策略接口设计不足，后续频繁修改 | 中 | 先做 DISCRIMINATOR，DATABASE 留接口占位 |
| 迁移期间编译中断 | 低 | 按 Phase 分步，每步可编译 |
| TenantContext 跨线程传播丢失 | 中 | 保留 TTL，增加 propagation 测试 |
| 旧配置被升级用户忽视 | 低 | Deprecated + WARN 日志过渡 |

---

## Phase 0: 模块骨架创建

**目标**: 创建 `atlas-richie-component-dao-tenant` 模块并注册到构建系统

### 任务清单

| # | 任务 | 文件 | 方式 |
|---|------|------|------|
| 0.1 | 创建模块目录结构 | `atlas-richie-component/atlas-richie-component-dao-tenant/` | 新建 |
| 0.2 | 编写 `pom.xml` | `atlas-richie-component-dao-tenant/pom.xml` | 新建 |
| 0.3 | 注册模块到组件父 pom | `atlas-richie-component/pom.xml` | 编辑 |
| 0.4 | 注册到版本管理 | `atlas-richie-component-dependencies/pom.xml` | 编辑 |
| 0.5 | 创建 `AutoConfiguration.imports` | `src/main/resources/META-INF/spring/...` | 新建 |
| 0.6 | 创建 Java 包骨架 | `src/main/java/com/richie/component/dao/tenant/` | 新建 |

### pom.xml 模板

```xml
<parent>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-dependencies</artifactId>
    <version>${revision}</version>
    <relativePath>../atlas-richie-component-dependencies/pom.xml</relativePath>
</parent>

<artifactId>atlas-richie-component-dao-tenant</artifactId>
<name>atlas-richie-component-dao-tenant</name>
<description>多租户数据访问增强模块（可选）</description>

<dependencies>
    <!-- 核心 DAO 模块（不包含租户代码，已被清除） -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-dao</artifactId>
    </dependency>

    <!-- dynamic-datasource: 仅 DATABASE 策略需要 -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>dynamic-datasource-spring-boot3-starter</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Redisson: 仅动态数据源/消息通知需要 -->
    <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson-spring-boot-starter</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### 验收标准

- [ ] `mvn compile -pl atlas-richie-component-dao-tenant` 通过
- [ ] 模块在 IDE 中可识别
- [ ] `AutoConfiguration.imports` 文件存在且为空（待 Phase 2 填充）

---

## Phase 1: 代码迁移

**目标**: 将 21 个租户文件从 DAO 模块移动到新模块

### 1.1 迁移文件清单

```
源路径 (atlas-richie-component-dao)                   →  目标路径 (atlas-richie-component-dao-tenant)
────────────────────────────────────────────────────     ────────────────────────────────────────────────
tenant/MybatisPlusTenantAutoConfiguration.java           →  config/TenantAutoConfiguration.java (重命名)
tenant/TenantProperties.java                              →  config/TenantProperties.java
tenant/TenantConstant.java                                →  config/TenantConstant.java
tenant/TenantCodeContextHolder.java                       →  core/TenantCodeContextHolder.java
tenant/TenantDataSourcePropertyMapCache.java              →  core/TenantDataSourcePropertyMapCache.java
tenant/TenantOperator.java                                →  service/TenantOperator.java
tenant/DatasourceOperator.java                            →  service/DatasourceOperator.java
tenant/DataSourceInterceptor.java                         →  interceptor/DataSourceInterceptor.java
tenant/DataSourceListener.java                            →  listener/DataSourceListener.java
tenant/DynamicDataSourceCustomAnnotationInterceptor.java  →  interceptor/DynamicDataSourceCustomAnnotationInterceptor.java
tenant/WebMvcConfigurerDataSourceInterceptor.java         →  interceptor/WebMvcConfigurerDataSourceInterceptor.java
tenant/ModifyMybatisPlusInterceptor.java                  →  interceptor/ModifyMybatisPlusInterceptor.java
tenant/AddTenantCode.java                                 →  model/AddTenantCode.java
tenant/domain/TenantDatasource.java                       →  model/TenantDatasource.java
tenant/service/TenantDatasourceService.java               →  service/TenantDatasourceService.java
tenant/service/impl/TenantDatasourceServiceImpl.java      →  service/impl/TenantDatasourceServiceImpl.java
tenant/mapper/TenantDatasourceMapper.java                 →  mapper/TenantDatasourceMapper.java
tenant/controller/TenantDatasourceController.java         →  controller/TenantDatasourceController.java
tenant/annotation/CommonDataSource.java                   →  annotation/CommonDataSource.java
tenant/aspect/CommonDataSourceAspect.java                 →  aspect/CommonDataSourceAspect.java
```

> **注意**: 迁移过程中**不修改任何 Java 代码内容**，仅做文件移动和包路径调整。业务逻辑修改在 Phase 2/3/4 中完成。

### 1.2 包路径变更

| 旧包路径 | 新包路径 |
|---------|---------|
| `com.richie.component.dao.tenant` | `com.richie.component.dao.tenant.config` |
| `com.richie.component.dao.tenant.annotation` | `com.richie.component.dao.tenant.annotation` |
| `com.richie.component.dao.tenant.aspect` | `com.richie.component.dao.tenant.aspect` |
| `com.richie.component.dao.tenant.domain` | `com.richie.component.dao.tenant.model` |
| `com.richie.component.dao.tenant.service` | `com.richie.component.dao.tenant.service` |
| `com.richie.component.dao.tenant.mapper` | `com.richie.component.dao.tenant.mapper` |
| `com.richie.component.dao.tenant.controller` | `com.richie.component.dao.tenant.controller` |

> **策略**: 保持 `com.richie.component.dao.tenant` 作为根包，避免不必要的外部 import 变更。

### 验收标准

- [ ] 21 个文件全部迁移完成
- [ ] DAO 模块中 `tenant/` 目录为空（或仅剩迁移后删除的空目录）
- [ ] 新模块 `mvn compile` 通过（此时仅做包路径调整，不修改逻辑）
- [ ] DAO 模块 `mvn compile` 通过（移除 tenant 代码后无编译错误）

---

## Phase 2: 核心重构

**目标**: 修复 5 个致命问题 + 引入策略模式

### 2.1 修复 #3: 移除 `extends MybatisConfiguration`

**文件**: `config/TenantAutoConfiguration.java`

```java
// ❌ Before
@Configuration(proxyBeanMethods = false)
public class MybatisPlusTenantAutoConfiguration extends MybatisConfiguration {
    // ...
}

// ✅ After
@AutoConfiguration
@EnableConfigurationProperties(TenantProperties.class)
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class TenantAutoConfiguration {
    // 所有 @Bean 方法移到此处
}
```

### 2.2 修复 #2: `@MapperScan` 移到条件化内部类

```java
@AutoConfiguration
public class TenantAutoConfiguration {

    // 内部类1: Discriminator 策略
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.component.dao.tenant", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "platform.component.dao.tenant", name = "strategy", havingValue = "DISCRIMINATOR", matchIfMissing = true)
    @MapperScan("com.richie.component.dao.tenant.mapper")
    static class DiscriminatorStrategyConfiguration {
        // TenantLineInnerInterceptor bean
        // TenantLineHandler bean
    }

    // 内部类2: Database-per-Tenant 策略
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.component.dao.tenant", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "platform.component.dao.tenant", name = "strategy", havingValue = "DATABASE")
    @MapperScan("com.richie.component.dao.tenant.mapper")
    static class DatabasePerTenantStrategyConfiguration {
        // jdbcDynamicDataSourceProvider bean
        // dynamic-datasource 相关 beans
    }
}
```

### 2.3 修复 #1/#5: 引入策略模式

**新增文件列表**:

| 文件 | 包路径 | 说明 |
|------|--------|------|
| `TenantIsolationStrategy.java` | `strategy/` | 策略接口 |
| `DiscriminatorColumnStrategy.java` | `strategy/` | 共享库+租户字段策略 |
| `DatabasePerTenantStrategy.java` | `strategy/` | 独立数据库策略 |
| `TenantIsolationType.java` | `strategy/` | 策略枚举 |

**策略接口**:

```java
public interface TenantIsolationStrategy {

    /** 策略类型标识 */
    TenantIsolationType getType();

    /** 注册 MyBatis-Plus 拦截器（返回需要注册的 InnerInterceptor 列表） */
    List<InnerInterceptor> createInterceptors(TenantProperties properties);

    /** 是否需要动态数据源路由 */
    boolean requiresDynamicDataSource();

    /** 创建数据源提供者（仅 DATABASE 策略实现） */
    Optional<DynamicDataSourceProvider> createDataSourceProvider();
}
```

**策略枚举**:

```java
public enum TenantIsolationType {
    DISCRIMINATOR,   // 共享数据库 + 租户字段行级过滤
    DATABASE,        // 独立数据库
    HYBRID           // 混合模式（按表/租户粒度区分）
}
```

### 2.4 修复 #4: SQL 注入

**文件**: `config/TenantAutoConfiguration.java` 中的 `jdbcDynamicDataSourceProvider`

```java
// ❌ Before
String sql = "select * from %s where %s='%s'";
rs = statement.executeQuery(String.format(sql, tableName, col, appName));

// ✅ After: 使用 PreparedStatement + 表名白名单
private static final Set<String> ALLOWED_TABLES = Set.of("tenant_datasource");
// 或使用 Properties 中配置的表名，但始终用 PreparedStatement
String sql = "SELECT * FROM " + validatedTableName + " WHERE " + SERVICE_NAME_COLUMN + " = ?";
PreparedStatement ps = connection.prepareStatement(sql);
ps.setString(1, appName);
ResultSet rs = ps.executeQuery();
```

### 2.5 配置属性重构

**文件**: `config/TenantProperties.java`

```java
@Data
@ConfigurationProperties(prefix = "platform.component.dao.tenant")
public class TenantProperties implements Serializable {

    /** 是否启用多租户 */
    private boolean enabled = false;

    /** 多租户隔离策略 */
    private TenantIsolationType strategy = TenantIsolationType.DISCRIMINATOR;

    /** 租户字段名（仅 DISCRIMINATOR/HYBRID 策略） */
    private String tenantIdColumn = "tenant_code";

    /** 忽略租户隔离的表名 */
    private Set<String> ignoreTables = Collections.emptySet();

    /** 严格模式：无租户时抛异常而非静默跳过 */
    private boolean strictMode = false;

    // -- DATABASE 策略专属配置 --
    /** 租户数据源表名 */
    private String tenantTableName = "tenant_datasource";

    /** 数据库连接 URL 模板 */
    private String dbUrlTemplate = "jdbc:mysql://%s?...";

    /** 找不到 master 时使用随机数据源 */
    private boolean useRandomMaster = true;

    /** 新增租户通知 topic */
    private String addTenantTopic = "add_tenant_topic";
}
```

### 验收标准

- [ ] `extends MybatisConfiguration` 已移除
- [ ] `@MapperScan` 在 `@ConditionalOnProperty` 保护的内部类中
- [ ] 策略模式已实现，`DISCRIMINATOR` 和 `DATABASE` 互斥
- [ ] SQL 查询使用 PreparedStatement
- [ ] 配置前缀改为 `platform.component.dao.tenant`
- [ ] `strategy=DISCRIMINATOR` 时仅注册 `TenantLineInnerInterceptor`
- [ ] `strategy=DATABASE` 时仅注册 `dynamic-datasource` 相关 beans
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过（现有测试）

---

## Phase 3: 修复严重问题

### 3.1 修复 #6: 裸线程 → `@Async`

**文件**: `listener/DataSourceListener.java`

```java
// ❌ Before
public void listen() {
    new Thread(() -> { ... }).start();
}

// ✅ After
@Async("tenantTaskExecutor")
public void listen() {
    RTopic topic = redissonClient.getTopic(tenantProperties.getAddTenantTopic());
    topic.addListenerAsync(List.class, (channel, msg) -> {
        tenantOperator.listenAddTenant(msg);
    });
}
```

**附加**: 在 `config/TenantAutoConfiguration.java` 中注册 `TaskExecutor`:

```java
@Bean("tenantTaskExecutor")
@ConditionalOnMissingBean(name = "tenantTaskExecutor")
public Executor tenantTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setThreadNamePrefix("tenant-");
    executor.initialize();
    return executor;
}
```

### 3.2 修复 #7: 空方法体

**策略**: 从接口移除 `refreshDatasource()` 方法，或将实现补全。

**选择**: **移除** — 分析表明该方法在 `DatasourceOperatorImpl` 中为空，且没有外部调用者。如果在接口中保留但未实现，会误导调用方。

```java
// Before
public interface DatasourceOperator {
    Set<String> listDatasource();
    Set<String> addDatasource(DataSourceProperty dataSourceProperty);
    void refreshDatasource();  // ← 移除
}
```

### 3.3 修复 #8: 静默 fallback → 严格模式

**文件**: `interceptor/DataSourceInterceptor.java`

```java
// ❌ Before: 静默回退到 "0"
return "0";

// ✅ After: 根据 strictMode 决定行为
private String getHeaderTenantCode(HttpServletRequest request) {
    String headerTenantCode = request.getHeader(GlobalConstants.X_TENANT_CODE_TOKEN);
    if (StringUtils.isNotBlank(headerTenantCode)) {
        return headerTenantCode;
    }

    if (tenantProperties.isStrictMode()) {
        throw new TenantNotFoundException(
            "无法从请求头 X-Tenant-Code 中获取租户编码。请确保请求携带租户信息，"
            + "或将 platform.component.dao.tenant.strict-mode 设置为 false"
        );
    }

    log.warn("未检测到租户编码，将以无租户模式执行（租户隔离将被跳过）");
    return null;  // 而非 "0"
}
```

**对应的 `TenantLineHandler` 修改**:

```java
@Override
public Expression getTenantId() {
    Long tenantCode = TenantCodeContextHolder.getTenantCode();
    if (tenantCode == null) {
        return new NullValue();  // ← 改为 NullValue：不返回任何数据
    }
    return new LongValue(tenantCode);
}
```

### 3.4 修复 #9: 拦截器链顺序

**文件**: `interceptor/ModifyMybatisPlusInterceptor.java` → **删除整个文件**

**替代方案**: 在 `DaoAutoConfiguration.mybatisPlusInterceptor()` 中按正确顺序注册：

```java
// 创建 MybatisPlusInterceptor 时直接按正确顺序添加拦截器
// 1. TenantLineInnerInterceptor (如果启用)
// 2. 其他拦截器
// 3. PaginationInnerInterceptor (始终最后)
```

> **说明**: 在 Phase 2 的策略模式中，`DiscriminatorStrategy.createInterceptors()` 已经按正确顺序返回拦截器列表，不再需要事后 hack。

### 3.5 修复 #10: 全局拦截 → 路径白名单

**文件**: `interceptor/WebMvcConfigurerDataSourceInterceptor.java`

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new DataSourceInterceptor(tenantDataSourcePropertyMapCache))
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/health",           // 健康检查
                "/actuator/**",      // Spring Actuator
                "/swagger-ui/**",    // API 文档
                "/v3/api-docs/**",   // OpenAPI
                "/error",            // 错误页面
                "/favicon.ico"       // 网站图标
            );
}
```

### 验收标准

- [ ] `DataSourceListener.listen()` 使用 `@Async` + managed `TaskExecutor`
- [ ] `DatasourceOperator.refreshDatasource()` 已从接口移除
- [ ] `strictMode=true` 时无租户请求抛 `TenantNotFoundException`
- [ ] `strictMode=false` 时无租户请求返回 `NullValue()`（不返回数据）
- [ ] `ModifyMybatisPlusInterceptor` 已删除
- [ ] 拦截器链在 Bean 创建时按正确顺序注册
- [ ] 健康检查路径被排除在拦截器之外

---

## Phase 4: 修复中等问题

### 4.1 修复 #11: Redisson scope → provided

**文件**: `atlas-richie-component-dao/pom.xml`

```xml
<!-- ✅ 从 DAO 模块移除 Redisson compile 依赖 -->
<!-- 只在新模块 atlas-richie-component-dao-tenant 中保留 provided scope -->
```

**同时**:

```xml
<!-- DAO 模块 pom.xml: 移除以下依赖 -->
- org.redisson:redisson-spring-boot-starter (从 compile 改为不声明)
- com.baomidou:dynamic-datasource-spring-boot3-starter (保持不变: provided)

<!-- DAO-tenant 模块 pom.xml: 保留 as provided -->
+ org.redisson:redisson-spring-boot-starter (scope: provided)
+ com.baomidou:dynamic-datasource-spring-boot3-starter (scope: provided)
```

### 4.2 修复 #12: 配置前缀 → `platform.component.dao.tenant`

> **已在 Phase 2.5 中完成** — `TenantProperties` 使用新前缀。

向后兼容：在 `TenantAutoConfiguration` 中注册一个 `@Deprecated` 的属性绑定，将旧配置自动映射到新前缀：

```java
@Bean
@ConditionalOnProperty("spring.datasource.dynamic.tenant")
public static EnvironmentPostProcessor legacyConfigMigrationProcessor() {
    return environment -> {
        log.warn("检测到旧配置前缀 'spring.datasource.dynamic.tenant'，"
                + "请迁移到 'platform.component.dao.tenant'。" 
                + "旧前缀将在下个大版本中移除。");
        // 自动映射旧配置值到新配置
    };
}
```

### 4.3 修复 #13: 常量分离

**文件**: `atlas-richie-component-dao/src/main/java/.../config/DaoConstant.java`

```java
// ❌ Before
public class DaoConstant {
    public static final String DAO_PREFIX = "platform.component.dao";
    public static final String DAO_ENABLE_TENANT_PREFIX = "enable-tenant";  // ← 移除
}

// ✅ After
public class DaoConstant {
    public static final String DAO_PREFIX = "platform.component.dao";
    // 不再包含租户相关常量
}
```

**新增**: `atlas-richie-component-dao-tenant/.../config/TenantConstant.java` 中增加:

```java
public class TenantConstant {
    // 现有常量保持不变
    // 新增
    public static final String TENANT_PREFIX = "platform.component.dao.tenant";
    public static final String TENANT_ENABLED_KEY = "enabled";
}
```

### 验收标准

- [ ] DAO 模块 pom.xml 不再声明 Redisson 依赖（非 test scope）
- [ ] 旧配置前缀被检测时输出 WARN 日志
- [ ] `DaoConstant` 不再包含 `DAO_ENABLE_TENANT_PREFIX`
- [ ] `TenantConstant` 包含完整的租户配置常量

---

## Phase 5: DAO 模块清理

**目标**: 彻底清除 DAO 模块的租户耦合

### 任务清单

| # | 任务 | 文件 |
|---|------|------|
| 5.1 | 删除 `tenant/` 目录下所有剩余文件 | `atlas-richie-component-dao/src/main/java/.../tenant/**` |
| 5.2 | 从 `AutoConfiguration.imports` 移除 `MybatisPlusTenantAutoConfiguration` | `META-INF/spring/AutoConfiguration.imports` |
| 5.3 | 移除 `DaoConstant` 中的 `DAO_ENABLE_TENANT_PREFIX` | `config/DaoConstant.java` |
| 5.4 | 检查 `DaoAutoConfiguration` 的 `@ComponentScan` | `config/DaoAutoConfiguration.java` |
| 5.5 | 更新 DAO 模块 `pom.xml`（移除 Redisson 等） | `pom.xml` |
| 5.6 | 移除 `@ConditionalOnProperty` 的 import（如未使用） | `DaoAutoConfiguration.java` |

### `AutoConfiguration.imports` 变更

```
# Before
com.richie.component.dao.config.DaoAutoConfiguration
com.richie.component.dao.tenant.MybatisPlusTenantAutoConfiguration  ← 移除
com.richie.component.dao.snowflake.IdBuilderAutoConfiguration

# After
com.richie.component.dao.config.DaoAutoConfiguration
com.richie.component.dao.snowflake.IdBuilderAutoConfiguration
```

### 新的 `AutoConfiguration.imports`（租户模块）

```properties
# atlas-richie-component-dao-tenant
com.richie.component.dao.tenant.config.TenantAutoConfiguration
```

### 验收标准

- [ ] DAO 模块中不存在 `com.richie.component.dao.tenant` 包的任何文件
- [ ] DAO 模块的 `AutoConfiguration.imports` 不含租户配置类
- [ ] DAO 模块 pom.xml 依赖树中不含 Redisson（非 test）
- [ ] `mvn compile -pl atlas-richie-component-dao` 通过
- [ ] `mvn compile -pl atlas-richie-component-dao-tenant` 通过

---

## Phase 6: 集成验证

### 6.1 编译验证

```bash
# 全量编译
mvn clean compile -DskipTests

# 分模块验证
mvn compile -pl atlas-richie-component-dao -am
mvn compile -pl atlas-richie-component-dao-tenant -am
mvn compile -pl atlas-richie-component
```

### 6.2 下游模块验证

确认以下模块在移除 DAO 租户代码后仍能编译：

| 模块 | 依赖 | 验证方式 |
|------|------|---------|
| `atlas-richie-component-logging` | `dao` + `IdBuilder` | `mvn compile -pl logging -am` |
| `atlas-richie-component-mfa-management` | `dao` | `mvn compile -pl mfa-mgmt -am` |
| `atlas-richie-component-statemachine` | `dao` (provided) | `mvn compile -pl statemachine -am` |
| `atlas-richie-component-storage-local` | `dao` | `mvn compile -pl storage-local -am` |
| `sample-logging` | `dao` | `mvn compile -pl sample-logging -am` |
| `sample-threadpool` | `dao` | `mvn compile -pl sample-threadpool -am` |
| `sample-mfa` | `dao` | `mvn compile -pl sample-mfa -am` |

### 6.3 功能验证清单

| 验证项 | 预期行为 |
|--------|---------|
| **不引入 dao-tenant** | 应用正常启动，无租户功能，无 Redisson 依赖 |
| **引入 dao-tenant + enabled=false** | 应用正常启动，租户配置类不生效 |
| **enabled=true + strategy=DISCRIMINATOR** | `TenantLineInnerInterceptor` 注册，SQL 自动注入租户条件 |
| **enabled=true + strategy=DATABASE** | `dynamic-datasource` 提供者注册，数据源按租户路由 |
| **strictMode=true + 无租户请求** | `TenantNotFoundException` 抛出 |
| **strictMode=false + 无租户请求** | `NullValue()` 返回，不返回任何数据 |
| **旧前缀 `spring.datasource.dynamic.tenant`** | WARN 日志 + 自动映射 |

### 验收标准

- [ ] 全量 `mvn clean compile` 通过
- [ ] 所有下游模块编译通过
- [ ] 非租户应用启动后 classpath 中无 Redisson
- [ ] 租户应用引入 `dao-tenant` 后可正常启用租户功能

---

## Phase 7: 配套设施（后续）

> 以下为建议在重构完成后跟进的事项

| # | 事项 | 说明 |
|---|------|------|
| 7.1 | **单元测试** | 为 `DiscriminatorColumnStrategy` 和 `DatabasePerTenantStrategy` 编写单元测试 |
| 7.2 | **集成测试** | 多租户数据 fixtures，验证 CRUD 操作正确隔离 |
| 7.3 | **MDC 日志** | 将 `tenantCode` 注入 `MDC`，方便日志追踪 |
| 7.4 | **审计日志** | 策略切换时记录审计事件 |
| 7.5 | **升级指南** | 编写 `UPGRADE.md`，指导用户从旧配置迁移到新配置 |
| 7.6 | **Shadow Mode** | 可选的"新旧并行运行"模式，用于验证一致性 |

---

## 附录: 时间估算

| Phase | 内容 | 预估时间 | 文件变更数 |
|-------|------|---------|-----------|
| Phase 0 | 模块骨架 | 15 min | ~5 新建 |
| Phase 1 | 代码迁移 | 30 min | 21 移动 + 5 编辑 |
| Phase 2 | 核心重构 | 60 min | ~15 编辑 + 4 新建 |
| Phase 3 | 严重问题修复 | 45 min | ~8 编辑 + 1 删除 |
| Phase 4 | 中等问题修复 | 20 min | ~5 编辑 |
| Phase 5 | DAO 清理 | 15 min | ~5 编辑/删除 |
| Phase 6 | 集成验证 | 30 min | 编译验证为主 |
| **合计** | | **~3.5 小时** | ~50 文件 |

---

> **下一步**: 确认计划后，按 Phase 0→6 顺序执行。每完成一个 Phase，提交一次代码。
