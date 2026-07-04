# Atlas Richie DAO Component (atlas-richie-component-dao)

> Database access layer component based on MyBatis Plus, providing out-of-the-box database operation capabilities including auto-configuration, pagination, multi-tenant, distributed ID generation, and SQL monitoring.

---

## 📖 Contents

- [✨ Features](#-features)
- [🚀 Quick Start](#-quick-start)
  - [1. Add the Dependency](#1-add-the-dependency)
  - [2. Configure the Data Source](#2-configure-the-data-source)
  - [3. Create the Entity Class](#3-create-the-entity-class)
  - [4. Create the Mapper Interface](#4-create-the-mapper-interface)
  - [5. Usage Example](#5-usage-example)
- [🔧 Core Features](#-core-features)
  - [1. Smart Pagination](#1-smart-pagination)
  - [2. Distributed ID Generation](#2-distributed-id-generation)
  - [3. Auto Field Filling](#3-auto-field-filling)
  - [4. SQL Monitoring](#4-sql-monitoring)
  - [5. Optimistic Lock Support](#5-optimistic-lock-support)
  - [6. Safety Protection](#6-safety-protection)
  - [7. Batch Update Limit](#7-batch-update-limit)
- [🏢 Multi-Tenant Support](#-multi-tenant-support)
  - [Overview](#overview)
  - [Features](#features-1)
  - [Configuration](#configuration)
  - [Datasource Table Structure](#datasource-table-structure)
  - [Usage Methods](#usage-methods)
  - [Tenant Data Isolation](#tenant-data-isolation)
- [⚙️ Configuration Reference](#-configuration-reference)
  - [Basic Configuration](#basic-configuration)
  - [Multi-Tenant Configuration](#multi-tenant-configuration)
- [🎯 Best Practices](#-best-practices)
  - [1. Entity Class Design](#1-entity-class-design)
  - [2. Pagination Query](#2-pagination-query)
  - [3. Conditional Query](#3-conditional-query)
  - [4. Batch Operations](#4-batch-operations)
  - [5. Logical Delete](#5-logical-delete)
  - [6. Multi-Tenant Usage](#6-multi-tenant-usage)
- [❓ FAQ](#-faq)
  - [Q1: How to customize the ID generation strategy?](#q1-how-to-customize-the-id-generation-strategy)
  - [Q2: How to disable auto field filling?](#q2-how-to-disable-auto-field-filling)
  - [Q3: How is the sort field converted in pagination queries?](#q3-how-is-the-sort-field-converted-in-pagination-queries)
  - [Q4: How does multi-tenant switch data sources?](#q4-how-does-multi-tenant-switch-data-sources)
  - [Q5: How to configure slow SQL detection?](#q5-how-to-configure-slow-sql-detection)
  - [Q6: How to adjust the batch update limit?](#q6-how-to-adjust-the-batch-update-limit)
  - [Q7: How to ignore tenant isolation for some tables?](#q7-how-to-ignore-tenant-isolation-for-some-tables)
- [📝 Summary](#-summary)

---

## ✨ Features

### `Core` `Capabilities`

- ✅ **MyBatis Plus Auto-Configuration**: zero-config integration with MyBatis Plus, including pagination plugin and field auto-fill
- ✅ **Smart Pagination**: `BasePage` encapsulates common pagination parameters, supports camelCase to underscore automatic conversion
- ✅ **Distributed ID Generation**: built-in Snowflake algorithm, supports 1024 workers, 41-bit timestamp (epoch 2020-05-03)
- ✅ **Multi-Tenant Support**: complete multi-tenant solution with dynamic datasource switching and tenant data isolation
- ✅ **SQL Monitoring**: integrated with p6spy, configurable slow SQL threshold and log format
- ✅ **Auto Field Filling**: default filling of `createId`, `updateId`, `createTime`, `updateTime`, `deleted`
- ✅ **Optimistic Lock Support**: based on `@Version` annotation
- ✅ **Safety Protection**: prevent UPDATE without WHERE clause, prevent DELETE without WHERE clause
- ✅ **Batch Update Limit**: prevent accidentally updating massive data, default limit 1000 rows

### `Extended` `Capabilities`

- ✅ **Internationalization**: integrates with `richie-component-i18n`, supports multi-language field values
- ✅ **Dynamic DataSource**: integrates with `spring-datasource-dynamic`, supports runtime datasource switching
- ✅ **SQL Optimization**: integrates with SQL parsing plugins, supports slow SQL interception and optimization suggestions

---

## 🚀 Quick Start

### 1) `Add` the `Dependency`

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-dao</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2) `Configure` the `Data` `Source`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

platform:
  component:
    dao:
      # Database type: MYSQL, POSTGRE_SQL
      db-type: MYSQL
      # Whether to enable SQL log formatting
      enable-logging: true
      # Whether to enable multi-tenant
      enable-tenant: false
      # Whether to enable batch update limit
      enable-batch-update-limit: true
      # Batch update limit threshold
      batch-update-limit: 1000
      # Whether to enable default field handler
      enable-default-field-handler: true
```

### 3) `Create` the `Entity` `Class`

```java
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String email;

    private Integer age;

    /**
     * Creator ID (auto-filled)
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createId;

    /**
     * Updater ID (auto-filled)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateId;

    /**
     * Creation time (auto-filled)
     */
    @TableField(fill = FieldFill.INSERT)
    private ZonedDateTime createTime;

    /**
     * Update time (auto-filled)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private ZonedDateTime updateTime;

    /**
     * Logical delete flag (0: not deleted, 1: deleted)
     */
    @TableLogic
    private Integer deleted;
}
```

### 4) `Create` the `Mapper` `Interface`

```java
public interface UserMapper extends BaseMapper<User> {
    // MyBatis Plus provides basic CRUD methods by default
    // Custom SQL can be added here
}
```

### 5) `Usage` `Example`

```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    /**
     * Save user
     */
    public User saveUser(User user) {
        save(user);
        return user;
    }

    /**
     * Paginated query
     */
    public IPage<User> pageUsers(int current, int size) {
        return page(new Page<>(current, size));
    }

    /**
     * Conditional query
     */
    public List<User> findUsers(String username, Integer minAge) {
        return lambdaQuery()
                .eq(User::getUsername, username)
                .ge(User::getAge, minAge)
                .list();
    }
}
```

---

## 🔧 Core Features

### 1) `Smart` `Pagination`

#### `Using` `BasePage` (`Recommended`)

```java
@Data
public class UserQuery extends BasePage {
    private String username;
    private Integer minAge;
    private Integer maxAge;
}

// Usage
UserQuery query = new UserQuery();
query.setCurrent(1);
query.setSize(10);
query.setUsername("test");

IPage<User> page = userService.lambdaQuery()
        .eq(User::getUsername, query.getUsername())
        .ge(User::getAge, query.getMinAge())
        .page(new Page<>(query.getCurrent(), query.getSize()));
```

#### `Using` `MyBatis` `Plus` `Page`

```java
Page<User> page = new Page<>(1, 10);
userMapper.selectPage(page, null);
```

**Features**:

- Auto camelCase to underscore field name conversion
- Default sort by `id DESC`
- Supports custom sort fields
- Auto total count statistics

### 2) `Distributed` `ID` `Generation`

```java
// Use ASSIGN_ID in entity class
@TableId(type = IdType.ASSIGN_ID)
private Long id;
```

**Features**:

- Snowflake algorithm based
- Supports up to 1024 workers (10-bit workerId)
- 41-bit timestamp (millisecond precision, epoch 2020-05-03)
- 12-bit serial number (4096 IDs per millisecond per worker)

**ID Structure**:

```
0 | 0000000000 | 00000000000000000000000000000000000000000 | 000000000000
^   ^           ^                                            ^
|   |           |                                            |
|   |           |                                            +-- 12-bit serial number
|   |           +----------------------------------------------- 41-bit timestamp
|   +---------------------------------------------------------- 10-bit workerId
+-------------------------------------------------------------- 1-bit sign bit
```

### 3) `Auto` `Field` `Filling`

Default auto-filled fields:

- `createId`: creator ID
- `updateId`: updater ID
- `createTime`: creation time
- `updateTime`: update time
- `deleted`: logical delete flag

**Usage**:

```java
@TableField(fill = FieldFill.INSERT)
private Long createId;

@TableField(fill = FieldFill.INSERT_UPDATE)
private ZonedDateTime updateTime;
```

**Custom Field Filling**:

```java
@Component
public class CustomFieldHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createId", Long.class, SecurityUtils.getCurrentUserId());
        this.strictInsertFill(metaObject, "createTime", ZonedDateTime.class, ZonedDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateId", Long.class, SecurityUtils.getCurrentUserId());
        this.strictUpdateFill(metaObject, "updateTime", ZonedDateTime.class, ZonedDateTime.now());
    }
}
```

### 4) `SQL` `Monitoring`

**Features**:

- Integrated with p6spy
- Configurable slow SQL threshold (default 2s)
- Configurable log format
- Supports printing executed SQL and parameters

**Configuration Example**:

```yaml
platform:
  component:
    dao:
      enable-logging: true
```

**Log Format Example**:

```
2025-01-09 10:30:00+0800 | SQL took: 15 ms | connection: statement-1 | statement: SELECT * FROM user WHERE id = ?
```

### 5) `Optimistic` `Lock` `Support`

```java
@Data
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Version
    private Integer version;
}

// Update user
User user = userMapper.selectById(1L);
user.setAge(26);
userMapper.updateById(user); // Will append WHERE version = ?
```

### 6) `Safety` `Protection`

- Prevent UPDATE without WHERE clause (blocks full-table updates)
- Prevent DELETE without WHERE clause (blocks full-table deletes)
- Prevent batch operations exceeding the limit (default 1000 rows)

### 7) `Batch` `Update` `Limit`

```yaml
platform:
  component:
    dao:
      enable-batch-update-limit: true
      batch-update-limit: 1000
```

---

## 🏢 Multi-Tenant Support

### `Overview`

Provides a complete multi-tenant solution supporting dynamic datasource switching and tenant data isolation. Tenants are identified by the `tenantCode`, and the system automatically routes requests to the corresponding tenant's datasource.

### `Features`

- Dynamic datasource switching: automatically switch datasource based on `X-Tenant-Code` request header or JWT Token
- Tenant data isolation: SQL queries auto-append `tenant_code` condition to prevent data leakage
- Ignore table config: certain tables (e.g., dictionary tables, configuration tables) can be excluded from tenant isolation
- Datasource cache: built-in datasource connection pool, avoids repeated datasource creation

### `Configuration`

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/master?...
          username: root
          password: root

platform:
  component:
    dao:
      enable-tenant: true

spring.datasource.dynamic.tenant:
  # Tables excluded from tenant isolation
  ignore-tenant-tables:
    - sys_config
    - sys_dict
  # Tenant datasource table name
  tenant-table-name: tenant_datasource
  # Database URL template (%s will be replaced with tenant's db_host)
  db-url-template: jdbc:mysql://%s:3306/%s?useUnicode=true&characterEncoding=utf8
  # Tenant field name
  tenant-id-column: tenant_code
  # Topic for adding new tenants
  add-tenant-topic: add_tenant_topic
  # Use random datasource when master cannot be found
  use-random-master: true
```

### `Datasource` `Table` `Structure`

```sql
CREATE TABLE tenant_datasource (
    id BIGINT PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL COMMENT 'service name',
    ds_name VARCHAR(100) NOT NULL COMMENT 'datasource name',
    tenant_codes TEXT NOT NULL COMMENT 'tenant codes (JSON array)',
    db_username VARCHAR(100) NOT NULL COMMENT 'database username',
    db_password VARCHAR(255) NOT NULL COMMENT 'database password',
    db_param TEXT COMMENT 'additional connection parameters (JSON)',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### `Usage` `Methods`

#### 1. `Automatic` `Datasource` `Switching`

The system reads the tenant code from the request header or JWT Token and automatically switches the datasource:

```java
// Frontend sends request header
fetch('/api/users', {
    headers: {
        'X-Tenant-Code': 'tenant-001'
    }
});
```

#### 2. `Annotation`-based `Datasource` `Switching`

Use `@CommonDataSource` annotation to manually specify the datasource:

```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    @CommonDataSource("master")
    public List<User> findAllUsers() {
        // Use master datasource, no tenant filter applied
        return list();
    }
}
```

#### 3. `Manual` `Datasource` `Switching`

```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    public List<User> findUsersFromTenant(String tenantCode) {
        try {
            DynamicDataSourceContextHolder.push(tenantCode);
            return list();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
```

### `Tenant` `Data` `Isolation`

When multi-tenant is enabled, the component automatically appends the `tenant_code` condition to all SQL queries:

```sql
-- Original query
SELECT * FROM user WHERE username = 'test'

-- Actual executed query (tenant_code auto-added)
SELECT * FROM user WHERE username = 'test' AND tenant_code = '1001'
```

**Ignored Tables Config**:

```yaml
spring:
  datasource:
    dynamic:
      tenant:
        ignore-tenant-tables:
          - sys_config
          - sys_dict
          - region
```

---

## ⚙️ Configuration Reference

### `Basic` `Configuration`

| Config Item | Type | Default | Description |
|-------------|------|---------|-------------|
| `platform.component.dao.db-type` | String | `MYSQL` | Database type: `MYSQL`, `POSTGRE_SQL` |
| `platform.component.dao.enable-logging` | boolean | `false` | Whether to enable SQL log formatting |
| `platform.component.dao.enable-tenant` | boolean | `false` | Whether to enable multi-tenant |
| `platform.component.dao.enable-batch-update-limit` | boolean | `true` | Whether to enable batch update limit |
| `platform.component.dao.batch-update-limit` | int | `1000` | Batch update limit threshold |
| `platform.component.dao.enable-default-field-handler` | boolean | `true` | Whether to enable default field handler |

### `Multi`-`Tenant` `Configuration`

| Config Item | Type | Default | Description |
|-------------|------|---------|-------------|
| `spring.datasource.dynamic.tenant.ignore-tenant-tables` | List<String> | `[]` | Table names excluded from tenant isolation |
| `spring.datasource.dynamic.tenant.tenant-table-name` | String | `tenant_datasource` | Tenant datasource table name |
| `spring.datasource.dynamic.tenant.db-url-template` | String | `jdbc:mysql://%s?...` | Database connection URL template |
| `spring.datasource.dynamic.tenant.tenant-id-column` | String | `tenant_code` | Tenant field name |
| `spring.datasource.dynamic.tenant.add-tenant-topic` | String | `add_tenant_topic` | Topic for adding new tenants |
| `spring.datasource.dynamic.tenant.use-random-master` | boolean | `true` | Whether to use random datasource when master cannot be found |

---

## 🎯 Best Practices

### 1) `Entity` `Class` `Design`

```java
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String email;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private Long createId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateId;

    @TableField(fill = FieldFill.INSERT)
    private ZonedDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private ZonedDateTime updateTime;
}
```

### 2) `Pagination` `Query`

```java
@Data
public class UserQuery extends BasePage {
    private String username;
    private Integer minAge;
    private Integer maxAge;
}

// Controller
@GetMapping("/users")
public IPage<User> listUsers(UserQuery query) {
    return userService.lambdaQuery()
            .eq(StrUtil.isNotBlank(query.getUsername()), User::getUsername, query.getUsername())
            .ge(query.getMinAge() != null, User::getAge, query.getMinAge())
            .le(query.getMaxAge() != null, User::getAge, query.getMaxAge())
            .page(new Page<>(query.getCurrent(), query.getSize()));
}
```

### 3) `Conditional` `Query`

```java
List<User> users = userService.lambdaQuery()
        .eq(User::getStatus, 1)
        .like(User::getUsername, "test")
        .orderByDesc(User::getCreateTime)
        .list();
```

### 4) `Batch` `Operations`

```java
// Batch save
userService.saveBatch(userList, 500);

// Batch update
userService.updateBatchById(userList, 500);
```

### 5) `Logical` `Delete`

```java
// `removeById` actually executes UPDATE deleted = 1 WHERE id = ?
userService.removeById(1L);
```

### 6) `Multi`-`Tenant` `Usage`

```java
@CommonDataSource("master")
public List<User> findAllUsers() {
    // Use master datasource, no tenant filter applied
    return userService.list();
}
```

---

## ❓ FAQ

### `Q1` — `How` to customize the `ID` generation strategy?

**A:** The component uses the Snowflake algorithm by default. To customize, implement the `IdentifierGenerator` interface:

```java
@Component
public class CustomIdGenerator implements IdentifierGenerator {
    @Override
    public Long nextId(Object entity) {
        // Return custom ID
        return IdUtil.getSnowflake(1, 1).nextId();
    }
}
```

### `Q2` — `How` to disable auto field filling?

**A:** Disable it in config:

```yaml
platform:
  component:
    dao:
      enable-default-field-handler: false
```

### `Q3` — `How` is the sort field converted in pagination queries?

**A:** The component automatically converts camelCase field names to underscore field names. If a ResultMap is used, the mapping relationship takes priority.

### `Q4` — `How` does multi-tenant switch data sources?

**A:** The component automatically retrieves the tenant code from the request header (`X-Tenant-Code`) or JWT Token, and auto-switches the datasource. You can also use the `@CommonDataSource` annotation to manually specify the datasource.

### `Q5` — `How` to configure slow `SQL` detection?

**A:** Slow SQL detection is provided by p6spy with a default threshold of 2 seconds. Configure it in `spy.properties`:

```properties
outagedetection=true
outagedetectioninterval=2
```

### `Q6` — `How` to adjust the batch update limit?

**A:** Adjust in config:

```yaml
platform:
  component:
    dao:
      enable-batch-update-limit: true
      batch-update-limit: 2000
```

### `Q7` — `How` to ignore tenant isolation for some tables?

**A:** Add ignored tables in config:

```yaml
spring:
  datasource:
    dynamic:
      tenant:
        ignore-tenant-tables:
          - sys_config
          - sys_dict
```

---

## 📝 Summary

The DAO component provides a complete database access layer solution covering entity definition, CRUD operations, pagination, multi-tenant, ID generation, and SQL monitoring. By combining the auto-configuration and best practices provided by the component, you can quickly build reliable, secure, and performant data access layers.

**Key Points**:

1. **Choose the right ID generation strategy**: Snowflake algorithm by default, suitable for distributed environments
2. **Use pagination wisely**: use `BasePage` to simplify pagination queries
3. **Be mindful of batch operation limits**: avoid updating massive amounts of data at once
4. **Multi-tenant configuration**: correctly configure the tenant datasource table and datasource switching
5. **Monitor SQL performance**: enable SQL monitoring to detect slow queries promptly