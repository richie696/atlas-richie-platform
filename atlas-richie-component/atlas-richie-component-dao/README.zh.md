# Atlas Richie DAO组件 (atlas-richie-component-dao)

基于 MyBatis Plus 的数据库访问层组件，提供自动配置、分页、多租户、分布式ID生成、SQL监控等开箱即用的数据库操作能力。

## 📖 目录

- [✨ 功能特性](#-功能特性)
- [🚀 快速开始](#-快速开始)
  - [1. 添加依赖](#1-添加依赖)
  - [2. 配置数据源](#2-配置数据源)
  - [3. 创建实体类](#3-创建实体类)
  - [4. 创建 Mapper 接口](#4-创建-mapper-接口)
  - [5. 使用示例](#5-使用示例)
- [🔧 核心功能](#-核心功能)
  - [1. 智能分页](#1-智能分页)
  - [2. 分布式ID生成](#2-分布式id生成)
  - [3. 自动字段填充](#3-自动字段填充)
  - [4. SQL监控](#4-sql监控)
  - [5. 乐观锁支持](#5-乐观锁支持)
  - [6. 安全防护](#6-安全防护)
  - [7. 批量更新限制](#7-批量更新限制)
- [🏢 多租户支持](#-多租户支持)
  - [概述](#概述)
  - [功能特性](#功能特性)
  - [配置](#配置)
  - [数据源表结构](#数据源表结构)
  - [使用方式](#使用方式)
  - [租户数据隔离](#租户数据隔离)
- [⚙️ 配置说明](#-配置说明)
  - [基础配置](#基础配置)
  - [多租户配置](#多租户配置)
- [🎯 最佳实践](#-最佳实践)
  - [1. 实体类设计](#1-实体类设计)
  - [2. 分页查询](#2-分页查询)
  - [3. 条件查询](#3-条件查询)
  - [4. 批量操作](#4-批量操作)
  - [5. 逻辑删除](#5-逻辑删除)
  - [6. 多租户使用](#6-多租户使用)
- [❓ 常见问题](#-常见问题)
  - [Q1: 如何自定义ID生成策略？](#q1-如何自定义id生成策略)
  - [Q2: 如何禁用自动字段填充？](#q2-如何禁用自动字段填充)
  - [Q3: 分页查询时排序字段如何转换？](#q3-分页查询时排序字段如何转换)
  - [Q4: 多租户如何切换数据源？](#q4-多租户如何切换数据源)
  - [Q5: 如何配置慢SQL检测？](#q5-如何配置慢sql检测)
  - [Q6: 批量更新限制如何调整？](#q6-批量更新限制如何调整)
  - [Q7: 如何忽略某些表的租户隔离？](#q7-如何忽略某些表的租户隔离)
- [📝 总结](#-总结)

---

## ✨ 功能特性

### 核心能力

- ✅ **MyBatis Plus 自动配置**：开箱即用的 MyBatis Plus 配置，无需手动配置
- ✅ **智能分页**：自定义分页拦截器，支持自动转换排序字段（驼峰转下划线）
- ✅ **分布式ID生成**：基于雪花算法的分布式ID生成器，自动集成到 MyBatis Plus
- ✅ **多租户支持**：动态数据源切换，支持多租户数据隔离
- ✅ **SQL监控**：集成 p6spy，提供SQL执行监控和慢SQL检测
- ✅ **自动字段填充**：自动填充创建时间、更新时间、创建人、更新人等字段
- ✅ **乐观锁支持**：集成 MyBatis Plus 乐观锁插件
- ✅ **安全防护**：阻止全表更新和删除操作，防止误操作
- ✅ **批量更新限制**：可配置批量更新限制，防止批量更新过多数据

### 扩展能力

- ✅ **国际化支持**：I18n 类型处理器，支持多语言数据存储
- ✅ **动态数据源**：支持基于注解的动态数据源切换
- ✅ **SQL优化**：自动优化SQL日志格式，便于调试和监控

---

## 🚀 快速开始

### 1) 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-dao</artifactId>
</dependency>
```

### 2) 配置数据源

```yaml
# application.yml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/db_name?useUnicode=true&characterEncoding=utf8&useSSL=false
    username: root
    password: password

# DAO组件配置
platform:
  component:
    dao:
      db-type: MYSQL                    # 数据库类型：MYSQL、POSTGRE_SQL
      enable-logging: false             # 是否启用SQL日志格式化
      enable-tenant: false              # 是否启用多租户
      enable-batch-update-limit: true   # 是否启用批量更新限制
      batch-update-limit: 1000          # 批量更新限制阈值
      enable-default-field-handler: true # 是否启用默认字段处理器
```

### 3) 创建实体类

```java
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)  // 使用雪花算法生成ID
    private Long id;
    
    private String username;
    private String email;
    
    // 以下字段会自动填充（如果启用默认字段处理器）
    private String createId;      // 创建人
    private String updateId;      // 更新人
    private ZonedDateTime createTime;  // 创建时间
    private ZonedDateTime updateTime;  // 更新时间
    private Boolean deleted;      // 逻辑删除标记
}
```

### 4) 创建 `Mapper` 接口

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // MyBatis Plus 提供的基础CRUD方法
    // 也可以自定义SQL
}
```

### 5) 使用示例

```java
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    /**
     * 保存用户（自动填充创建时间、更新时间等）
     */
    public void saveUser(User user) {
        save(user);  // ID会自动生成，创建时间等字段会自动填充
    }
    
    /**
     * 分页查询
     */
    public IPage<User> pageUsers(int pageNum, int pageSize) {
        Page<User> page = new Page<>(pageNum, pageSize);
        return page(page);
    }
    
    /**
     * 条件查询
     */
    public List<User> findUsers(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return list(wrapper);
    }
}
```

---

## 🔧 核心功能

### 1) 智能分页

组件提供了自定义分页拦截器，支持自动转换排序字段（驼峰转下划线）。

#### 使用 `BasePage`（推荐）

```java
import com.richie.component.dao.page.BasePage;

public class UserQuery extends BasePage {
    private String username;
    // pageNum、pageSize、orderName、orderType 已继承自 BasePage
}

// 在Controller中使用
@GetMapping("/users")
public IPage<User> getUsers(UserQuery query) {
    return userService.page(query.getPage());
}
```

#### 使用 `MyBatis` `Plus` `Page`

```java
// 直接使用 MyBatis Plus 的 Page
Page<User> page = new Page<>(1, 10);
page.addOrder(OrderItem.asc("username"));  // 自动转换为 user_name
IPage<User> result = userMapper.selectPage(page, null);
```

**特性**：
- 自动将驼峰字段名转换为下划线字段名（如 `userName` → `user_name`）
- 支持 ResultMap 映射，优先使用映射关系
- 支持多字段排序

### 2) 分布式ID生成

组件集成了基于雪花算法的分布式ID生成器，自动为实体类生成唯一ID。

```java
@TableId(type = IdType.ASSIGN_ID)  // 使用组件提供的ID生成器
private Long id;
```

**特性**：
- 基于雪花算法，保证分布式环境下ID唯一性
- 自动生成，无需手动设置
- 支持最大 1024 个工作节点
- 时间戳从 2020-05-03 开始计算

**ID结构**：
```
最高 1 位：始终为 0
接下来的 10 位：workerId（工作节点ID）
下一个 41 位：时间戳
最低 12 位：序列号
```

### 3) 自动字段填充

组件提供了默认字段处理器，自动填充以下字段：

- `createId`：创建人（从登录上下文获取）
- `updateId`：更新人（从登录上下文获取）
- `createTime`：创建时间（当前时间）
- `updateTime`：更新时间（当前时间）
- `deleted`：逻辑删除标记（默认为 false）

**使用方式**：

```java
// 实体类字段会自动填充
User user = new User();
user.setUsername("test");
save(user);  // createTime、createId 等字段会自动填充
```

**自定义字段填充**：

如果需要自定义字段填充逻辑，可以实现 `MetaObjectHandler` 接口：

```java
@Component
public class CustomFieldHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        // 自定义插入时的字段填充
    }
    
    @Override
    public void updateFill(MetaObject metaObject) {
        // 自定义更新时的字段填充
    }
}
```

### 4) SQL监控

组件集成了 p6spy，提供SQL执行监控和慢SQL检测。

**功能特性**：
- SQL执行时间统计
- 慢SQL检测（默认2秒）
- 优化的SQL日志格式
- 连接信息记录

**配置示例**：

```yaml
# 通过配置启用SQL监控
platform:
  component:
    dao:
      enable-logging: true  # 启用SQL日志格式化
```

**日志格式示例**：
```
2025-01-09 10:30:00+0800 | SQL耗时： 15 ms | 连接信息： statement-1 | 执行语句：
SELECT * FROM user WHERE id = ?
```

### 5) 乐观锁支持

组件集成了 MyBatis Plus 乐观锁插件，支持乐观锁更新。

```java
// 实体类中添加版本字段
@Version
private Integer version;

// 更新时会自动检查版本号
User user = getById(1L);
user.setUsername("newName");
updateById(user);  // 版本号会自动递增
```

### 6) 安全防护

组件集成了安全防护插件，防止误操作：

- **阻止全表更新**：防止 `UPDATE table SET ...` 不带 WHERE 条件
- **阻止全表删除**：防止 `DELETE FROM table` 不带 WHERE 条件

### 7) 批量更新限制

组件支持配置批量更新限制，防止批量更新过多数据导致性能问题。

```yaml
platform:
  component:
    dao:
      enable-batch-update-limit: true   # 启用批量更新限制
      batch-update-limit: 1000          # 批量更新限制阈值
```

当批量更新超过限制时，会抛出异常，防止误操作。

---

## 🏢 多租户支持

### 概述

组件提供了完整的多租户解决方案，支持动态数据源切换和租户数据隔离。

### 功能特性

- ✅ **动态数据源切换**：根据租户代码自动切换数据源
- ✅ **租户数据隔离**：自动在SQL中添加租户过滤条件
- ✅ **忽略表配置**：支持配置忽略租户隔离的表
- ✅ **数据源缓存**：缓存租户数据源配置，提升性能

### 配置

```yaml
# 启用多租户
platform:
  component:
    dao:
      enable-tenant: true

# 多租户配置
spring:
  datasource:
    dynamic:
      tenant:
        # 忽略租户隔离的表名
        ignore-tenant-tables:
          - sys_config
          - sys_dict
        # 租户数据源表名
        tenant-table-name: tenant_datasource
        # 数据库连接URL模板
        db-url-template: jdbc:mysql://%s?useUnicode=true&characterEncoding=utf8&useSSL=false
        # 租户字段名
        tenant-id-column: tenant_code
        # 新增租户的topic（用于消息通知）
        add-tenant-topic: add_tenant_topic
        # 如果找不到master数据源，是否使用随机数据源
        use-random-master: true
```

### 数据源表结构

需要在数据库中创建租户数据源配置表：

```sql
CREATE TABLE tenant_datasource (
    id BIGINT PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL COMMENT '服务名',
    ds_name VARCHAR(64) NOT NULL COMMENT '数据源名（master或其他）',
    tenant_codes VARCHAR(512) NOT NULL COMMENT '租户编码，逗号分隔',
    db_username VARCHAR(64) NOT NULL COMMENT '数据库用户名',
    db_password VARCHAR(256) NOT NULL COMMENT '数据库密码（加密）',
    db_param VARCHAR(256) NOT NULL COMMENT '数据库参数（如：127.0.0.1:3306/db_name）',
    create_time DATETIME,
    update_time DATETIME
);
```

### 使用方式

#### 1. 自动数据源切换

组件会自动从请求头或JWT Token中获取租户代码，并自动切换数据源：

```java
// 请求头方式
// Header: X-Tenant-Code: 1001

// JWT Token方式
// Token中包含 tenantCode 字段

// 组件会自动切换数据源，无需手动处理
```

#### 2. 注解方式切换数据源

```java
import annotation.tenant.com.richie.component.dao.CommonDataSource;

@Service
public class CommonService {

    @CommonDataSource  // 使用公共数据源（不进行租户隔离）
    public List<Config> getConfigs() {
        return configMapper.selectList(null);
    }
}
```

#### 3. 手动切换数据源

```java
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;

// 切换到指定数据源
DynamicDataSourceContextHolder.push("ds1");

// 恢复默认数据源
DynamicDataSourceContextHolder.clear();
```

### 租户数据隔离

启用多租户后，组件会自动在SQL中添加租户过滤条件：

```sql
-- 原始SQL
SELECT * FROM user WHERE username = 'test'

-- 自动添加租户条件后
SELECT * FROM user WHERE username = 'test' AND tenant_code = '1001'
```

**忽略表配置**：

对于不需要租户隔离的表（如系统配置表），可以在配置中忽略：

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

## ⚙️ 配置说明

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.component.dao.db-type` | String | `MYSQL` | 数据库类型：`MYSQL`、`POSTGRE_SQL` |
| `platform.component.dao.enable-logging` | boolean | `false` | 是否启用SQL日志格式化 |
| `platform.component.dao.enable-tenant` | boolean | `false` | 是否启用多租户 |
| `platform.component.dao.enable-batch-update-limit` | boolean | `true` | 是否启用批量更新限制 |
| `platform.component.dao.batch-update-limit` | int | `1000` | 批量更新限制阈值 |
| `platform.component.dao.enable-default-field-handler` | boolean | `true` | 是否启用默认字段处理器 |

### 多租户配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `spring.datasource.dynamic.tenant.ignore-tenant-tables` | List<String> | `[]` | 忽略租户隔离的表名列表 |
| `spring.datasource.dynamic.tenant.tenant-table-name` | String | `tenant_datasource` | 租户数据源表名 |
| `spring.datasource.dynamic.tenant.db-url-template` | String | `jdbc:mysql://%s?...` | 数据库连接URL模板 |
| `spring.datasource.dynamic.tenant.tenant-id-column` | String | `tenant_code` | 租户字段名 |
| `spring.datasource.dynamic.tenant.add-tenant-topic` | String | `add_tenant_topic` | 新增租户的topic |
| `spring.datasource.dynamic.tenant.use-random-master` | boolean | `true` | 找不到master时是否使用随机数据源 |

---

## 🎯 最佳实践

### 1) 实体类设计

```java
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)  // 使用分布式ID
    private Long id;
    
    private String username;
    private String email;
    
    // 自动填充字段
    private String createId;
    private String updateId;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;
    private Boolean deleted;
    
    @Version  // 乐观锁字段
    private Integer version;
}
```

### 2) 分页查询

```java
// 推荐使用 BasePage
public class UserQuery extends BasePage {
    private String username;
    private String email;
}

// Controller
@GetMapping("/users")
public IPage<User> getUsers(UserQuery query) {
    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
    wrapper.like(StringUtils.isNotBlank(query.getUsername()), 
                User::getUsername, query.getUsername());
    return userService.page(query.getPage(), wrapper);
}
```

### 3) 条件查询

```java
// 使用 LambdaQueryWrapper（类型安全）
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, "test")
       .like(User::getEmail, "@")
       .orderByDesc(User::getCreateTime);
List<User> users = userService.list(wrapper);
```

### 4) 批量操作

```java
// 批量插入
List<User> users = Arrays.asList(user1, user2, user3);
userService.saveBatch(users);

// 批量更新（注意批量更新限制）
userService.updateBatchById(users);
```

### 5) 逻辑删除

```java
// 实体类字段
private Boolean deleted;

// 删除（逻辑删除）
userService.removeById(1L);  // 实际是 UPDATE user SET deleted = 1 WHERE id = 1

// 查询时自动过滤已删除数据
List<User> users = userService.list();  // 自动添加 WHERE deleted = 0
```

### 6) 多租户使用

```java
// 1. 确保请求头或JWT中包含租户代码
// 2. 组件会自动切换数据源和添加租户过滤条件

// 公共表查询（不需要租户隔离）
@CommonDataSource
public List<Config> getConfigs() {
    return configMapper.selectList(null);
}
```

---

## ❓ 常见问题

### `Q1` — 如何自定义ID生成策略？

**A:** 组件默认使用雪花算法生成ID。如果需要自定义，可以实现 `IdentifierGenerator` 接口：

```java
@Component
public class CustomIdGenerator implements IdentifierGenerator {
    @Override
    public Number nextId(Object entity) {
        // 自定义ID生成逻辑
        return System.currentTimeMillis();
    }
}
```

### `Q2` — 如何禁用自动字段填充？

**A:** 在配置中禁用：

```yaml
platform:
  component:
    dao:
      enable-default-field-handler: false
```

### `Q3` — 分页查询时排序字段如何转换？

**A:** 组件会自动将驼峰字段名转换为下划线字段名。如果使用 ResultMap，会优先使用映射关系。

### `Q4` — 多租户如何切换数据源？

**A:** 组件会自动从请求头（`X-Tenant-Code`）或JWT Token中获取租户代码，并自动切换数据源。也可以使用 `@CommonDataSource` 注解手动指定数据源。

### `Q5` — 如何配置慢SQL检测？

**A:** 慢SQL检测由 p6spy 提供，默认阈值为2秒。可以在 `spy.properties` 中配置：

```properties
outagedetection=true
outagedetectioninterval=2  # 慢SQL阈值（秒）
```

### `Q6` — 批量更新限制如何调整？

**A:** 在配置中调整：

```yaml
platform:
  component:
    dao:
      enable-batch-update-limit: true
      batch-update-limit: 2000  # 调整为2000
```

### `Q7` — 如何忽略某些表的租户隔离？

**A:** 在配置中添加忽略表：

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

## 📝 总结

Richie DAO Component 提供了完整的数据库访问层解决方案，涵盖了从基础CRUD到高级特性的各个方面。通过合理使用这些功能，可以构建高性能、高可用的数据访问层。

**关键要点**：

1. **选择合适的ID生成策略**：默认使用雪花算法，适合分布式环境
2. **合理使用分页**：使用 BasePage 简化分页查询
3. **注意批量操作限制**：避免批量更新过多数据
4. **多租户配置**：正确配置租户数据源表和数据源切换
5. **监控SQL性能**：启用SQL监控，及时发现慢SQL

通过遵循这些最佳实践，可以充分发挥组件的性能优势，为业务系统提供稳定可靠的数据访问支持。

