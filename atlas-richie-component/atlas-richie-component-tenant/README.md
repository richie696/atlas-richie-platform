# Richie Component DAO Tenant

## 概述

多租户数据访问增强组件（可选），在 `atlas-richie-component-dao` 基础上为 MyBatis-Plus 增加多租户数据隔离能力。支持 **column / table / schema / database / hybrid** 五种隔离模式，通过配置驱动、策略可插拔，对业务代码零侵入。

**技术栈**：JDK 25 · Spring Boot 4.0.6 · MyBatis-Plus 3.5.12 · ScopedValue · HikariCP

## 文档索引

> 主方案见 [多租户方案设计.md](docs/多租户方案设计.md)

| 顺序 | 文档 | 说明 |
|------|------|------|
| 1 | [概念设计](docs/多租户MyBatis-Plus通用插件概念设计.md) | 系统概述、技术架构、数据模型、业务流程 |
| 2 | [上下文模块](docs/上下文模块详细设计.md) | `TenantContextHolder` 接口、ScopedValue / TransmittableThreadLocal 双实现 |
| 3 | [策略模块](docs/策略模块详细设计.md) | `TenancyStrategy` 工厂、Column / Table / Schema / Database / Hybrid 五策略 |
| 4 | [持久层路由与拦截器](docs/持久层路由与拦截器集成模块详细设计.md) | `DynamicTenantDataSource`、拦截器链、事务冻结、连接清理 |
| 5 | [可运维与灰度增强](docs/可运维与灰度增强详细设计.md) | 健康检查、灰度发布、动态配置刷新 |

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-tenant</artifactId>
</dependency>
```

### 最小配置

```yaml
multi-tenancy:
  enabled: true
  mode: column
  tenant-id-column: tenant_id
  datasource:
    shared:
      url: jdbc:postgresql://localhost:5432/shared_db
      username: app
      password: password
```

### 实体继承

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends TenantIdDomain {
    private String username;
    // tenantId, deleted 等字段自动继承
}
```

## 约定

- 所有租户上下文操作通过 `TenantContext` 门面，禁止直接操作 `ScopedValue` / `ThreadLocal`
- SQL 改写由拦截器链自动完成，业务代码无需添加租户过滤条件
- 事务内禁止切换租户，违规立即回滚
- `@Qualifier` 必需时不得省略

## 构建

```bash
mvn clean install -pl atlas-richie-component/atlas-richie-component-tenant -am -DskipTests
```
